<script setup lang="ts">
import { ref } from 'vue'
import { Home, Lock, Key, ChevronRight } from 'lucide-vue-next'
import { api, quickAccounts, ApiError } from '../api'
import { setUser, toast, state, refreshAll } from '../store'

const username = ref('')
const sendKey = ref('')
const loading = ref(false)
const submitting = ref(false)

async function submit() {
  if (!username.value || !sendKey.value) { toast('请输入用户名和 SendKey'); return }
  submitting.value = true
  try {
    const u = await api.login(username.value.trim(), sendKey.value.trim())
    setUser(u)
    toast('登录成功，欢迎回家 🏡')
    await refreshAll()
    location.hash = '#/home'
  } catch (e) {
    if (e instanceof ApiError) toast(e.message)
    else toast('登录失败')
  } finally {
    submitting.value = false
  }
}

function quick(acc: { username: string; sendKey: string }) {
  username.value = acc.username
  sendKey.value = acc.sendKey
  submit()
}
</script>

<template>
  <div class="login-screen">
    <div class="login-card">
      <div class="login-logo"><Home /></div>
      <h1>家庭房源</h1>
      <div class="login-sub">家庭房屋租赁管理 · 让每一套房子都被照顾</div>

      <div class="login-demo">
        <span>一键登录：</span>
        <button v-for="a in quickAccounts" :key="a.username" @click="quick(a)">{{ a.username }}</button>
      </div>

      <div style="margin-top:18px;text-align:left">
        <label class="login-field">
          <Lock />
          <input v-model="username" placeholder="用户名" autocomplete="username" @keyup.enter="submit" />
        </label>
        <label class="login-field">
          <Key />
          <input v-model="sendKey" type="password" placeholder="Server酱 SendKey" autocomplete="off" @keyup.enter="submit" />
        </label>
        <button class="login-btn" :disabled="submitting" @click="submit">
          {{ submitting ? '登录中…' : '登 录' }}<ChevronRight v-if="!submitting" style="display:inline;width:16px;height:16px;vertical-align:-3px;margin-left:4px" />
        </button>
      </div>
      <div class="login-tip">家人各自用本人 SendKey 登录，每日在设定时间收到聚合日报推送到微信</div>
    </div>
  </div>
</template>
