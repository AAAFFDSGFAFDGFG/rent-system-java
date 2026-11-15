<script setup lang="ts">
import { ref, onMounted, reactive, computed, watch } from 'vue'
import { Plus, Pencil, Trash2, Home, Copy, Check, RotateCcw, Bell, Send } from 'lucide-vue-next'
import { api, ApiError, HouseDetail, DigestTemplate, MemberProfile } from '../api'
import { state, toast, refreshAll, loadHousesOnly } from '../store'
import DetailDrawer from '../components/DetailDrawer.vue'
import LeaseModal from '../components/LeaseModal.vue'

const drawerOpen = ref(false)
const current = ref<HouseDetail | null>(null)
const createOpen = ref(false)
const profile = ref<MemberProfile | null>(null)
const pushEnabled = ref(true)
const pushTime = ref('09:00')

// 模板
const tpl = reactive({ text: '', preview: '', restored: false })
const saveState = ref<'idle' | 'saving' | 'saved'>('idle')
const copied = ref(false)
let debounce: any = null

const grouped = computed(() => {
  const m = new Map<string, any[]>()
  state.houses.forEach(h => { if (!m.has(h.community)) m.set(h.community, []); m.get(h.community)!.push(h) })
  return [...m.entries()]
})

async function openHouse(id: number) {
  try { current.value = await api.houseDetail(id); drawerOpen.value = true }
  catch (e) { if (e instanceof ApiError) toast(e.message) }
}

async function afterChange() { await refreshAll(); await loadHousesOnly() }

onMounted(async () => {
  try { state.houses = await api.houses('all'); } catch {}
  try { profile.value = await api.me() } catch {}
  try {
    const t = await api.template()
    tpl.text = t.contentText
    tpl.preview = t.renderedPreview
  } catch {}
  if (profile.value) { pushEnabled.value = profile.value.pushEnabled; pushTime.value = (profile.value.digestSendTime || '09:00').slice(0, 5) }
})

function onTplInput() {
  saveState.value = 'idle'
  if (debounce) clearTimeout(debounce)
  debounce = setTimeout(async () => {
    try { const r = await api.templatePreview(tpl.text); tpl.preview = r.renderedPreview } catch {}
  }, 300)
}
async function saveTpl() {
  saveState.value = 'saving'
  try { const t: DigestTemplate = await api.templateSave(tpl.text); tpl.text = t.contentText; tpl.preview = t.renderedPreview; saveState.value = 'saved'; setTimeout(() => saveState.value = 'idle', 1800) }
  catch (e) { if (e instanceof ApiError) toast(e.message); saveState.value = 'idle' }
}
async function resetTpl() {
  try { const t: DigestTemplate = await api.templateReset(); tpl.text = t.contentText; tpl.preview = t.renderedPreview; toast('已恢复默认模板'); saveState.value = 'saved'; setTimeout(() => saveState.value = 'idle', 1800) }
  catch (e) { if (e instanceof ApiError) toast(e.message) }
}
async function copyTpl() {
  try { await navigator.clipboard.writeText(tpl.preview); copied.value = true; setTimeout(() => copied.value = false, 1800) } catch { toast('复制失败') }
}
async function savePush() {
  try {
    const u = await api.pushSettings({ pushEnabled: pushEnabled.value, digestSendTime: pushTime.value })
    profile.value = u
    toast('推送设置已保存')
  } catch (e) { if (e instanceof ApiError) toast(e.message) }
}
</script>

