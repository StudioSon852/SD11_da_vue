<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { currentUser, logout } from '@/stores/authStore'
import { api } from '@/services/apiClient'
import PosPage from './admin/pages/PosPage.vue'
import PaymentsPage from './admin/pages/PaymentsPage.vue'
import CustomersPage from './admin/pages/CustomersPage.vue'
import DashboardPage from './admin/pages/DashboardPage.vue'
import { fetchAllData, paymentChannel, paymentSearch, toasts, toastIcon, dismissToast } from './admin/adminStore'

const router = useRouter()
const activeTab = ref('overview')
const managementSearch = ref('')
const employeeTabs = [
  { key: 'overview', number: '01', label: 'Tổng quan', terms: 'thống kê vận hành đơn hàng sản phẩm' },
  { key: 'pos', number: '02', label: 'Bán hàng tại quầy', terms: 'pos tạo hóa đơn tiền mặt chuyển khoản qr' },
  { key: 'orders', number: '03', label: 'Đơn hàng & thanh toán', terms: 'đơn online offline thanh toán xác nhận' },
  { key: 'customers', number: '04', label: 'Khách hàng', terms: 'crm hạng thành viên lịch sử mua hàng' },
  { key: 'shifts', number: '05', label: 'Ca làm', terms: 'lịch ca chấm công có mặt' },
  { key: 'payroll', number: '06', label: 'Mức lương của tôi', terms: 'lương tuần khấu trừ ca vắng' },
  { key: 'profile', number: '07', label: 'Hồ sơ', terms: 'thông tin cá nhân số điện thoại địa chỉ' },
]
const normalizeMenuText = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').toLowerCase().trim()
const visibleEmployeeTabs = computed(() => {
  const q = normalizeMenuText(managementSearch.value)
  if (!q) return employeeTabs
  return employeeTabs.filter(tab => normalizeMenuText(`${tab.label} ${tab.terms}`).includes(q))
})
function openFirstMenuMatch() {
  if (visibleEmployeeTabs.value.length) activeTab.value = visibleEmployeeTabs.value[0].key
}

const employeeName = computed(() => profile.value.fullName || currentUser.value?.full_name || currentUser.value?.fullName || currentUser.value?.name || 'Nhân viên')
const profile = ref({ fullName: '', email: '', phone: '', address: '' })
const profileSaving = ref(false)
const message = ref('')
const messageError = ref(false)
const shifts = ref([])
const payroll = ref(null)
const dataLoading = ref(false)
let refreshTimer = null

