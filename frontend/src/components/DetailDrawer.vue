<script setup lang="ts">
import { ref, computed } from 'vue'
import { ArrowLeft, Phone, MapPin, Wallet, Coins, CalendarDays, Clock, FileText, Zap, User, Repeat, LogOut, Pencil, Trash2 } from 'lucide-vue-next'
import { api, ApiError, HouseDetail, BreachPreview } from '../api'
import { toast, refreshAll, loadHousesOnly, state } from '../store'
import LeaseModal from './LeaseModal.vue'

const props = defineProps<{ house: HouseDetail | null; open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'changed'): void }>()

const modalOpen = ref(false)
const modalMode = ref<'lease' | 'renew' | 'edit'>('lease')
const breach = ref<BreachPreview | null>(null)
const confirming = ref(false)

const addr = computed(() => props.house ? `${props.house.community} ${props.house.building} ${props.house.floorNo}F-${props.house.roomNo}` : '')
const isRented = computed(() => props.house?.status === 'rented')

function openModal(mode: 'lease' | 'renew' | 'edit') {
  modalMode.value = mode
  breach.value = null
  modalOpen.value = true
}
function afterModal() { modalOpen.value = false; emit('changed') }

function maskPhone(p?: string) {
  if (!p) return ''
  if (p.length <= 7) return p
  return p.slice(0, 3) + '-****-' + p.slice(-4)
}

async function startVacate() {
  if (!props.house) return
  confirming.value = true
  try {
    if (!breach.value) {
      await api.vacate(props.house.id, false) // 期望 2006 抛错带预览
    }
  } catch (e: any) {
    if (e instanceof ApiError && e.code === 2006) {
      breach.value = e.data as BreachPreview
    } else if (e instanceof ApiError) {
      toast(e.message)
    }
  } finally { confirming.value = false }
}
async function confirmBreach() {
  if (!props.house) return
  confirming.value = true
  try {
    await api.vacate(props.house.id, true)
    toast('已退租，押金没收计入财产明细')
    breach.value = null
    emit('changed')
  } catch (e: any) { if (e instanceof ApiError) toast(e.message) }
  finally { confirming.value = false }
}
async function doDelete() {
  if (!props.house) return
  if (!confirm('确认删除该房源？删除后不可恢复，有生效合同时禁止删除。')) return
  try {
    await api.houseDelete(props.house.id)
    toast('房源已删除')
    emit('changed')
    emit('close')
  } catch (e: any) { if (e instanceof ApiError) toast(e.message) }
}
</script>

