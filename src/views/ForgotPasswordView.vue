<script setup>
import { ref } from 'vue'
import { requestPasswordReset } from '../stores/authStore'
import { notify } from '../stores/uiStore'
import BrandLogo from '../components/BrandLogo.vue'

const email = ref('')
const loading = ref(false)
const sent = ref(false)
const resetLink = ref('')

const submit = async () => {
  if (!email.value || !/.+@.+\..+/.test(email.value)) { notify({ type: 'error', message: 'Vui lòng nhập email hợp lệ.' }); return }
  loading.value = true
  try {
    const response = await requestPasswordReset(email.value)
    if (response?.resetToken) resetLink.value = `/reset-password?token=${encodeURIComponent(response.resetToken)}`
  } catch { /* vẫn hiển thị phản hồi chung để tránh lộ email tồn tại */ }
  loading.value = false
  sent.value = true
  notify({ type: 'success', title: 'Đã gửi yêu cầu', message: 'Kiểm tra email để đặt lại mật khẩu.' })
}
</script>

<template>
  <div class="auth-page">
    <div class="fp-card sg-card">
      <router-link to="/" class="auth-logo">
        <BrandLogo :size="32" :radius="6" />
        <span class="auth-logo-text"><span class="logo-shoe">SHOE</span><span class="logo-group">GROUP</span></span>
      </router-link>

      <div v-if="!sent">
        <div class="fp-ic"><i class="icon icon-key"></i></div>
        <h2 class="auth-title">Quên mật khẩu?</h2>
        <p class="auth-sub">Nhập email đăng ký, chúng tôi sẽ gửi liên kết đặt lại mật khẩu đến hộp thư của bạn.</p>
        <label class="co-label">Email</label>
        <div class="in-wrap"><i class="icon icon-envelope"></i><input v-model="email" type="email" class="auth-input" placeholder="you@example.com" @keyup.enter="submit"></div>
        <button class="btn-sg w-full mt-4" :disabled="loading" @click="submit"><i class="icon icon-send mr-2"></i>{{ loading ? 'Đang gửi…' : 'Gửi liên kết đặt lại' }}</button>
      </div>

      <div v-else class="fp-sent">
        <div class="suc-check"><i class="icon icon-envelope-check"></i></div>
        <h2 class="auth-title">Kiểm tra email của bạn</h2>
        <p class="auth-sub">Yêu cầu đặt lại mật khẩu đã được ghi nhận. Bản hiện tại chưa tích hợp máy chủ gửi email; nếu đang chạy local và đã bật chế độ thử nghiệm, liên kết sẽ hiện bên dưới.</p>
        <div v-if="resetLink" class="fp-dev-link"><strong>Liên kết thử nghiệm (chỉ dùng local):</strong><router-link :to="resetLink">Mở trang đặt lại mật khẩu</router-link></div>
        <div class="fp-hint"><i class="icon icon-info-circle"></i> Khi triển khai thực tế, hãy cấu hình dịch vụ email và tắt AUTH_EXPOSE_RESET_TOKEN.</div>
      </div>

      <router-link to="/login" class="fp-back"><i class="icon icon-arrow-left mr-1"></i>Quay lại đăng nhập</router-link>
    </div>
  </div>
</template>

<style scoped>
.auth-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px; background: var(--sg-grad-hero); }
.fp-card { max-width: 460px; width: 100%; padding: 44px; text-align: center; border-radius: 26px; }
.auth-logo { display: inline-flex; align-items: center; gap: 8px; font-weight: 900; font-size: 1.3rem; color: var(--sg-ink); text-decoration: none; font-family: 'Inter', sans-serif; letter-spacing: 0.12em; }
.auth-logo-text .logo-shoe { color: #0A0A0A; }
.auth-logo-text .logo-group { color: #0A0A0A; }
.fp-ic { width: 66px; height: 66px; margin: 26px auto 0; border-radius: 50%; background: var(--sg-soft); color: var(--sg-blue); display: flex; align-items: center; justify-content: center; font-size: 1.8rem; }
.auth-title { font-weight: 900; font-size: 1.7rem; margin-top: 18px; }
.auth-sub { color: var(--sg-muted); margin-bottom: 18px; }
.co-label { font-weight: 700; font-size: .82rem; color: var(--sg-ink-2); margin: 12px 0 6px; display: block; text-align: left; }
.in-wrap { display: flex; align-items: center; gap: 10px; border: 1.5px solid var(--sg-line); border-radius: 12px; padding: 4px 14px; transition: .2s; }
.in-wrap:focus-within { border-color: var(--sg-blue); box-shadow: 0 0 0 4px rgba(37,99,235,.12); }
.in-wrap i { color: var(--sg-muted); }
.auth-input { border: 0; outline: none; padding: 12px 0; width: 100%; font-weight: 500; background: transparent; }
.suc-check { width: 72px; height: 72px; margin: 20px auto 0; border-radius: 50%; background: linear-gradient(135deg,#22c55e,#16a34a); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 2.2rem; }
.fp-dev-link { display:flex; flex-direction:column; gap:8px; overflow-wrap:anywhere; margin-top:12px; padding:12px; border-radius:12px; background:#eff6ff; color:#1e40af; font-size:.83rem; text-align:left; }
.fp-dev-link a { color:#075985; text-decoration:underline; font-weight:800; }
.fp-hint { background: var(--sg-canvas); border-radius: 12px; padding: 12px 14px; font-size: .82rem; color: var(--sg-muted); margin-top: 10px; }
.fp-back { display: inline-block; margin-top: 22px; font-weight: 700; color: var(--sg-blue); text-decoration: none; font-size: .9rem; }
.fp-back:hover { text-decoration: underline; }
</style>

