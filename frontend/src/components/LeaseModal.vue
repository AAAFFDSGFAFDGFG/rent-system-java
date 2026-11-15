<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { X, User, Phone, Wallet, Coins, CalendarDays, Zap, FileText, Repeat, Pencil } from 'lucide-vue-next'
import { api, ApiError, HouseDetail } from '../api'
import { toast } from '../store'

const props = defineProps<{ open: boolean; mode: 'lease' | 'renew' | 'edit' | 'create'; house?: HouseDetail | null; houseId?: number }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'done'): void }>()

const today = new Date().toISOString().slice(0, 10)
const f = reactive<any>({})
const errors = reactive<Record<string, string>>({})
const submitting = ref(false)

function openReset() {
  Object.keys(f).forEach(k => delete f[k])
  Object.keys(errors).forEach(k => delete errors[k])
  if (props.mode === 'lease') {
    f.startDate = today
    f.endDate = new Date(new Date().setFullYear(new Date().getFullYear() + 1)).toISOString().slice(0, 10)
    f.payDay = 18
    f.paymentMode = '押一付三'
    f.deposit = ''
    f.actualRent = ''
  } else if (props.mode === 'renew' && props.house) {
    const c = props.house.contract!
    f.newEndDate = new Date(new Date(c.endDate).getTime() + 86400000 * 400).toISOString().slice(0, 10)
    f.newActualRent = c.actualRent ?? ''
    f.newPaymentMode = c.paymentMode ?? '押一付三'
    f.newDeposit = c.deposit ?? ''
  } else if (props.mode === 'edit' && props.house) {
    f.community = props.house.community
    f.building = props.house.building
    f.floorNo = props.house.floorNo
    f.roomNo = props.house.roomNo
    f.layout = props.house.layout
    f.area = props.house.area
    f.expectedRent = props.house.expectedRent
  } else {
    f.community = ''; f.building = ''; f.floorNo = ''; f.roomNo = ''; f.layout = ''; f.area = ''; f.expectedRent = ''
  }
}
watch(() => props.open, (v) => { if (v) openReset() }, { immediate: true })

const titleMap = { lease: '登记出租', renew: '续租', edit: '编辑房源', create: '新增房源' }
const iconMap = { lease: User, renew: Repeat, edit: Pencil, create: Pencil }

function validate() {
  Object.keys(errors).forEach(k => delete errors[k])
  if (props.mode === 'lease') {
    if (!f.tenantName?.trim()) errors.tenantName = '请填写租客姓名'
    const rent = Number(f.actualRent)
    if (!rent || rent <= 0) errors.actualRent = '租金需大于 0'
    const payDay = Number(f.payDay)
    if (!Number.isInteger(payDay) || payDay < 1 || payDay > 31) errors.payDay = '付款日 1-31'
    if (f.startDate && f.endDate && f.endDate <= f.startDate) errors.endDate = '结束日期须晚于开始日期'
    const dep = Number(f.deposit)
    if (f.deposit !== '' && (isNaN(dep) || dep < 0)) errors.deposit = '押金不能为负'
  } else if (props.mode === 'renew') {
    const rent = Number(f.newActualRent)
    if (!rent || rent <= 0) errors.newActualRent = '新租金需大于 0'
    if (!f.newEndDate) errors.newEndDate = '请选择新到期日'
    else if (props.house?.contract && f.newEndDate <= props.house.contract.endDate) errors.newEndDate = '新到期日须晚于当前到期日'
  } else if (props.mode === 'create') {
    if (!f.community?.trim()) errors.community = '请填写小区'
    if (!f.building?.trim()) errors.building = '请填写楼栋'
    if (f.floorNo === '' || isNaN(Number(f.floorNo))) errors.floorNo = '楼层必填'
    if (!f.roomNo?.trim()) errors.roomNo = '请填写房号'
    if (!f.layout?.trim()) errors.layout = '请填写户型'
    if (!f.area || Number(f.area) <= 0) errors.area = '面积需大于 0'
    if (!f.expectedRent || Number(f.expectedRent) <= 0) errors.expectedRent = '预期租金需大于 0'
  }
  return Object.keys(errors).length === 0
}

