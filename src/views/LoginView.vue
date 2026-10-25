<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter, RouterLink } from "vue-router";
import api from "../services/api";
import { setSessionUser } from "../stores/authStore.js";
import type { SessionUser } from "../types";

const router = useRouter();
const route = useRoute();
const email = ref("");
const password = ref("");
const showPassword = ref(false);
const loading = ref(false);
const errorMessage = ref("");

async function login() {
  errorMessage.value = "";
  if (!email.value.trim() || !password.value) { errorMessage.value = "Vui lòng nhập email và mật khẩu."; return; }
  loading.value = true;
  try {
    const data = await api.post("/auth/login", { email: email.value.trim(), password: password.value });
    if (!data?.success || !data?.user || !data?.token) throw new Error("API đăng nhập chưa trả về user hoặc JWT hợp lệ.");
    const signedUser = { ...(data.user as SessionUser), token: data.token as string };
    setSessionUser(signedUser);
    const role = String(signedUser.role || (Number(signedUser.role_id) === 1 ? "admin" : Number(signedUser.role_id) === 4 ? "manager" : Number(signedUser.role_id) === 3 ? "employee" : "customer")).toLowerCase();
    const roleHome = role === "admin" ? "/admin" : role === "manager" ? "/manager" : ["employee", "staff", "nhan vien", "nhân viên"].includes(role) ? "/employee" : "/";
    const requested = typeof route.query.redirect === "string" ? route.query.redirect : "";
    const redirect = requested.startsWith("/") && !requested.startsWith("//") ? requested : roleHome;
    await router.push(redirect);
  } catch (error: unknown) {
    const e = error as { message?: string; data?: { message?: string } };
    const raw = e.data?.message || e.message || "";
    errorMessage.value = /failed to fetch|network|load failed/i.test(raw)
      ? "Không kết nối được API. Mở http://localhost:8080/api/health để xem chẩn đoán SQL Server."
      : (raw || "Đăng nhập thất bại.");
  } finally { loading.value = false; }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-aside"><span class="eyebrow">WELCOME BACK</span><h1>Sẵn sàng cho<br />bước đi tiếp theo?</h1><p>Đăng nhập để xem thông tin tài khoản và tiếp tục khám phá những đôi giày yêu thích.</p><div class="auth-shoe">STEP<br /><em>FORWARD.</em></div></div>
    <div class="auth-panel"><div class="auth-card">
      <RouterLink to="/" class="back-link">← Về trang chủ</RouterLink><span class="eyebrow">TÀI KHOẢN FREEDOMS</span><h2>Đăng nhập</h2><p class="auth-subtitle">Chào mừng bạn quay trở lại.</p>
      <form @submit.prevent="login"><label for="login-email">Email</label><input id="login-email" v-model.trim="email" type="email" autocomplete="email" placeholder="example@gmail.com" required />
        <label for="login-password">Mật khẩu</label><div class="password-field"><input id="login-password" v-model="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="Nhập mật khẩu" required /><button type="button" :aria-label="showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'" @click="showPassword = !showPassword">{{ showPassword ? "Ẩn" : "Hiện" }}</button></div>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p><button class="primary-button full-button" type="submit" :disabled="loading">{{ loading ? "ĐANG ĐĂNG NHẬP..." : "ĐĂNG NHẬP →" }}</button>
        <p class="auth-forgot"><RouterLink to="/forgot-password">Quên mật khẩu?</RouterLink></p>
      </form><p class="auth-bottom">Chưa có tài khoản? <RouterLink to="/signup">Đăng ký ngay</RouterLink></p>
    </div></div>
  </section>
</template>

<style scoped>
.auth-page{box-sizing:border-box;width:100%;min-height:100vh;display:flex;align-items:center;justify-content:center;background:linear-gradient(to bottom,#2c3e50,#3498db,#eaf4ff,#fff);padding:24px}.auth-aside{display:none}.auth-panel{box-sizing:border-box;width:100%;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;background:transparent}.auth-card{width:100%;max-width:400px;padding:36px 34px;background:#ffffffed;border:1px solid #ffffffb8;border-radius:14px;box-shadow:0 22px 65px #122c4630;backdrop-filter:blur(12px)}.back-link{display:block;text-decoration:none;color:#516578;font-size:12px;margin-bottom:26px}.eyebrow{display:block;color:#4d7290;font-size:10px;font-weight:800;letter-spacing:.16em;margin-bottom:8px}.auth-card h2{font-size:30px;margin:10px 0 8px;color:#172b3b}.auth-subtitle{font-size:13px;color:#718092;margin-bottom:25px}.auth-card form>label{display:block;font-size:12px;color:#25394b;font-weight:700;margin:16px 0 7px}.auth-card input{box-sizing:border-box;width:100%;min-width:0;border:1px solid #d6e0e8;border-radius:7px;padding:13px 12px;background:#fff;color:#172b3b;outline:none;font-size:13px}.auth-card input:focus{border-color:#2e7fb9;box-shadow:0 0 0 3px #3498db20}.password-field{display:flex;align-items:center;gap:6px}.password-field input{flex:1}.password-field button{border:0;background:transparent;color:#2e6d9b;font-size:11px;cursor:pointer;padding:10px}.full-button{width:100%;margin-top:16px;border:0;border-radius:7px;background:#172b3b;color:#fff;padding:14px;font-size:11px;font-weight:800;letter-spacing:.07em;cursor:pointer}.full-button:hover{background:#244b69}.full-button:disabled{opacity:.65;cursor:wait}.form-error{font-size:12px;color:#b42318;background:#fff1f0;border-radius:5px;padding:10px;margin:13px 0 0}.auth-forgot{text-align:right;margin:12px 0 0;font-size:11px}.auth-forgot a{color:#17699f;text-decoration:none}.auth-forgot a:hover{text-decoration:underline}.auth-bottom{text-align:center;font-size:12px;color:#66788a;margin-top:23px}.auth-bottom a{color:#17699f;font-weight:800;text-decoration:none}.auth-bottom a:hover{text-decoration:underline}@media(max-width:480px){.auth-page,.auth-panel{padding:14px;min-height:100vh}.auth-card{padding:28px 20px}}
</style>
