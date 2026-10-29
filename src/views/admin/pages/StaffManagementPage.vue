<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '@/services/apiClient'
import { notify } from '../adminStore'

const tab = ref('schedule')
const loading = ref(false)
const employees = ref([])
const assignments = ref([])
const payroll = ref([])
const salaryDrafts = reactive({})
const today = new Date()
function mondayOf(d) {
  const x = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const day = x.getDay() || 7
  x.setDate(x.getDate() - day + 1)
  return x
}
function localIso(d) { return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const weekStart = ref(localIso(mondayOf(today)))
const weekDates = computed(() => {
  const start = new Date(`${weekStart.value}T12:00:00`)
  return Array.from({ length: 7 }, (_, i) => {
    const date = new Date(start); date.setDate(start.getDate() + i)
    return { date: localIso(date), label: date.toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' }) }
  })
})
const shiftDefinitions = [
  { no: 1, time: '06:40–08:40' }, { no: 2, time: '08:45–10:45' }, { no: 3, time: '10:50–12:50' },
  { no: 4, time: '13:00–15:00' }, { no: 5, time: '15:05–17:05' }, { no: 6, time: '17:10–19:10' }, { no: 7, time: '19:15–21:15' },
]
const money = value => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(value) || 0)
const dateLabel = date => new Date(`${date}T12:00:00`).toLocaleDateString('vi-VN')
function slot(date, shiftNo) { return assignments.value.find(a => String(a.workDate).slice(0,10) === date && Number(a.shiftNo) === Number(shiftNo)) }
function shiftEmployee(date, shiftNo) { const found = slot(date, shiftNo); return found ? String(found.employeeId) : '' }
function shiftState(date, shiftNo) { const found = slot(date, shiftNo); return found?.isPresent ? 'Đã chấm công' : found ? 'Đã xếp ca' : 'Chưa xếp' }
async function loadAll() {
  loading.value = true
  try {
    const [staff, schedule, report] = await Promise.all([
      api.get('/admin/staff'),
      api.get(`/admin/staff/schedule?weekStart=${weekStart.value}`),
      api.get(`/admin/staff/payroll?weekStart=${weekStart.value}`),
    ])
    employees.value = Array.isArray(staff) ? staff : []
    assignments.value = Array.isArray(schedule) ? schedule : []
    payroll.value = Array.isArray(report) ? report : []
    for (const employee of employees.value) {
      const id = employee.employeeId ?? employee.id
      if (!salaryDrafts[id]) salaryDrafts[id] = { weeklyBaseSalary: Number(employee.weeklyBaseSalary ?? 800000), expectedShiftsPerWeek: Number(employee.expectedShiftsPerWeek ?? 8) }
      else {
        salaryDrafts[id].weeklyBaseSalary = Number(employee.weeklyBaseSalary ?? salaryDrafts[id].weeklyBaseSalary)
        salaryDrafts[id].expectedShiftsPerWeek = Number(employee.expectedShiftsPerWeek ?? salaryDrafts[id].expectedShiftsPerWeek)
      }
    }
  } catch (error) {
    notify(error?.message || 'Không tải được danh sách nhân viên/ca làm. Hãy chạy migration Employee Shifts/Payroll.', 'error')
  } finally { loading.value = false }
}
async function saveSlot(date, shiftNo, newValue) {
  const employeeUserId = newValue === '' ? null : Number(newValue)
  try {
    await api.put('/admin/staff/schedule', { workDate: date, shiftNo, employeeUserId })
    notify('Đã lưu lịch ca.', 'success')
    await loadAll()
  } catch (error) { notify(error?.message || 'Không thể lưu ca này.', 'error') }
}
async function saveSalary(employee) {
  const id = employee.employeeId ?? employee.id
  const draft = salaryDrafts[id]
  try {
    await api.put(`/admin/staff/${id}/salary`, {
      weeklyBaseSalary: Number(draft.weeklyBaseSalary),
      expectedShiftsPerWeek: Number(draft.expectedShiftsPerWeek),
    })
    notify(`Đã lưu lương của ${employee.fullName || employee.name}.`, 'success')
    await loadAll()
  } catch (error) { notify(error?.message || 'Không thể lưu mức lương.', 'error') }
}
function moveWeek(amount) {
  const d = new Date(`${weekStart.value}T12:00:00`); d.setDate(d.getDate() + amount * 7); weekStart.value = localIso(d)
}
watch(weekStart, () => loadAll())
onMounted(loadAll)
</script>

<template>
  <section class="staff-management">
    <header class="panel-head">
      <div><p class="eyebrow">FREEDOMS · NHÂN SỰ</p><h2>Quản lý nhân viên</h2><p class="muted">Xếp ca, theo dõi chấm công và điều chỉnh mức lương hàng tuần.</p></div>
      <div class="week-control"><button class="small-btn" @click="moveWeek(-1)" aria-label="Tuần trước">←</button><span>{{ dateLabel(weekStart) }} – {{ dateLabel(weekDates[6].date) }}</span><button class="small-btn" @click="moveWeek(1)" aria-label="Tuần sau">→</button></div>
    </header>
    <nav class="staff-tabs">
      <button :class="{active: tab==='schedule'}" @click="tab='schedule'">Lịch phân ca</button>
      <button :class="{active: tab==='salary'}" @click="tab='salary'">Mức lương</button>
      <button :class="{active: tab==='payroll'}" @click="tab='payroll'">Bảng lương tuần</button>
    </nav>

    <div v-if="loading" class="empty-state">Đang đồng bộ dữ liệu…</div>
    <template v-else>
      <section v-if="tab==='schedule'" class="panel-card">
        <div class="section-title"><div><h3>Phân ca theo tuần</h3><p>Chọn nhân viên tại ô ngày/ca. Mỗi ca chỉ có một nhân viên được phân công.</p></div><span>{{ assignments.length }} ca đã xếp</span></div>
        <div class="table-wrap">
          <table class="schedule-table">
            <thead><tr><th>Ca / giờ</th><th v-for="day in weekDates" :key="day.date">{{ day.label }}</th></tr></thead>
            <tbody>
              <tr v-for="shift in shiftDefinitions" :key="shift.no">
                <th><strong>Ca {{ shift.no }}</strong><small>{{ shift.time }}</small></th>
                <td v-for="day in weekDates" :key="day.date">
                  <select :value="shiftEmployee(day.date, shift.no)" @change="saveSlot(day.date, shift.no, $event.target.value)" :aria-label="`Phân công ca ${shift.no} ngày ${day.date}`">
                    <option value="">— Chưa xếp —</option>
                    <option v-for="employee in employees.filter(e => e.active !== false && e.active !== 0)" :key="employee.employeeId || employee.id" :value="String(employee.employeeId || employee.id)">{{ employee.fullName || employee.name }}</option>
                  </select>
                  <small :class="slot(day.date, shift.no)?.isPresent ? 'attended' : 'slot-note'">{{ shiftState(day.date, shift.no) }}</small>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="tab==='salary'" class="panel-card">
        <div class="section-title"><div><h3>Mức lương nhân viên</h3><p>Ví dụ: 800.000đ / 8 ca chuẩn = khấu trừ 100.000đ cho mỗi ca vắng đã hết giờ.</p></div></div>
        <div class="table-wrap"><table><thead><tr><th>Nhân viên</th><th>Email</th><th>Lương tuần (đ)</th><th>Ca chuẩn/tuần</th><th></th></tr></thead><tbody>
          <tr v-for="employee in employees" :key="employee.employeeId || employee.id">
            <td><strong>{{ employee.fullName || employee.name }}</strong><small>{{ employee.active === false || employee.active === 0 ? 'Đã khóa tài khoản' : 'Đang hoạt động' }}</small></td>
            <td>{{ employee.email }}</td>
            <td><input v-model.number="salaryDrafts[employee.employeeId || employee.id].weeklyBaseSalary" type="number" min="0" max="1000000000" step="10000" /></td>
            <td><input v-model.number="salaryDrafts[employee.employeeId || employee.id].expectedShiftsPerWeek" type="number" min="1" max="49" /></td>
            <td><button class="primary-btn" @click="saveSalary(employee)">Lưu lương</button></td>
          </tr>
          <tr v-if="!employees.length"><td colspan="5" class="empty-state">Chưa có tài khoản nhân viên. Tạo nhân viên tại Tài khoản & phân quyền trước.</td></tr>
        </tbody></table></div>
      </section>

      <section v-else class="panel-card">
        <div class="section-title"><div><h3>Bảng lương dự kiến</h3><p>Ca vắng chỉ bị khấu trừ sau khi hết giờ ca. Tuần hiện tại là số tạm tính.</p></div><span>{{ payroll.some(p => p.isFinalized) ? 'Đã qua tuần' : 'Tạm tính' }}</span></div>
        <div class="table-wrap"><table><thead><tr><th>Nhân viên</th><th>Lương chuẩn</th><th>Ca có mặt</th><th>Ca vắng</th><th>Khấu trừ</th><th>Thực nhận dự kiến</th></tr></thead><tbody>
          <tr v-for="row in payroll" :key="row.employeeUserId"><td><strong>{{ row.employeeName }}</strong></td><td>{{ money(row.weeklyBaseSalary) }}</td><td>{{ row.attendedShifts }}/{{ row.expectedShiftsPerWeek }}</td><td>{{ row.missedShifts }}</td><td class="deduction">−{{ money(row.deduction) }}</td><td><strong>{{ money(row.projectedPay) }}</strong></td></tr>
          <tr v-if="!payroll.length"><td colspan="6" class="empty-state">Chưa có bảng lương cho tuần này.</td></tr>
        </tbody></table></div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.staff-management{color:#172338}.panel-head{display:flex;justify-content:space-between;align-items:flex-end;gap:16px;margin-bottom:18px}.eyebrow{font-size:10px;letter-spacing:.16em;font-weight:800;color:#a56a2a;margin:0 0 5px}.panel-head h2{font-size:24px;font-weight:800;margin:0 0 4px}.muted,.section-title p{font-size:12px;color:#758093;margin:0}.week-control{display:flex;align-items:center;gap:10px;background:#fff;border:1px solid #e2e6eb;padding:6px 8px;border-radius:10px;font-size:12px;white-space:nowrap}.small-btn{border:1px solid #e0e4e9;background:#fff;border-radius:7px;width:30px;height:30px}.staff-tabs{display:flex;gap:6px;margin-bottom:14px;border-bottom:1px solid #e0e4e9}.staff-tabs button{border:0;background:transparent;padding:12px 14px;font-size:12px;font-weight:700;color:#687487;border-bottom:2px solid transparent}.staff-tabs button.active{color:#172338;border-color:#b47a38}.panel-card{background:#fff;border:1px solid #e7e9ed;border-radius:12px;overflow:hidden}.section-title{padding:18px 20px;display:flex;justify-content:space-between;align-items:center;gap:14px;border-bottom:1px solid #edf0f3}.section-title h3{font-size:16px;font-weight:800;margin:0 0 5px}.section-title>span{font-size:11px;border-radius:20px;background:#f2f4f7;padding:7px 10px;white-space:nowrap}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse;font-size:12px}th,td{padding:12px 13px;text-align:left;border-bottom:1px solid #eef0f3;vertical-align:middle;white-space:nowrap}thead th{font-size:10px;text-transform:uppercase;letter-spacing:.05em;color:#737e8f;background:#fafbfc}tbody tr:last-child td,tbody tr:last-child th{border-bottom:0}.schedule-table{min-width:1000px}.schedule-table tbody th{min-width:120px;background:#fcfcfd}.schedule-table th strong,.schedule-table th small,td small{display:block}.schedule-table th small,td small{font-size:10px;color:#798395;margin-top:4px}.schedule-table select{width:100%;min-width:115px;border:1px solid #dfe4eb;border-radius:7px;padding:8px;background:#fff;color:#263449;font-size:11px}.attended{color:#137547!important;font-weight:700}.primary-btn{border:0;background:#172a43;color:#fff;border-radius:7px;padding:9px 12px;font-weight:700;font-size:11px;white-space:nowrap}input[type=number]{width:130px;padding:8px;border:1px solid #dfe4eb;border-radius:7px}.deduction{color:#b42318}.empty-state{text-align:center;padding:34px;color:#7e8794;font-size:12px}tbody td:first-child strong{display:block}tbody td:first-child small{display:block}@media(max-width:700px){.panel-head{align-items:flex-start;flex-direction:column}.week-control{max-width:100%}.section-title{align-items:flex-start;flex-direction:column}.staff-tabs button{padding:11px 8px;font-size:11px}}
</style>
