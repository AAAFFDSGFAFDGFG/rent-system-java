<script setup lang="ts">
import { X, Wallet, Clock, User } from 'lucide-vue-next'
import { CalendarItem } from '../api'

defineProps<{ date: string; items: CalendarItem[]; open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void }>()
</script>

<template>
  <Teleport to="body">
    <div class="modal-overlay" :class="{ show: open }" @click="emit('close')"></div>
    <div class="modal" :class="{ show: open }">
      <div class="modal-head">
        <Clock style="width:20px;height:20px;color:var(--c-primary)" />
        <h3>{{ date }} 待办事项</h3>
        <button class="close" @click="emit('close')"><X /></button>
      </div>
      <div class="modal-body">
        <div v-if="!items.length" style="text-align:center;color:var(--c-text-2);padding:14px 0">当日暂无事项 🌿</div>
        <div v-for="it in items" :key="it.houseId + it.type" class="todo" style="cursor:default">
          <div class="ic" :class="it.type === 'pay' ? 'pay' : 'exp'">
            <component :is="it.type === 'pay' ? Wallet : Clock" />
          </div>
          <div class="body">
            <b>{{ it.houseName }}</b>
            <span>{{ it.type === 'pay' ? '付款日' : '租约到期' }} · {{ it.tenantName }} · ¥{{ it.actualRent }}/月 · {{ it.paymentMode }}</span>
          </div>
        </div>
        <button class="copy-main" @click="emit('close')">知道了</button>
      </div>
    </div>
  </Teleport>
</template>
