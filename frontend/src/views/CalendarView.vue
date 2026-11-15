<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ChevronLeft, ChevronRight, Bell, Wallet, Clock, Send, RefreshCw, X } from 'lucide-vue-next'
import { api, ApiError, UpcomingItem, NotificationRecord, CalendarDayDto } from '../api'
import { state, toast } from '../store'
import DayModal from '../components/DayModal.vue'

const dayModal = reactive({ open: false, date: '', items: [] as any[] })
const upcoming = ref<UpcomingItem[]>([])
const records = ref<NotificationRecord[]>([])

async function loadCalendar() {
  try {
    state.calendar = await api.calendar(state.year, state.month)
    records.value = await api.notifications(20)
  } catch (e) { if (e instanceof ApiError) toast(e.message) }
}
async function loadUpcoming() {
  try { upcoming.value = await api.upcoming() } catch {}
}

onMounted(async () => { await loadCalendar(); await loadUpcoming() })

function prevMonth() {
  if (state.month === 1) { state.month = 12; state.year-- } else state.month--
  state.finance = null
  loadCalendar()
}
function nextMonth() {
  if (state.month === 12) { state.month = 1; state.year++ } else state.month++
  loadCalendar()
}

function eventMap() {
  const m = new Map<number, string[]>()
  state.calendar?.eventDays.forEach(e => m.set(e.day, e.types))
  return m
}
function buildGrid() {
  if (!state.calendar) return []
  const first = new Date(state.year, state.month - 1, 1)
  const startDow = first.getDay()
  const daysInMonth = new Date(state.year, state.month, 0).getDate()
  const cells: { day: number; out: boolean; today: boolean; ev: boolean }[] = []
  for (let i = 0; i < startDow; i++) cells.push({ day: 0, out: true, today: false, ev: false })
  const todayStr = state.calendar.today
  const em = eventMap()
  for (let d = 1; d <= daysInMonth; d++) {
    const ds = `${state.year}-${String(state.month).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    cells.push({ day: d, out: false, today: ds === todayStr, ev: em.has(d) })
  }
  while (cells.length % 7 !== 0) cells.push({ day: 0, out: true, today: false, ev: false })
  return cells
}

async function openDay(d: number) {
  if (!d || d === 0) return
  const ds = `${state.year}-${String(state.month).padStart(2, '0')}-${String(d).padStart(2, '0')}`
  try {
    const r: CalendarDayDto = await api.calendarDay(ds)
    dayModal.date = ds
    dayModal.items = r.items
    dayModal.open = true
  } catch (e) { if (e instanceof ApiError) toast(e.message) }
}

async function resend(id: number) {
  try { await api.resend(id); toast('已补发'); await loadCalendar() } catch (e: any) { if (e instanceof ApiError) toast(e.message) }
}
function statusText(s: string) { return s === 'success' ? '成功' : s === 'degraded' ? '降级' : '失败' }
</script>

<template>
  <section class="page active" id="page-calendar">
    <div class="cal-panel">
      <div class="cal-head">
        <h3>{{ state.year }} 年 {{ state.month }} 月</h3>
        <div class="cal-nav">
          <button @click="prevMonth"><ChevronLeft /></button>
          <button @click="nextMonth"><ChevronRight /></button>
        </div>
      </div>
      <div class="big-cal">
        <div class="wd" v-for="w in ['日','一','二','三','四','五','六']" :key="w">{{ w }}</div>
        <div v-for="(c, i) in buildGrid()" :key="i" class="d" :class="{ out: c.out, today: c.today, 'ev-day': c.ev }" @click="openDay(c.day)">
          <span class="num">{{ c.day === 0 ? '' : c.day }}</span>
        </div>
      </div>
      <div style="display:flex;gap:16px;margin-top:14px;font-size:12px;color:var(--c-text-2)">
        <span><span style="display:inline-block;width:11px;height:11px;border-radius:3px;background:var(--c-danger);margin-right:5px;vertical-align:-1px"></span>有付款/到期事项</span>
        <span><span style="display:inline-block;width:11px;height:11px;border-radius:50%;background:var(--c-primary);margin-right:5px;vertical-align:-1px"></span>今天</span>
      </div>
    </div>

    <div class="section-head"><h2>未来 7 天临期</h2><span class="hint">付款日与租约到期前 7 天提醒</span></div>
    <div class="todo-list">
      <div v-if="!upcoming.length" style="text-align:center;color:var(--c-text-2);padding:18px">未来 7 天无临期事项 🌿</div>
      <div class="todo" v-for="u in upcoming" :key="u.houseId + u.type + u.date">
        <div class="ic" :class="u.type === 'pay' ? 'pay' : 'exp'"><component :is="u.type === 'pay' ? Wallet : Clock" /></div>
        <div class="body"><b>{{ u.houseName }}</b><span>{{ u.type === 'pay' ? '付款日' : '租约到期' }} · {{ u.tenantName }} · {{ u.date }}</span></div>
        <div class="cd" :class="{ urgent: u.urgent }">{{ u.daysLeft === 0 ? '今天' : '剩 ' + u.daysLeft + ' 天' }}</div>
      </div>
    </div>

    <div class="section-head"><h2>通知记录</h2><span class="hint">每日聚合日报推送状态</span></div>
    <div style="display:flex;flex-direction:column;gap:10px">
      <div v-if="!records.length" style="text-align:center;color:var(--c-text-2);padding:18px">暂无推送记录</div>
      <div class="record" v-for="r in records" :key="r.id">
        <div class="ch"><Send style="width:18px;height:18px;color:#fff" /></div>
        <div class="body"><b>{{ r.memberUsername }}</b><span>{{ r.reportDate }} · {{ r.channel === 'wecom' ? '企业微信' : 'Server酱' }} · {{ r.sentAt || '—' }}</span></div>
        <span class="st" :class="r.status === 'success' ? 'ok' : r.status === 'degraded' ? 'deg' : 'fail'">{{ statusText(r.status) }}</span>
        <button v-if="r.canResend" class="resend" @click="resend(r.id)"><RefreshCw style="width:13px;height:13px" /> 补发</button>
      </div>
    </div>

    <DayModal :date="dayModal.date" :items="dayModal.items" :open="dayModal.open" @close="dayModal.open = false" />
  </section>
</template>
