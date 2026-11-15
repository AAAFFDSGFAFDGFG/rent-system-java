// 统一 API 层：fetch 封装（code/traceId/1001 跳转）+ 27 个接口

export interface ApiResult<T> { code: number; message?: string; data: T; traceId?: string }

export class ApiError extends Error {
  code: number
  fields?: string[]
  data?: any
  constructor(code: number, message: string, data?: any) {
    super(message)
    this.code = code
    this.data = data
  }
}

export function clearSession() {
  localStorage.removeItem('rs_user')
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(path, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  })
  let json: ApiResult<T>
  try { json = await res.json() } catch { throw new ApiError(res.status, '响应解析失败') }
  if (json.code === 1001) {
    clearSession()
    if (location.hash !== '#/login') location.hash = '#/login'
    throw new ApiError(1001, json.message || '登录失效')
  }
  if (json.code !== 0) {
    throw new ApiError(json.code, json.message || '请求失败', (json as any).data)
  }
  return json.data
}

// ---------------- 类型 ----------------
export interface MemberProfile { username: string; phone?: string; sendKeyTail: string; pushEnabled: boolean; digestSendTime?: string }
export interface HouseSummary { id: number; community: string; building: string; floorNo: number; roomNo: string; status: string; layout: string; area: number; expectedRent: number; actualRent: number; tenantName: string | null; deposit: number; payDay: number; daysToExpiry: number; currentContractId: number | null; contractCount: number; displayRent: number }
export interface ContractDto { id: number; houseId: number; tenantName: string; tenantPhone?: string; actualRent: number; paymentMode: string; deposit: number; startDate: string; endDate: string; payDay: number; electricStart: number | null; waterStart: number | null; note?: string; status: string; terminatedAt?: string; endReason?: string; forfeitedDeposit: number; predecessorId: number | null }
export interface ContractHistoryDto { id: number; startDate: string; endDate: string; actualRent: number; tenantName: string; status: string; endReason?: string; terminatedAt?: string; forfeitedDeposit: number }
export interface LeaseProgressDto { startDate: string; endDate: string; today: string; daysTotal: number; daysElapsed: number; daysRemaining: number; progressPercent: number }
export interface PaymentCalendarDto { year: number; month: number; payDay: number; days: DayCell[] }
export interface DayCell { day: number; isToday: boolean; isPayDay: boolean }
export interface HouseDetail { id: number; community: string; building: string; floorNo: number; roomNo: string; status: string; layout: string; area: number; expectedRent: number; actualRent: number; deposit: number; payDay: number; tenantName: string | null; contract: ContractDto | null; contractHistory: ContractHistoryDto[]; leaseProgress: LeaseProgressDto | null; paymentCalendar: PaymentCalendarDto | null; displayRent: number; note?: string; electricStart?: number | null; waterStart?: number | null }
export interface DashboardStats { totalHouses: number; rentedHouses: number; vacantHouses: number; pendingCollectionCount: number; paymentReminderCount: number; communityCount: number }
export interface FinanceBreakdown { houseId: number; contractId: number; days: number; dailyRent: number; amount: number }
export interface ForfeitedDetail { houseId: number; contractId: number; amount: number; terminatedAt?: string; tenantName?: string }
export interface FinanceIncome { year: number; month: number | null; amount: number; monthExpected: number; yearTotal: number; forfeitedDeposit: number; forfeitedDetails: ForfeitedDetail[]; breakdown: FinanceBreakdown[]; asOf: string }
export interface CalendarEventDay { day: number; types: string[]; houseIds: number[] }
export interface CalendarDto { year: number; month: number; today: string; eventDays: CalendarEventDay[] }
export interface CalendarItem { houseId: number; houseName: string; type: string; tenantName?: string; actualRent: number; paymentMode: string }
export interface CalendarDayDto { date: string; items: CalendarItem[] }
export interface UpcomingItem { houseId: number; contractId: number; houseName: string; type: string; date: string; daysLeft: number; tenantName?: string; urgent: boolean }
export interface DigestSummary { vacantCount: number; upcomingCount: number }
export interface DigestToday { reportDate: string; summary: DigestSummary; contentText: string; generated: boolean; channel: string; sentMemberCount: number }
export interface DigestTemplate { id: number; name: string; contentText: string; variables: string[]; isDefault: boolean; updatedAt?: string; renderedPreview: string; restored?: boolean }
export interface SystemConfig { digestSendTime: string; remindAdvanceDays: number; sendOnEmptyDay: boolean }
export interface NotificationRecord { id: number; reportDate: string; channel: string | null; status: string; memberUsername: string; sentAt?: string; retryCount: number; canResend: boolean }
export interface BreachPreview { houseId: number; houseName: string; tenantName: string; endDate: string; today: string; daysEarly: number; deposit: number; message: string }

