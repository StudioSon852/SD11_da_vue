<script setup lang="ts">
import { ref } from "vue";
import axios from "axios";
import { useRouter } from "vue-router";
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
  errorMessage.value = "";
  successMessage.value = "";
  if (password.value.length < 8) { errorMessage.value = "Mật khẩu cần tối thiểu 8 ký tự."; return; }
  if (password.value !== confirmPassword.value) { errorMessage.value = "Mật khẩu xác nhận không khớp."; return; }
  if (birth_date.value && birth_date.value > new Date().toISOString().slice(0, 10)) { errorMessage.value = "Ngày sinh không thể ở tương lai."; return; }
  loading.value = true;
  try {
    // Không gửi role từ trình duyệt. Backend cần tự gán role customer.
    const { data } = await api.post("/auth/register", {
      full_name: full_name.value.trim(),
      email: email.value.trim(),
      password: password.value,
      phone: phone.value.trim(),
      address: address.value.trim(),
      birth_date: birth_date.value || null
    });
    successMessage.value = data.message || "Đăng ký thành công. Vui lòng đăng nhập.";
    window.setTimeout(() => router.push("/login"), 700);
  } catch (error: unknown) {
    if (axios.isAxiosError(error)) errorMessage.value = error.response?.data?.message || "Đăng ký thất bại. Kiểm tra API /auth/register.";
    else errorMessage.value = "Đăng ký thất bại.";
  } finally { loading.value = false; }
}
</script>

<template>
  <section class="auth-page register-page">
    <div class="auth-aside"><span class="eyebrow">JOIN THE COMMUNITY</span><h1>Phong cách của bạn.<br /><em>Lựa chọn của bạn.</em></h1><p>Tạo tài khoản để quản lý thông tin và thuận tiện hơn trong những lần mua sắm tiếp theo.</p><div class="auth-shoe">FIND YOUR<br /><em>OWN PLACE.</em></div></div>
    <div class="auth-panel"><div class="auth-card register-card"><RouterLink to="/" class="back-link">← Về trang chủ</RouterLink><span class="eyebrow">BẮT ĐẦU NGAY</span><h2>Tạo tài khoản</h2><p class="auth-subtitle">Điền thông tin của bạn bên dưới.</p>
      <form @submit.prevent="register">
        <label>Họ và tên</label><input v-model.trim="full_name" type="text" autocomplete="name" placeholder="Nguyễn Văn A" required />
        <div class="two-fields"><div><label>Email</label><input v-model.trim="email" type="email" autocomplete="email" placeholder="example@gmail.com" required /></div><div><label>Số điện thoại</label><input v-model.trim="phone" type="tel" autocomplete="tel" placeholder="09xxxxxxxx" required /></div></div>
        <label>Ngày tháng năm sinh</label><input v-model="birth_date" type="date" :max="new Date().toISOString().slice(0, 10)" />
        <label>Địa chỉ</label><textarea v-model.trim="address" autocomplete="street-address" placeholder="Số nhà, đường, quận/huyện, tỉnh/thành phố" rows="2"></textarea>
        <div class="two-fields"><div><label>Mật khẩu</label><input v-model="password" type="password" autocomplete="new-password" minlength="8" placeholder="Tối thiểu 8 ký tự" required /></div><div><label>Xác nhận mật khẩu</label><input v-model="confirmPassword" type="password" autocomplete="new-password" placeholder="Nhập lại mật khẩu" required /></div></div>
        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p><p v-if="successMessage" class="form-success">{{ successMessage }}</p>
        <button class="primary-button full-button" type="submit" :disabled="loading">{{ loading ? "ĐANG TẠO TÀI KHOẢN..." : "TẠO TÀI KHOẢN →" }}</button>
      </form>
      <p class="auth-bottom">Đã có tài khoản? <RouterLink to="/login">Đăng nhập</RouterLink></p>
    </div></div>
  </section>
</template>