const shiftTimes = {
  1: '06:40–08:40', 2: '08:45–10:45', 3: '10:50–12:50', 4: '13:00–15:00',
  5: '15:05–17:05', 6: '17:10–19:10', 7: '19:15–21:15',
}
function localIso(d) { return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
function mondayOf(d) { const x = new Date(d.getFullYear(),d.getMonth(),d.getDate()); const wd=x.getDay()||7; x.setDate(x.getDate()-wd+1); return x }
function vietnamClock() {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date())
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]))
  return { date: `${values.year}-${values.month}-${values.day}`, minutes: Number(values.hour) * 60 + Number(values.minute) }
}
function attendanceWindowStatus(shift) {
  const date = String(shift.workDate || '').slice(0, 10)
  const now = vietnamClock()
  if (!date || date > now.date) return 'upcoming'
  if (date < now.date) return 'expired'
  const times = shiftTimes[Number(shift.shiftNo)]
  if (!times) return 'expired'
  const [start, end] = times.split('–').map(value => { const [hour, minute] = value.split(':').map(Number); return hour * 60 + minute })
  if (now.minutes < start) return 'upcoming'
  if (now.minutes >= end) return 'expired'
  return 'open'
}
const weekStart = ref(localIso(mondayOf(new Date())))
const money = value => new Intl.NumberFormat('vi-VN', { style:'currency', currency:'VND', maximumFractionDigits:0 }).format(Number(value)||0)
const dateLabel = value => value ? new Date(`${String(value).slice(0,10)}T12:00:00`).toLocaleDateString('vi-VN',{weekday:'short',day:'2-digit',month:'2-digit',year:'numeric'}) : '—'
function tell(text, isError=false) { message.value=text; messageError.value=isError; window.setTimeout(() => { if (message.value===text) message.value='' }, 5000) }
function onShowPayments(event) {
  paymentChannel.value = 'Offline'
  paymentSearch.value = event?.detail?.orderId == null ? '' : String(event.detail.orderId)
  activeTab.value = 'orders'
}
async function loadEmployeeData(includeProfile = true) {
  dataLoading.value = true
  try {
    const [me, weeklyShifts, pay] = await Promise.all([
      api.get('/employee/profile'),
      api.get(`/employee/shifts?weekStart=${weekStart.value}`),
      api.get(`/employee/payroll?weekStart=${weekStart.value}`),
    ])
    if (includeProfile) profile.value = { fullName: me.fullName || '', email: me.email || '', phone: me.phone || '', address: me.address || '' }
    shifts.value = Array.isArray(weeklyShifts) ? weeklyShifts : []
    payroll.value = pay || null
  } catch (error) {
    tell(error?.message || 'Không tải được dữ liệu nhân viên. Kiểm tra migration ca làm/lương.', true)
  } finally { dataLoading.value = false }
}
async function markAttendance(shift) {
  try {
    const result = await api.post(`/employee/shifts/${shift.assignmentId}/attendance`, {})
    tell(result?.message || 'Đã ghi nhận chấm công.', false)
    await loadEmployeeData()
  } catch (error) { tell(error?.message || 'Không thể chấm công ca này.', true) }
}
async function saveProfile() {
  profileSaving.value=true
  try {
    const updated = await api.put('/employee/profile', { fullName: profile.value.fullName, phone: profile.value.phone, address: profile.value.address })
    profile.value = { ...profile.value, ...updated }
    tell('Đã cập nhật hồ sơ.', false)
  } catch (error) { tell(error?.message || 'Không thể cập nhật hồ sơ.', true) }
  finally { profileSaving.value=false }
}
onMounted(async () => {
  window.addEventListener('freedoms:show-payments', onShowPayments)
  await Promise.all([fetchAllData(), loadEmployeeData()])
  refreshTimer = window.setInterval(() => { fetchAllData(true); loadEmployeeData(false) }, 5000)
})
onUnmounted(() => {
  window.removeEventListener('freedoms:show-payments', onShowPayments)
  if (refreshTimer) window.clearInterval(refreshTimer)
})
function signOut() { if (typeof logout === 'function') logout(); router.replace('/login') }
</script>

