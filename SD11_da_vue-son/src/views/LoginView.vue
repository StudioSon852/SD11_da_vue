<script setup lang="ts">
import { ref } from "vue";
import axios from "axios";
import { useRoute, useRouter } from "vue-router";
import api from "../services/api";

const router = useRouter();
const route = useRoute();
const email = ref("");
const password = ref("");
const showPassword = ref(false);
const loading = ref(false);
const errorMessage = ref("");

async function login() {
  errorMessage.value = "";
  if (!email.value.trim() || !password.value) {
    errorMessage.value = "Vui lòng nhập email và mật khẩu.";
    return;
  }
  loading.value = true;
  try {
    const { data } = await api.post("/auth/login", {
      email: email.value.trim(),
      password: password.value
    });
    if (!data.user) throw new Error("API đăng nhập chưa trả về trường user.");
    localStorage.setItem("user", JSON.stringify(data.user));
    if (data.token) localStorage.setItem("token", data.token);
    router.push(typeof route.query.redirect === "string" ? route.query.redirect : "/");
  } catch (error: unknown) {
    if (axios.isAxiosError(error)) errorMessage.value = error.response?.data?.message || "Không kết nối được server. Kiểm tra backend và CORS.";
    else errorMessage.value = error instanceof Error ? error.message : "Đăng nhập thất bại.";
  } finally { loading.value = false; }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-aside"><span class="eyebrow">WELCOME BACK</span><h1>Sẵn sàng cho<br />bước đi tiếp theo?</h1><p>Đăng nhập để xem thông tin tài khoản và tiếp tục khám phá những đôi giày yêu thích.</p><div class="auth-shoe">STEP<br /><em>FORWARD.</em></div></div>
    <div class="auth-panel"><div class="auth-card"><RouterLink to="/" class="back-link">← Về trang chủ</RouterLink><span class="eyebrow">TÀI KHOẢN FREEDOMSHOE</span><h2>Đăng nhập</h2><p class="auth-subtitle">Chào mừng bạn quay trở lại.</p>
      <form @submit.prevent="login">
        <label>Email</label><input v-model.trim="email" type="email" autocomplete="email" placeholder="example@gmail.com" required />
        <label>Mật khẩu</label><div class="password-field"><input v-model="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="Nhập mật khẩu" required /><button type="button" @click="showPassword = !showPassword">{{ showPassword ? "Ẩn" : "Hiện" }}</button></div>
        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
        <button class="primary-button full-button" type="submit" :disabled="loading">{{ loading ? "ĐANG ĐĂNG NHẬP..." : "ĐĂNG NHẬP →" }}</button>
      </form>
      <p class="auth-bottom">Chưa có tài khoản? <RouterLink to="/signup">Đăng ký ngay</RouterLink></p>
    </div></div>
  </section>
</template>