async function submit() {
  if (!validate()) return
  submitting.value = true
  try {
    if (props.mode === 'lease') {
      await api.lease((props.houseId ?? props.house!.id), {
        tenantName: f.tenantName, tenantPhone: f.tenantPhone || undefined, actualRent: Number(f.actualRent),
        paymentMode: f.paymentMode, deposit: f.deposit === '' ? undefined : Number(f.deposit),
        payDay: Number(f.payDay), startDate: f.startDate, endDate: f.endDate,
        electricStart: f.electricStart === '' ? undefined : Number(f.electricStart),
        waterStart: f.waterStart === '' ? undefined : Number(f.waterStart), note: f.note || undefined
      })
      toast('登记成功，房源已转为已出租 🎉')
    } else if (props.mode === 'renew') {
      await api.renew(props.house!.id, {
        newEndDate: f.newEndDate, newActualRent: Number(f.newActualRent),
        newDeposit: f.newDeposit === '' ? undefined : Number(f.newDeposit), newPaymentMode: f.newPaymentMode
      })
      toast('续租成功，新合同已生效')
    } else if (props.mode === 'create') {
      await api.houseUpsert({
        community: f.community, building: f.building, floorNo: Number(f.floorNo),
        roomNo: f.roomNo, layout: f.layout, area: Number(f.area), expectedRent: Number(f.expectedRent)
      })
      toast('房源已新增')
    } else {
      await api.houseEdit(props.house!.id, {
        community: f.community, building: f.building, floorNo: Number(f.floorNo),
        roomNo: f.roomNo, layout: f.layout, area: Number(f.area), expectedRent: Number(f.expectedRent)
      })
      toast('房源信息已更新')
    }
    emit('done')
  } catch (e: any) {
    if (e instanceof ApiError) toast(e.message)
  } finally { submitting.value = false }
}
</script>

<template>
  <Teleport to="body">
    <div class="modal-overlay" :class="{ show: open }" @click="emit('close')"></div>
    <div class="modal" :class="{ show: open }">
      <div class="modal-head">
        <component :is="iconMap[mode]" style="width:20px;height:20px;color:var(--c-primary)" />
        <h3>{{ titleMap[mode] }}</h3>
        <button class="close" @click="emit('close')"><X /></button>
      </div>
      <div class="modal-body">
        <!-- 登记出租 -->
        <template v-if="mode === 'lease'">
          <div class="frm-grid">
            <label>租客姓名 *<input v-model="f.tenantName" placeholder="如 张伟" /></label>
            <label>联系电话<input v-model="f.tenantPhone" placeholder="选填" /></label>
            <label>实际月租 *<input v-model="f.actualRent" type="number" placeholder=">0" /></label>
            <label>付款方式<input v-model="f.paymentMode" placeholder="押一付三" /></label>
            <label>押金<input v-model="f.deposit" type="number" placeholder="选填，默认 0" /></label>
            <label>付款日 1-31<input v-model="f.payDay" type="number" /></label>
            <label>起租日期<input v-model="f.startDate" type="date" /></label>
            <label>到期日期<input v-model="f.endDate" type="date" /></label>
            <label>电表起度<input v-model="f.electricStart" type="number" placeholder="选填" /></label>
            <label>水表起度<input v-model="f.waterStart" type="number" placeholder="选填" /></label>
          </div>
          <label style="font-size:12px;color:var(--c-text-2);font-weight:600;display:flex;flex-direction:column;gap:5px">
            备注<input v-model="f.note" placeholder="选填，如 含车位" />
          </label>
        </template>

        <!-- 续租 -->
        <template v-else-if="mode === 'renew'">
          <div class="renew-info">当前合同 ¥{{ house?.contract?.actualRent }}/月，租期至 {{ house?.contract?.endDate }}。续租后旧合同归档，新合同自 {{ house?.contract?.endDate }} 次日起按新租金生效。</div>
          <div class="frm-grid">
            <label>新到期日 *<input v-model="f.newEndDate" type="date" /></label>
            <label>新实际月租 *<input v-model="f.newActualRent" type="number" /></label>
            <label>新押金<input v-model="f.newDeposit" type="number" placeholder="留空沿用" /></label>
            <label>新付款方式<input v-model="f.newPaymentMode" placeholder="押一付三" /></label>
          </div>
        </template>

        <!-- 新增/编辑房源 -->
        <template v-else>
          <div class="frm-grid">
            <label>小区<input v-model="f.community" /></label>
            <label>楼栋<input v-model="f.building" /></label>
            <label>楼层<input v-model="f.floorNo" type="number" /></label>
            <label>房号<input v-model="f.roomNo" /></label>
            <label>户型<input v-model="f.layout" placeholder="如 两室一厅" /></label>
            <label>面积㎡<input v-model="f.area" type="number" /></label>
            <label>预期租金<input v-model="f.expectedRent" type="number" /></label>
          </div>
        </template>

        <div style="color:var(--c-danger);font-size:12px" v-if="Object.keys(errors).length">
          请修正：{{ Object.values(errors).join('；') }}
        </div>
        <button class="copy-main" :disabled="submitting" @click="submit">{{ submitting ? '提交中…' : '确认提交' }}</button>
      </div>
    </div>
  </Teleport>
</template>