<template>
  <div class="overlay" :class="{ show: open }" @click="emit('close')"></div>
  <aside class="drawer" :class="{ show: open }">
    <div class="drawer-head">
      <button class="back" @click="emit('close')"><ArrowLeft /></button>
      <div class="t"><h3>{{ addr }}</h3><span>{{ house?.layout }} · {{ house?.area }}㎡</span></div>
      <span class="pill" :class="house?.status">{{ house?.status === 'rented' ? '已出租' : '空置中' }}</span>
    </div>

    <div class="drawer-body" v-if="house">
      <!-- 紧凑条 -->
      <div class="hero-card" :class="{ vh: !isRented }">
        <div class="rent">{{ isRented ? '¥' + house.displayRent : '¥' + house.expectedRent }}<small> /月</small></div>
        <div class="who" v-if="isRented">{{ house.tenantName }}<br/><span style="opacity:.85">{{ maskPhone(house.contract?.tenantPhone) }}</span></div>
        <div class="who" v-else>预期租金</div>
      </div>

      <!-- 租期时间轴 -->
      <div class="block" v-if="isRented && house.leaseProgress">
        <h4><Clock /> 租期进度</h4>
        <div class="timeline">
          <div class="tl-track"><div class="now"></div></div>
          <div class="tl-points">
            <div><span class="lb">起租</span><span class="dt">{{ house.leaseProgress.startDate }}</span></div>
            <div style="text-align:center"><span class="lb">已 {{ house.leaseProgress.daysElapsed }} 天</span><span class="dt">剩 {{ house.leaseProgress.daysRemaining }} 天</span></div>
            <div style="text-align:right"><span class="lb">到期</span><span class="dt">{{ house.leaseProgress.endDate }}</span></div>
          </div>
        </div>
      </div>

      <!-- 租赁条款 -->
      <div class="block" v-if="isRented && house.contract">
        <h4><FileText /> 租赁条款</h4>
        <div class="terms">
          <div class="term"><div class="k"><Wallet /> 押几付几</div><div class="v">{{ house.contract.paymentMode }}</div></div>
          <div class="term"><div class="k"><Coins /> 押金</div><div class="v">¥{{ house.contract.deposit }}</div></div>
          <div class="term"><div class="k"><CalendarDays /> 付款日</div><div class="v">每月 {{ house.contract.payDay }} 号</div></div>
          <div class="term"><div class="k"><Zap /> 实际月租</div><div class="v">¥{{ house.contract.actualRent }}</div></div>
          <div class="term"><div class="k"><Zap /> 电表起度</div><div class="v">{{ house.contract.electricStart ?? '—' }}</div></div>
          <div class="term"><div class="k"><Zap /> 水表起度</div><div class="v">{{ house.contract.waterStart ?? '—' }}</div></div>
        </div>
        <div v-if="house.contract.note" style="margin-top:10px;font-size:12px;color:var(--c-text-2)">备注：{{ house.contract.note }}</div>
      </div>

      <!-- 付款日历 -->
      <div class="block" v-if="isRented && house.paymentCalendar">
        <h4><CalendarDays /> {{ house.paymentCalendar.year }} 年 {{ house.paymentCalendar.month }} 月付款日历</h4>
        <div class="cal-note">每月 <b>{{ house.paymentCalendar.payDay }}</b> 号收款 · 高亮为付款日，实心为今天</div>
        <div class="cal">
          <div class="wd" v-for="w in ['日','一','二','三','四','五','六']" :key="w">{{ w }}</div>
          <div class="d" v-for="(c, i) in house.paymentCalendar.days" :key="i"
               :class="{ pay: c.isPayDay, today: c.isToday, empty: c.day === 0 }">
            {{ c.day === 0 ? '' : c.day }}
          </div>
        </div>
      </div>

      <!-- 合同历史 -->
      <div class="block" v-if="house.contractHistory.length">
        <h4><FileText /> 合同历史（{{ house.contractHistory.length }}）</h4>
        <div class="hist-row" v-for="h in house.contractHistory" :key="h.id">
          <div class="lt">¥{{ h.actualRent }}/月 · {{ h.tenantName }}</div>
          <div class="ls">{{ h.startDate }} ~ {{ h.endDate }} · {{ h.status === 'active' ? '生效中' : (h.endReason === 'breach' ? '违约退租' : h.endReason === 'renewed' ? '续租归档' : '到期退租') }}<span v-if="h.endReason === 'breach'"> · 没收押金 ¥{{ h.forfeitedDeposit }}</span></div>
        </div>
      </div>

      <!-- 操作区 -->
      <div style="display:flex;gap:10px;flex-wrap:wrap">
        <button v-if="!isRented" class="copy-main" style="flex:1" @click="openModal('lease')"><User /> 登记出租</button>
        <template v-else>
          <button class="copy-main" style="flex:1" @click="openModal('renew')"><Repeat /> 续租</button>
          <button v-if="!breach" class="resend" style="flex:1;background:#FBE4E4;color:var(--c-danger)" :disabled="confirming" @click="startVacate"><LogOut /> 退租</button>
        </template>
        <button class="resend" @click="openModal('edit')"><Pencil /> 编辑</button>
        <button class="resend" style="background:#FBE4E4;color:var(--c-danger)" @click="doDelete"><Trash2 /> 删除</button>
      </div>

      <!-- 违约确认 -->
      <div v-if="breach" class="breach-box">
        <b>⚠ 提前退租确认</b>
        <div class="breach-grid">
          <div><span>房源</span><b>{{ breach.houseName }}</b></div>
          <div><span>租客</span><b>{{ breach.tenantName }}</b></div>
          <div><span>原定到期</span><b>{{ breach.endDate }}</b></div>
          <div><span>提前天数</span><b>{{ breach.daysEarly }} 天</b></div>
          <div><span>押金</span><b>¥{{ breach.deposit }}</b></div>
        </div>
        <p>{{ breach.message }}</p>
        <div style="display:flex;gap:10px;margin-top:14px">
          <button class="copy-main" style="flex:1;background:linear-gradient(135deg,#E5484D,#F0736F)" :disabled="confirming" @click="confirmBreach">确认退租并没收押金</button>
          <button class="resend" style="flex:0 0 auto" @click="breach=null">取消</button>
        </div>
      </div>
    </div>

    <LeaseModal v-if="house" :open="modalOpen" :mode="modalMode" :house="house" @close="modalOpen = false" @done="afterModal" />
  </aside>
</template>
