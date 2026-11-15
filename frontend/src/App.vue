<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { Home, CalendarDays, Settings as SettingsIcon, Bell, Check, LogOut } from 'lucide-vue-next'
import { api, ApiError } from './api'
import { state, setUser, logout, toast, refreshAll } from './store'
import LoginView from './views/LoginView.vue'
import DashboardView from './views/DashboardView.vue'
import CalendarView from './views/CalendarView.vue'
import ConfigView from './views/ConfigView.vue'
import OverviewCards from './components/OverviewCards.vue'

const route = ref<'home' | 'calendar' | 'settings'>('home')
const isPhone = ref(false)

function parseHash() {
  const h = location.hash.replace('#/', '')
  if (h === 'calendar') route.value = 'calendar'
  else if (h === 'settings') route.value = 'settings'
  else route.value = 'home'
}
function nav(to: 'home' | 'calendar' | 'settings') { location.hash = '#/' + to }
function goLogin() { logout(); location.hash = '#/login' }

function checkPhone() { isPhone.value = window.innerWidth <= 760 }
window.addEventListener('resize', checkPhone)

onMounted(async () => {
  checkPhone()
  parseHash()
  window.addEventListener('hashchange', parseHash)
  if (state.user) {
    try { await api.me(); await refreshAll() }
    catch (e) { if (e instanceof ApiError && e.code === 1001) { logout() } }
  }
})

const activeComponent = computed(() => {
  if (route.value === 'calendar') return CalendarView
  if (route.value === 'settings') return ConfigView
  return DashboardView
})
const greet = computed(() => { const h = new Date().getHours(); return h < 11 ? '早安' : h < 14 ? '午安' : h < 18 ? '下午好' : '晚上好' })
</script>

<template>
  <LoginView v-if="!state.user" />

  <template v-else>
    <!-- 桌面视图 -->
    <div class="desktop-view" :class="{ 'phone-mode': isPhone }">
      <header class="topbar">
        <div class="brand"><div class="logo"><Home /></div>家庭房源</div>
        <div class="greeting">{{ greet }}，{{ state.user.username }} <b>🏡</b></div>
        <div class="spacer"></div>
        <button class="icon-btn" @click="nav('settings')"><Bell /><span class="dot" v-if="(state.digest?.summary.upcomingCount || 0) > 0"></span></button>
        <button class="icon-btn" @click="goLogin"><LogOut /></button>
      </header>
      <div class="shell">
        <div class="layout">
          <aside class="sidebar">
            <div class="side-nav">
              <button :class="{ active: route === 'home' }" @click="nav('home')"><Home /> 总览看板</button>
              <button :class="{ active: route === 'calendar' }" @click="nav('calendar')"><CalendarDays /> 提醒日历</button>
              <button :class="{ active: route === 'settings' }" @click="nav('settings')"><SettingsIcon /> 配置管理</button>
            </div>
            <OverviewCards />
          </aside>
          <main class="content">
            <component :is="activeComponent" />
          </main>
        </div>
      </div>
    </div>

    <!-- 手机视图 -->
    <div class="phone-view" :class="{ show: isPhone }">
      <div class="device">
        <div class="screen">
          <div class="notch"></div>
          <div class="phone-body">
            <header style="display:flex;align-items:center;gap:10px;padding:6px 4px">
              <div class="brand" style="font-size:17px"><div class="logo" style="width:34px;height:34px"><Home style="width:18px;height:18px" /></div>家庭房源</div>
            </header>
            <OverviewCards v-if="route === 'home'" compact />
            <component :is="activeComponent" />
          </div>
          <nav class="phone-tab">
            <button :class="{ active: route === 'home' }" @click="nav('home')"><Home /> 看板</button>
            <button :class="{ active: route === 'calendar' }" @click="nav('calendar')"><CalendarDays /> 日历</button>
            <button :class="{ active: route === 'settings' }" @click="nav('settings')"><SettingsIcon /> 我的</button>
          </nav>
        </div>
      </div>
    </div>
  </template>

  <!-- Toast -->
  <div class="toast" :class="{ show: state.toast.show }">
    <Check v-if="state.toast.show" /> {{ state.toast.text }}
  </div>
</template>