<template>
  <section class="page active" id="page-settings">
    <div class="setting-grid">
      <!-- 左列：房源管理 -->
      <div class="setting-col">
        <div class="setting-card">
          <div class="section-head" style="margin-bottom:0">
            <h4 style="font-size:15px">房源管理</h4>
            <span class="hint">{{ state.houses.length }} 套</span>
            <button class="copy-main" style="flex:none;padding:9px 16px;font-size:13px;margin-left:auto" @click="createOpen = true"><Plus style="width:15px;height:15px" /> 新增房源</button>
          </div>
          <div style="margin-top:16px;display:flex;flex-direction:column;gap:18px">
            <div v-for="[community, list] in grouped" :key="community">
              <div style="font-size:13px;font-weight:800;color:var(--c-text-2);margin-bottom:8px">{{ community }}</div>
              <div style="display:flex;flex-direction:column;gap:8px">
                <div v-for="h in list" :key="h.id" class="row-line" style="cursor:pointer" @click="openHouse(h.id)">
                  <div style="display:flex;align-items:center;gap:10px;flex:1;min-width:0">
                    <Home style="width:17px;height:17px;color:var(--c-primary);flex:none" />
                    <div style="min-width:0">
                      <div style="font-size:14px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ h.building }} {{ h.floorNo }}F - {{ h.roomNo }}</div>
                      <div style="font-size:11.5px;color:var(--c-text-2)">{{ h.layout }} · ¥{{ h.status === 'rented' ? h.displayRent : h.expectedRent }}/月</div>
                    </div>
                  </div>
                  <span class="state" :class="h.status" style="padding:3px 9px;border-radius:16px;font-size:11px;font-weight:700">{{ h.status === 'rented' ? '已租' : '空置' }}</span>
                  <button class="resend" @click.stop="openHouse(h.id)"><Pencil style="width:13px;height:13px" /> 管理</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右列：推送配置 + 模板 -->
      <div class="setting-col">
        <div class="setting-card">
          <div class="section-head" style="margin-bottom:0"><h4 style="font-size:15px">推送配置</h4></div>
          <p class="desc">家人各自在设定时间收到聚合日报推送到微信</p>
          <div style="display:flex;align-items:center;gap:12px;margin-bottom:14px">
            <div class="avatar">{{ profile?.username?.charAt(0)?.toUpperCase() || '家' }}</div>
            <div style="flex:1"><div style="font-size:14px;font-weight:700">{{ profile?.username || '—' }}</div><div style="font-size:12px;color:var(--c-text-2)">SendKey 尾号 {{ profile?.sendKeyTail || '—' }}</div></div>
            <div class="switch" :class="{ on: pushEnabled }" @click="pushEnabled = !pushEnabled"></div>
          </div>
          <div class="row-line push-time-row">
            <div class="lt">每日推送时间</div>
            <select class="fsel" :value="pushTime" @change="pushTime = ($event.target as HTMLSelectElement).value" style="min-width:90px">
              <option v-for="h in 24" :key="h - 1" :value="String(h - 1).padStart(2, '0') + ':00'">{{ String(h - 1).padStart(2, '0') }}:00</option>
            </select>
          </div>
          <button class="copy-main" style="margin-top:6px" @click="savePush"><Send style="width:16px;height:16px" /> 保存推送设置</button>
        </div>

        <div class="setting-card">
          <div class="section-head" style="margin-bottom:0"><h4 style="font-size:15px">每日分发文案模板</h4></div>
          <p class="desc">支持 5 个变量；保存后当日已生成正文不变，次日新模板生效</p>
          <textarea class="tpl-area" v-model="tpl.text" @input="onTplInput" placeholder="【家庭房源日报】{日期}..."></textarea>
          <div class="tpl-preview">{{ tpl.preview }}</div>
          <div class="tpl-actions">
            <button class="copy-main" @click="copyTpl"><component :is="copied ? Check : Copy" style="width:15px;height:15px" /> {{ copied ? '已复制' : '复制预览' }}</button>
            <button class="resend" @click="resetTpl"><RotateCcw style="width:13px;height:13px" /> 恢复默认</button>
            <button class="resend" :disabled="saveState === 'saving'" @click="saveTpl">{{ saveState === 'saved' ? '已保存' : '保存' }}</button>
          </div>
        </div>
      </div>
    </div>

    <DetailDrawer :house="current" :open="drawerOpen" @close="drawerOpen = false" @changed="afterChange" />
    <LeaseModal v-if="createOpen" :open="createOpen" mode="create" @close="createOpen = false" @done="() => { createOpen = false; afterChange() }" />
  </section>
</template>
