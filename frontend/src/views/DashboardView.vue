<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Bell, ChevronDown, User, Copy, Check } from 'lucide-vue-next'
import { api, ApiError, HouseDetail } from '../api'
import { state, toast, refreshAll, loadHousesOnly } from '../store'
import DetailDrawer from '../components/DetailDrawer.vue'
import LeaseModal from '../components/LeaseModal.vue'

const drawerOpen = ref(false)
const current = ref<HouseDetail | null>(null)
const detailLoading = ref(false)
const digestOpen = ref(false)
const copied = ref(false)
const communities = ref<string[]>([])
const buildings = ref<string[]>([])
const quickLeaseId = ref<number | null>(null)
const quickLeaseOpen = ref(false)

onMounted(async () => {
  try { communities.value = await api.communities() } catch {}
})

async function openHouse(id: number) {
  detailLoading.value = true
  try {
    current.value = await api.houseDetail(id)
    drawerOpen.value = true
  } catch (e) { if (e instanceof ApiError) toast(e.message) }
  finally { detailLoading.value = false }
}

async function onFilter() {
  try { await loadHousesOnly() } catch {}
  // 楼栋级联
  buildings.value = [...new Set(state.houses.filter(h => !state.filter.community || h.community === state.filter.community).map(h => h.building))]
}

function openQuickLease(id: number) {
  quickLeaseId.value = id
  quickLeaseOpen.value = true
}
async function afterQuickLease() {
  quickLeaseOpen.value = false
  await afterDrawerChange()
}

async function copyDigest() {
  try {
    await navigator.clipboard.writeText(state.digest?.contentText || '')
    copied.value = true
    setTimeout(() => copied.value = false, 1800)
  } catch { toast('复制失败') }
}

async function afterDrawerChange() {
  await refreshAll()
  await onFilter()
  if (current.value) {
    try { current.value = await api.houseDetail(current.value.id) } catch {}
  }
}
</script>

<template>
  <section class="page active" id="page-home">
    <!-- 聚合日报卡 -->
    <div class="digest" :class="{ open: digestOpen }" @click="digestOpen = !digestOpen">
      <div class="drow">
        <Bell style="width:18px;height:18px" />
        <b>今日聚合日报</b>
        <span class="dsum">空置 {{ state.digest?.summary.vacantCount }} 套 · 临期 {{ state.digest?.summary.upcomingCount }} 项 · 已推送 {{ state.digest?.sentMemberCount }} 人</span>
        <ChevronDown class="chev" style="width:18px;height:18px" />
      </div>
      <div class="digest-body">
        <pre>{{ state.digest?.contentText }}</pre>
        <button class="copy-main" style="margin-top:12px" @click.stop="copyDigest">
          <component :is="copied ? Check : Copy" /> {{ copied ? '已复制' : '复制正文' }}
        </button>
      </div>
    </div>

    <!-- 筛选 + 房源网格 -->
    <div class="section-head">
      <h2>房源总览</h2>
      <span class="hint">{{ state.houses.length }} 套房源</span>
      <div class="filters">
        <div class="seg">
          <button :class="{ active: state.filter.status === 'all' }" @click="state.filter.status = 'all'; onFilter()">全部</button>
          <button :class="{ active: state.filter.status === 'rented' }" @click="state.filter.status = 'rented'; onFilter()">已出租</button>
          <button :class="{ active: state.filter.status === 'vacant' }" @click="state.filter.status = 'vacant'; onFilter()">空置中</button>
        </div>
        <select class="fsel" v-model="state.filter.community" @change="onFilter()">
          <option value="">全部小区</option>
          <option v-for="c in communities" :key="c" :value="c">{{ c }}</option>
        </select>
        <select class="fsel" v-model="state.filter.building" @change="onFilter()">
          <option value="">全部楼栋</option>
          <option v-for="b in buildings" :key="b" :value="b">{{ b }}</option>
        </select>
      </div>
    </div>

    <div class="empty" :class="{ show: state.houses.length === 0 }">
      <img src="/assets/empty-state.svg" alt="empty" />
      <h4>这里还没有房源</h4>
      <p>点击右下角「新增房源」开始记录你的第一套房子吧～</p>
    </div>

    <div class="cards">
      <div class="card" v-for="h in state.houses" :key="h.id" @click="h.status === 'rented' ? openHouse(h.id) : openQuickLease(h.id)">
        <div class="body">
          <div class="head">
            <div class="addr">{{ h.community }} {{ h.building }}</div>
            <div class="state" :class="h.status">{{ h.status === 'rented' ? '已租' : '空置' }}</div>
          </div>
          <div class="layout">{{ h.floorNo }}F - {{ h.roomNo }} · {{ h.layout }} · {{ h.area }}㎡</div>
          <div class="foot">
            <div class="price">¥{{ h.status === 'rented' ? h.displayRent : h.expectedRent }}<small>/月</small></div>
            <div class="tenant"><User style="width:14px;height:14px" /> {{ h.status === 'rented' ? h.tenantName : '待出租' }}</div>
          </div>
          <button class="cta" :class="h.status" @click.stop="h.status === 'rented' ? openHouse(h.id) : openQuickLease(h.id)">{{ h.status === 'rented' ? '查看详情' : '登记出租' }}</button>
        </div>
      </div>
    </div>

    <DetailDrawer :house="current" :open="drawerOpen" @close="drawerOpen = false" @changed="afterDrawerChange" />
    <LeaseModal v-if="quickLeaseId !== null" :open="quickLeaseOpen" mode="lease" :house-id="quickLeaseId" @close="quickLeaseOpen = false" @done="afterQuickLease" />
  </section>
</template>
