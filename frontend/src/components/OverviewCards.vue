<script setup lang="ts">
import { computed } from 'vue'
import { api } from '../api'
import { state } from '../store'

withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })

const yearOptions = computed(() => {
  const y = new Date().getFullYear()
  return [y - 1, y, y + 1]
})

function fmt(n: number) {
  return Number(n).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function changeYear(y: number) {
  state.year = y
  state.finance = await api.financeIncome(state.year, String(state.month))
}
async function changeMonth(m: string) {
  state.month = m === 'all' ? 0 : Number(m)
  state.finance = await api.financeIncome(state.year, m === 'all' ? undefined : m)
}
</script>

<template>
  <div class="overview">
    <div :class="compact ? 'phone-stats' : 'stats'">
      <div class="stat s1"><div class="k">总房源</div><div class="v">{{ state.dashboard?.totalHouses ?? '—' }}</div><div class="sub">在管房源总数</div></div>
      <div class="stat s2"><div class="k">已出租</div><div class="v">{{ state.dashboard?.rentedHouses ?? '—' }}</div><div class="sub">稳定现金流入</div></div>
      <div class="stat s3"><div class="k">空置中</div><div class="v">{{ state.dashboard?.vacantHouses ?? '—' }}</div><div class="sub">待招租 {{ state.dashboard?.communityCount ?? 0 }} 个小区</div></div>
      <div class="stat s4"><div class="k">本月待收租</div><div class="v">{{ state.dashboard?.pendingCollectionCount ?? '—' }}</div><div class="sub">套 · {{ state.dashboard?.paymentReminderCount ?? 0 }} 项提醒</div></div>
    </div>

    <div class="finance-card">
      <div class="fin-head">
        <b>财产卡</b>
        <select class="fsel" :value="state.year" @change="changeYear(Number(($event.target as HTMLSelectElement).value))">
          <option v-for="y in yearOptions" :key="y" :value="y">{{ y }} 年</option>
        </select>
        <select class="fsel" :value="state.month === 0 ? 'all' : state.month" @change="changeMonth(($event.target as HTMLSelectElement).value)">
          <option value="all">全年</option>
          <option v-for="m in 12" :key="m" :value="m">{{ m }} 月</option>
        </select>
      </div>
      <div class="fin-title">区间累计收入</div>
      <div class="fin-value">¥ {{ state.finance ? fmt(state.finance.amount) : '0.00' }}</div>
      <div class="fin-rows">
        <div><span>本区间没收押金</span><b style="color:var(--c-danger)">¥ {{ state.finance ? fmt(state.finance.forfeitedDeposit) : '0.00' }}</b></div>
        <div><span>当月预计（在租）</span><b>¥ {{ state.finance ? fmt(state.finance.monthExpected) : '0.00' }}</b></div>
        <div><span>当年累计</span><b>¥ {{ state.finance ? fmt(state.finance.yearTotal) : '0.00' }}</b></div>
        <div><span>数据截止</span><b>{{ state.finance?.asOf }}</b></div>
      </div>
    </div>
  </div>
</template>
