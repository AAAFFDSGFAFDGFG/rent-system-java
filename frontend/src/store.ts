import { reactive } from 'vue'
import { api, MemberProfile, DashboardStats, HouseSummary, FinanceIncome, CalendarDto, DigestToday } from './api'

interface ToastState { show: boolean; text: string; timer: any }

export const state = reactive({
  user: (localStorage.getItem('rs_user') ? JSON.parse(localStorage.getItem('rs_user')!) : null) as MemberProfile | null,
  dashboard: null as DashboardStats | null,
  houses: [] as HouseSummary[],
  finance: null as FinanceIncome | null,
  calendar: null as CalendarDto | null,
  digest: null as DigestToday | null,
  filter: { status: 'all', community: '', building: '' },
  year: new Date().getFullYear(),
  month: new Date().getMonth() + 1,
  toast: { show: false, text: '', timer: null as any } as ToastState
})

export function setUser(u: MemberProfile) {
  state.user = u
  localStorage.setItem('rs_user', JSON.stringify(u))
  localStorage.setItem('rs_username', u.username)
}

export function logout() {
  state.user = null
  localStorage.removeItem('rs_user')
  localStorage.removeItem('rs_username')
}

export function toast(text: string) {
  state.toast.text = text
  state.toast.show = true
  if (state.toast.timer) clearTimeout(state.toast.timer)
  state.toast.timer = setTimeout(() => { state.toast.show = false }, 2400)
}

// 链路 H：写操作后全局刷新（依次重拉 5 类数据）
export async function refreshAll() {
  const [dashboard, houses, finance, calendar, digest] = await Promise.all([
    api.dashboard(),
    api.houses(state.filter.status, state.filter.community || undefined),
    api.financeIncome(state.year, String(state.month)),
    api.calendar(state.year, state.month),
    api.digestToday()
  ])
  state.dashboard = dashboard
  state.houses = houses
  state.finance = finance
  state.calendar = calendar
  state.digest = digest
}

export async function loadHousesOnly() {
  state.houses = await api.houses(state.filter.status, state.filter.community || undefined)
}