<template>
  <div class="admin-shell employee-shell">
    <header class="employee-header">
      <RouterLink to="/employee" class="employee-brand" aria-label="FREEDOMS cổng nhân viên">
        <img src="/img/logogiay.png" alt="Logo FREEDOMS" class="employee-brand-logo" />
        <span class="employee-brand-words"><strong>FREEDOMS</strong><small>STEP INTO YOUR STYLE · CỔNG NHÂN VIÊN</small></span>
      </RouterLink>
      <form class="employee-management-search" role="search" @submit.prevent="openFirstMenuMatch">
        <i class="icon icon-search" aria-hidden="true"></i>
        <input v-model="managementSearch" type="search" aria-label="Tìm mục quản lý" placeholder="Tìm mục quản lý… (vd: ca làm, đơn hàng, khách hàng)" />
        <button v-if="managementSearch" type="button" class="employee-search-clear" aria-label="Xóa tìm kiếm" @click="managementSearch = ''">×</button>
        <button type="submit" class="employee-search-submit">Đi tới</button>
      </form>
      <div class="employee-user"><span>Xin chào, <strong>{{ employeeName }}</strong></span><button type="button" @click="signOut">Đăng xuất</button></div>
    </header>
    <nav class="employee-nav" aria-label="Chức năng nhân viên">
      <button v-for="tab in visibleEmployeeTabs" :key="tab.key" type="button" :class="{active: activeTab === tab.key}" @click="activeTab = tab.key"><span>{{ tab.number }}</span> {{ tab.label }}</button>
      <p v-if="visibleEmployeeTabs.length === 0" class="employee-no-menu">Không tìm thấy mục quản lý phù hợp. Thử “đơn hàng”, “ca làm” hoặc “lương”.</p>
    </nav>
    <main class="employee-content">
      <div class="employee-heading"><div><p>FREEDOMS · VẬN HÀNH</p><h1>{{ ({overview:'Tổng quan',pos:'Bán hàng tại quầy',orders:'Đơn hàng và thanh toán',customers:'Khách hàng',shifts:'Ca làm của tôi',payroll:'Mức lương của tôi',profile:'Hồ sơ nhân viên'})[activeTab] }}</h1><small>{{ ({overview:'Theo dõi đơn hàng, sản phẩm và hiệu quả vận hành. Không hiển thị phân bổ doanh thu theo phương thức thanh toán.',pos:'Tạo hóa đơn, nhận tiền mặt hoặc tạo yêu cầu chuyển khoản QR.',orders:'Đơn hàng online và tại quầy được đồng bộ với trang Admin.',customers:'Tra cứu thông tin và lịch sử mua hàng của khách.',shifts:'Chỉ ca được Admin xếp mới xuất hiện. Chấm công chỉ được thực hiện trong khung giờ ca.',payroll:'Lương tuần dự kiến, số ca có mặt, ca vắng và khoản khấu trừ.',profile:'Cập nhật thông tin liên hệ của tài khoản nhân viên.'})[activeTab] }}</small></div></div>
      <div v-if="message" class="employee-message" :class="{error: messageError}" role="status">{{ message }}</div>
      <DashboardPage v-if="activeTab === 'overview'" />
      <PosPage v-else-if="activeTab === 'pos'" />
      <PaymentsPage v-else-if="activeTab === 'orders'" />
      <CustomersPage v-else-if="activeTab === 'customers'" />

      <section v-else-if="activeTab === 'shifts'" class="employee-card">
        <div class="section-head"><div><h2>Lịch ca tuần này</h2><p>Tuần bắt đầu từ {{ dateLabel(weekStart) }}. Ca quá giờ sẽ tự khóa nút chấm công.</p></div><button class="outline-button" @click="loadEmployeeData" :disabled="dataLoading">Đồng bộ dữ liệu</button></div>
        <div v-if="dataLoading" class="empty">Đang tải ca làm…</div>
        <div v-else-if="!shifts.length" class="empty">Chưa được Admin xếp ca trong tuần này.</div>
        <div v-else class="shift-list">
          <article v-for="shift in shifts" :key="shift.assignmentId" class="shift-row">
            <div class="shift-num">CA {{ shift.shiftNo }}</div><div class="shift-info"><strong>{{ dateLabel(shift.workDate) }}</strong><span>{{ shiftTimes[shift.shiftNo] }}</span></div>
            <div class="shift-status" :class="{'checked': shift.isPresent}">{{ shift.isPresent ? 'Có mặt' : 'Chưa chấm công' }}<small v-if="shift.checkedInAt">{{ new Date(shift.checkedInAt).toLocaleTimeString('vi-VN',{hour:'2-digit',minute:'2-digit'}) }}</small></div>
            <button v-if="!shift.isPresent && attendanceWindowStatus(shift) === 'open'" class="primary-button" @click="markAttendance(shift)">Tôi có mặt</button>
            <span v-else-if="shift.isPresent" class="locked-chip">Đã ghi nhận</span>
            <span v-else class="locked-chip muted-chip">{{ attendanceWindowStatus(shift) === 'expired' ? 'Đã hết giờ chấm công' : 'Chưa đến giờ' }}</span>
          </article>
        </div>
      </section>

      <section v-else-if="activeTab === 'payroll'" class="employee-card">
        <div class="section-head"><div><h2>Lương tuần của tôi</h2><p>{{ payroll?.isFinalized ? 'Tuần đã kết thúc' : 'Số liệu tạm tính, ca tương lai chưa bị tính vắng' }} · {{ payroll?.weekStart || weekStart }} – {{ payroll?.weekEnd || '' }}</p></div><button class="outline-button" @click="loadEmployeeData" :disabled="dataLoading">Làm mới</button></div>
        <div v-if="payroll" class="payroll-grid">
          <div class="payroll-stat"><span>Lương cơ bản tuần</span><strong>{{ money(payroll.weeklyBaseSalary) }}</strong></div>
          <div class="payroll-stat"><span>Ca có mặt</span><strong>{{ payroll.attendedShifts }} ca</strong></div>
          <div class="payroll-stat"><span>Ca vắng đã hết giờ</span><strong>{{ payroll.missedShifts }} ca</strong></div>
          <div class="payroll-stat"><span>Khấu trừ</span><strong class="deduction">−{{ money(payroll.deduction) }}</strong></div>
          <div class="payroll-total"><span>Thực nhận dự kiến</span><strong>{{ money(payroll.projectedPay) }}</strong></div>
        </div>
        <p class="payroll-note">Công thức: lương cơ bản − (lương cơ bản ÷ {{ payroll?.expectedShiftsPerWeek || 8 }} ca chuẩn × số ca vắng đã hết giờ). Ca chưa đến thời gian kết thúc được để ở trạng thái chờ, chưa trừ lương.</p>
      </section>

      <section v-else-if="activeTab === 'profile'" class="employee-card profile-card">
        <div class="section-head"><div><h2>Thông tin cá nhân</h2><p>Email đăng nhập không thể thay đổi tại đây.</p></div></div>
        <form class="profile-form" @submit.prevent="saveProfile">
          <label>Họ và tên<input v-model.trim="profile.fullName" required minlength="2" maxlength="100" /></label>
          <label>Email<input :value="profile.email" disabled /></label>
          <label>Số điện thoại<input v-model.trim="profile.phone" inputmode="numeric" placeholder="0912345678" /></label>
          <label class="wide">Địa chỉ<input v-model.trim="profile.address" maxlength="255" placeholder="Địa chỉ liên hệ" /></label>
          <div class="wide"><button class="primary-button" type="submit" :disabled="profileSaving">{{ profileSaving ? 'ĐANG LƯU…' : 'Lưu hồ sơ' }}</button></div>
        </form>
      </section>
    </main>
    <Teleport to="body">
      <div class="toast-container employee-toast-container" aria-live="polite" aria-atomic="false">
        <div v-for="t in toasts" :key="t.id" class="app-toast fade-in-scale" :class="'toast-' + t.type">
          <i class="icon mr-2 text-base" :class="toastIcon(t.type)"></i>
          <span class="grow" v-text="t.message"></span>
          <button type="button" class="admin-toast-close" @click="dismissToast(t.id)" aria-label="Đóng thông báo"><i class="icon icon-x-lg" style="font-size:.8rem"></i></button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style src="./admin/admin-theme.css"></style>
