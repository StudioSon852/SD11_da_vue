<script setup lang="ts">
import { ref } from "vue";
import { useRouter, RouterLink } from "vue-router";
import api from "../services/api";

const router = useRouter();
const full_name = ref("");
const email = ref("");
const password = ref("");
const confirmPassword = ref("");
const phone = ref("");
const address = ref("");
const birth_date = ref("");
const errorMessage = ref("");
const successMessage = ref("");
const loading = ref(false);

async function register() {
  errorMessage.value = ""; successMessage.value = "";
  if (full_name.value.trim().length < 2) { errorMessage.value = "Vui lòng nhập họ tên từ 2 ký tự trở lên."; return; }
  if (password.value.length < 8) { errorMessage.value = "Mật khẩu cần tối thiểu 8 ký tự."; return; }
  if (password.value !== confirmPassword.value) { errorMessage.value = "Mật khẩu xác nhận không khớp."; return; }
  if (phone.value.trim() && !/^0[0-9]{9}$/.test(phone.value.trim())) { errorMessage.value = "Số điện thoại cần gồm 10 chữ số và bắt đầu bằng 0."; return; }
  if (birth_date.value && birth_date.value > new Date().toISOString().slice(0, 10)) { errorMessage.value = "Ngày sinh không thể ở tương lai."; return; }
  loading.value = true;
  try {
    const data = await api.post("/auth/register", { full_name: full_name.value.trim(), email: email.value.trim().toLowerCase(), password: password.value, phone: phone.value.trim(), address: address.value.trim(), birth_date: birth_date.value || null });
    successMessage.value = data?.message || "Đăng ký thành công. Vui lòng đăng nhập.";
    window.setTimeout(() => router.push("/login"), 700);
  } catch (error: unknown) {
    const e = error as { message?: string; data?: { message?: string } };
    const raw = e.data?.message || e.message || "";
    errorMessage.value = /failed to fetch|network|load failed/i.test(raw)
      ? "Không kết nối được API. Mở http://localhost:8080/api/health để xem chẩn đoán SQL Server."
      : (raw || "Đăng ký thất bại. Kiểm tra API /auth/register.");
  } finally { loading.value = false; }
}
</script>

<template>
  <section class="auth-page register-page"><div class="auth-aside"><span class="eyebrow">JOIN THE COMMUNITY</span><h1>Phong cách của bạn.<br /><em>Lựa chọn của bạn.</em></h1><p>Tạo tài khoản để quản lý thông tin và thuận tiện hơn trong những lần mua sắm tiếp theo.</p><div class="auth-shoe">FIND YOUR<br /><em>OWN PLACE.</em></div></div>
    <div class="auth-panel"><div class="auth-card register-card"><RouterLink to="/" class="back-link">← Về trang chủ</RouterLink><span class="eyebrow">BẮT ĐẦU NGAY</span><h2>Tạo tài khoản</h2><p class="auth-subtitle">Điền thông tin của bạn bên dưới.</p>
      <form @submit.prevent="register"><label for="full-name">Họ và tên</label><input id="full-name" v-model.trim="full_name" type="text" autocomplete="name" maxlength="100" placeholder="Nguyễn Văn A" required />
        <div class="two-fields"><div><label for="signup-email">Email</label><input id="signup-email" v-model.trim="email" type="email" autocomplete="email" maxlength="100" placeholder="example@gmail.com" required /></div><div><label for="signup-phone">Số điện thoại</label><input id="signup-phone" v-model.trim="phone" type="tel" autocomplete="tel" inputmode="numeric" maxlength="10" placeholder="09xxxxxxxx" /></div></div>
        <label for="birth-date">Ngày tháng năm sinh</label><input id="birth-date" v-model="birth_date" type="date" :max="new Date().toISOString().slice(0, 10)" />
        <label for="signup-address">Địa chỉ</label><textarea id="signup-address" v-model.trim="address" autocomplete="street-address" placeholder="Số nhà, đường, quận/huyện, tỉnh/thành phố" rows="2"></textarea>
        <div class="two-fields"><div><label for="signup-password">Mật khẩu</label><input id="signup-password" v-model="password" type="password" autocomplete="new-password" minlength="8" placeholder="Tối thiểu 8 ký tự" required /></div><div><label for="confirm-password">Xác nhận mật khẩu</label><input id="confirm-password" v-model="confirmPassword" type="password" autocomplete="new-password" placeholder="Nhập lại mật khẩu" required /></div></div>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p><p v-if="successMessage" class="form-success" role="status">{{ successMessage }}</p><button class="primary-button full-button" type="submit" :disabled="loading">{{ loading ? "ĐANG TẠO TÀI KHOẢN..." : "TẠO TÀI KHOẢN →" }}</button>
      </form><p class="auth-bottom">Đã có tài khoản? <RouterLink to="/login">Đăng nhập</RouterLink></p>
    </div></div>
  </section>
</template>

<style scoped>
.auth-page{box-sizing:border-box;width:100%;min-height:100vh;display:flex;align-items:center;justify-content:center;background:linear-gradient(to bottom,#2c3e50,#3498db,#eaf4ff,#fff);padding:24px}.auth-aside{display:none}.auth-panel{box-sizing:border-box;width:100%;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px;background:transparent}.auth-card{width:100%;max-width:540px;padding:28px 34px;background:#ffffffed;border:1px solid #ffffffb8;border-radius:14px;box-shadow:0 22px 65px #122c4630;backdrop-filter:blur(12px)}.back-link{display:block;text-decoration:none;color:#516578;font-size:12px;margin-bottom:18px}.eyebrow{display:block;color:#4d7290;font-size:10px;font-weight:800;letter-spacing:.16em;margin-bottom:8px}.auth-card h2{font-size:28px;margin:8px 0;color:#172b3b}.auth-subtitle{font-size:12px;color:#718092;margin-bottom:16px}.auth-card form>label,.two-fields label{display:block;font-size:11px;color:#25394b;font-weight:700;margin:12px 0 6px}.auth-card input,.auth-card textarea{box-sizing:border-box;width:100%;min-width:0;border:1px solid #d6e0e8;border-radius:7px;padding:11px 12px;background:#fff;color:#172b3b;outline:none;font-size:12px}.auth-card input:focus,.auth-card textarea:focus{border-color:#2e7fb9;box-shadow:0 0 0 3px #3498db20}.two-fields{display:grid;grid-template-columns:1fr 1fr;gap:12px}.two-fields>div{min-width:0}.full-button{width:100%;margin-top:14px;border:0;border-radius:7px;background:#172b3b;color:#fff;padding:13px;font-size:11px;font-weight:800;letter-spacing:.07em;cursor:pointer}.full-button:hover{background:#244b69}.full-button:disabled{opacity:.65;cursor:wait}.form-error,.form-success{font-size:12px;border-radius:5px;padding:9px;margin:12px 0 0}.form-error{color:#b42318;background:#fff1f0}.form-success{color:#166534;background:#f0fdf4}.auth-bottom{text-align:center;font-size:12px;color:#66788a;margin-top:18px}.auth-bottom a{color:#17699f;font-weight:800;text-decoration:none}@media(max-width:560px){.auth-page,.auth-panel{padding:12px;min-height:100vh}.auth-card{padding:24px 18px}.two-fields{grid-template-columns:1fr;gap:0}}
</style>