// ---------------- 快捷账号 ----------------
const rawQuick = (import.meta.env.VITE_QUICK_ACCOUNTS as string) || '[]'
export const quickAccounts: { username: string; sendKey: string }[] = (() => {
  try { return JSON.parse(rawQuick) } catch { return [] }
})()

// ---------------- 接口 ----------------
export const api = {
  login: (username: string, sendKey: string) =>
    request<MemberProfile>('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, sendKey }) }),
  me: () => request<MemberProfile>('/api/auth/me'),

  dashboard: () => request<DashboardStats>('/api/dashboard/stats'),
  houses: (status = 'all', community?: string, building?: string) => {
    const p = new URLSearchParams()
    if (status && status !== 'all') p.set('status', status)
    if (community) p.set('community', community)
    if (building) p.set('building', building)
    const qs = p.toString()
    return request<HouseSummary[]>('/api/houses' + (qs ? '?' + qs : ''))
  },
  houseDetail: (id: number) => request<HouseDetail>(`/api/houses/${id}`),
  houseUpsert: (body: any) => request<HouseDetail>('/api/houses', { method: 'POST', body: JSON.stringify(body) }),
  houseEdit: (id: number, body: any) => request<HouseDetail>(`/api/houses/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  houseDelete: (id: number) => request<void>(`/api/houses/${id}`, { method: 'DELETE' }),
  communities: () => request<string[]>('/api/communities'),

  lease: (id: number, body: any) => request<HouseDetail>(`/api/houses/${id}/lease`, { method: 'POST', body: JSON.stringify(body) }),
  vacate: (id: number, confirmBreach: boolean) => request<any>(`/api/houses/${id}/vacate`, { method: 'POST', body: JSON.stringify({ confirmBreach }) }),
  renew: (id: number, body: any) => request<HouseDetail>(`/api/houses/${id}/renew`, { method: 'POST', body: JSON.stringify(body) }),

  financeIncome: (year: number, month?: string) =>
    request<FinanceIncome>(`/api/finance/income?year=${year}` + (month ? `&month=${month}` : '')),

  calendar: (year: number, month: number) => request<CalendarDto>(`/api/calendar?year=${year}&month=${month}`),
  calendarDay: (date: string) => request<CalendarDayDto>(`/api/calendar/days/${date}`),
  upcoming: () => request<UpcomingItem[]>('/api/calendar/upcoming'),

  digestToday: () => request<DigestToday>('/api/digests/today'),
  template: () => request<DigestTemplate>('/api/config/digest-template'),
  templateSave: (contentText = '') => request<DigestTemplate>('/api/config/digest-template', { method: 'PUT', body: JSON.stringify({ contentText }) }),
  templatePreview: (contentText = '') => request<{ renderedPreview: string }>('/api/config/digest-template/preview', { method: 'POST', body: JSON.stringify({ contentText }) }),
  templateReset: () => request<DigestTemplate>('/api/config/digest-template/reset', { method: 'POST' }),
  systemConfig: () => request<SystemConfig>('/api/config/system'),

  notifications: (limit = 20) => request<NotificationRecord[]>(`/api/notifications?limit=${limit}`),
  resend: (id: number) => request<NotificationRecord>(`/api/notifications/${id}/resend`, { method: 'POST' }),

  pushSettings: (body: any) => request<MemberProfile>('/api/members/me/push-enabled', { method: 'PATCH', body: JSON.stringify(body) })
}