<style src="./admin/admin-pages-theme.css"></style>
<style src="./admin/admin-icons.css"></style>
<style>
/* POS, order/payment forms and employee forms use the same rounded controls as Admin. */
.employee-shell .employee-content :is(input:not([type="checkbox"]):not([type="radio"]):not([type="hidden"]), select, textarea) {
  border: 1px solid #dfe3e9;
  border-radius: 10px;
  background-color: #fff;
  transition: border-color .16s ease, box-shadow .16s ease;
}
.employee-shell .employee-content :is(input:not([type="checkbox"]):not([type="radio"]):not([type="hidden"]), select, textarea):focus {
  outline: none;
  border-color: #152740;
  box-shadow: 0 0 0 3px rgb(21 39 64 / 8%);
}
</style>
<style scoped>
.employee-shell{min-height:100vh;background:#f6f7f9;color:#182033;font-family:Inter,Arial,sans-serif}.employee-header{min-height:82px;background:#fff;border-bottom:1px solid #e6e8ec;padding:12px clamp(16px,4vw,56px);display:flex;align-items:center;gap:22px}.employee-brand,.employee-user{display:flex;align-items:center;gap:10px}.employee-brand{color:#111;text-decoration:none;flex:0 0 auto}.employee-brand-logo{width:46px;height:46px;object-fit:contain;display:block}.employee-brand-words strong{display:block;letter-spacing:1px;font-size:16px;font-weight:900}.employee-brand-words small{display:block;color:#777f8e;font-size:8px;letter-spacing:1px;margin-top:3px}.employee-management-search{display:flex;align-items:center;gap:9px;flex:1;min-width:160px;max-width:640px;margin:0 auto;border:1px solid #dfe3e9;border-radius:8px;padding:0 8px 0 12px;height:42px;background:#fff;color:#8a92a0}.employee-management-search input{flex:1;min-width:0;border:0;outline:0;color:#182033;background:transparent;font-size:12px}.employee-search-clear{border:0;background:transparent;font-size:20px;color:#8a92a0}.employee-search-submit{border:0;border-radius:6px;padding:7px 11px;background:#152740;color:#fff;font-size:11px;font-weight:800}.employee-user{font-size:12px;color:#60697a;flex:0 0 auto}.employee-user button,.outline-button{border:1px solid #d7dbe2;background:#fff;border-radius:8px;padding:8px 12px;font-weight:700;color:#333;font-size:11px}.employee-nav{display:flex;gap:5px;flex-wrap:wrap;background:#fff;border-bottom:1px solid #e6e8ec;padding:10px clamp(16px,4vw,56px)}.employee-nav button{border:1px solid transparent;background:transparent;border-radius:9px;padding:10px 12px;color:#626b7b;font-weight:700;font-size:11px}.employee-nav button span{font-size:10px;color:#9a6a30;margin-right:6px}.employee-nav button.active{background:#14233a;border-color:#14233a;color:white}.employee-no-menu{align-self:center;color:#8a4a10;font-size:11px;margin:5px 0;padding:8px}.employee-content{padding:24px clamp(16px,4vw,56px) 48px;max-width:1600px;margin:0 auto}.employee-toast-container{z-index:5000}.employee-heading{display:flex;justify-content:space-between;align-items:end;margin-bottom:22px}.employee-heading p{margin:0 0 5px;font-size:10px;letter-spacing:1.5px;color:#a56625;font-weight:800}.employee-heading h1{font-size:24px;margin:0 0 6px;font-weight:800}.employee-heading small{font-size:12px;color:#737b89}.employee-card{background:#fff;border:1px solid #e5e8ee;border-radius:12px;padding:20px}.section-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:18px}.section-head h2{font-size:17px;font-weight:800;margin:0 0 5px}.section-head p{font-size:11px;color:#7a8392;margin:0}.employee-message{padding:11px 14px;margin-bottom:14px;background:#eef8f0;border:1px solid #cdebd4;border-radius:9px;font-size:12px;color:#226438}.employee-message.error{background:#fff1f0;border-color:#ffd2cc;color:#b42318}.empty{padding:28px;text-align:center;color:#7e8794;font-size:12px}.shift-list{display:grid;gap:8px}.shift-row{display:flex;align-items:center;gap:14px;border:1px solid #e8ebf0;border-radius:10px;padding:12px}.shift-num{font-size:11px;font-weight:800;padding:10px;border-radius:8px;background:#f3f4f7;min-width:52px;text-align:center}.shift-info{flex:1}.shift-info strong,.shift-info span{display:block}.shift-info strong{font-size:12px}.shift-info span{font-size:11px;color:#737d8c;margin-top:4px}.shift-status{font-size:11px;color:#9a6a30;font-weight:700}.shift-status small{display:block;font-weight:500;margin-top:4px}.shift-status.checked{color:#17824c}.primary-button{border:0;background:#152740;color:#fff;border-radius:8px;padding:10px 14px;font-weight:800;font-size:11px;cursor:pointer}.primary-button:disabled,.outline-button:disabled{opacity:.55;cursor:not-allowed}.locked-chip{font-size:10px;color:#157347;background:#e6f6ec;border-radius:20px;padding:7px 10px}.locked-chip.muted-chip{color:#7b8492;background:#f0f2f5}.payroll-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.payroll-stat{background:#f7f8fa;border:1px solid #e8ebf0;border-radius:10px;padding:15px}.payroll-stat span,.payroll-total span{display:block;color:#758093;font-size:11px}.payroll-stat strong{display:block;font-size:18px;margin-top:8px}.payroll-stat .deduction{color:#bb2633}.payroll-total{grid-column:1/-1;border-radius:10px;background:#14233a;color:#fff;padding:17px;display:flex;align-items:center;justify-content:space-between}.payroll-total span{color:#ccd3df}.payroll-total strong{font-size:24px}.payroll-note{color:#737d8c;font-size:11px;line-height:1.6;margin:15px 0 0}.profile-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:15px}.profile-form label{display:grid;gap:7px;font-size:11px;font-weight:800;color:#384358}.profile-form input{width:100%;box-sizing:border-box;border:1px solid #dfe4eb;border-radius:8px;padding:11px 12px;font-size:12px;font-weight:400}.profile-form input:disabled{background:#f1f3f6;color:#8892a0}.profile-form .wide{grid-column:1/-1}@media(max-width:1100px){.employee-header{flex-wrap:wrap}.employee-management-search{order:3;flex-basis:100%;max-width:none}.employee-user{margin-left:auto}}@media(max-width:860px){.employee-nav{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));padding:8px}.employee-nav button{padding:10px 5px}.payroll-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.employee-header{align-items:flex-start;padding:12px}.employee-user{flex-direction:column;align-items:flex-end;gap:6px}.employee-brand-words small{max-width:160px;line-height:1.4}.employee-content{padding:18px 10px}.section-head{align-items:flex-start;flex-direction:column}.shift-row{gap:8px;flex-wrap:wrap}.shift-info{min-width:150px}.profile-form{grid-template-columns:1fr}.profile-form .wide{grid-column:auto}.payroll-total strong{font-size:19px}.employee-nav{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
