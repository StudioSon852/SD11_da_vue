
<script setup lang="ts">
import { ref } from "vue";
import axios from "axios";
import { useRouter } from "vue-router";
import api from "../services/api";

const router = useRouter();

// Dữ liệu form đăng ký
const full_name = ref("");
const email = ref("");
const password = ref("");
const confirmPassword = ref("");
const phone = ref("");
const address = ref("");
const birth_date = ref("");

// Trạng thái giao diện
const errorMessage = ref("");
const successMessage = ref("");
const loading = ref(false);

// Lấy ngày hiện tại theo giờ địa phương

function getTodayLocalDate(): string {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, "0");
  const day = String(today.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}


// Xử lý đăng ký
async function register() {
  errorMessage.value = "";
  successMessage.value = "";

  // Kiểm tra họ tên
  if (!full_name.value.trim()) {
    errorMessage.value = "Vui lòng nhập họ và tên.";
    return;
  }

  // Kiểm tra email
  if (!email.value.trim()) {
    errorMessage.value = "Vui lòng nhập email.";
    return;
  }

  // Kiểm tra số điện thoại
  if (!phone.value.trim()) {
    errorMessage.value = "Vui lòng nhập số điện thoại.";
    return;
  }

  // Kiểm tra độ dài mật khẩu
  if (password.value.length < 8) {
    errorMessage.value = "Mật khẩu cần tối thiểu 8 ký tự.";
    return;
  }

  // Kiểm tra mật khẩu xác nhận
  if (password.value !== confirmPassword.value) {
    errorMessage.value = "Mật khẩu xác nhận không khớp.";
    return;
  }

  
if (birth_date.value) {
  const selectedDate = new Date(`${birth_date.value}T00:00:00`);
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  if (
    Number.isNaN(selectedDate.getTime()) ||
    selectedDate > today
  ) {
    errorMessage.value = "Ngày sinh không hợp lệ hoặc nằm trong tương lai.";
    return;
  }
}


  loading.value = true;

  try {
    const { data } = await api.post("/auth/register", {
      full_name: full_name.value.trim(),
      email: email.value.trim(),
      password: password.value,
      phone: phone.value.trim(),
      address: address.value.trim(),
      birth_date: birth_date.value || null,
    });

    successMessage.value =
      data.message || "Đăng ký thành công. Vui lòng đăng nhập.";

    // Chuyển sang trang đăng nhập sau khi đăng ký thành công
    setTimeout(() => {
      router.push("/");
    }, 1000);
  
} catch (error: unknown) {
  if (axios.isAxiosError(error)) {
    console.error("Lỗi đăng ký:", error);
    console.error("HTTP status:", error.response?.status);
    console.error("Backend response:", error.response?.data);

    errorMessage.value =
      error.response?.data?.message ||
      error.message ||
      "Không thể kết nối máy chủ.";
  } else {
    console.error("Lỗi không xác định:", error);
    errorMessage.value = "Đã xảy ra lỗi khi đăng ký.";
  }
} finally {
  loading.value = false;
}

}
</script>

<template>
  <div class="register-container">
    <div class="register-card">
      <h2>Tạo tài khoản</h2>

      <!-- Thông báo lỗi -->
      <p v-if="errorMessage" class="error-message">
        {{ errorMessage }}
      </p>

      <!-- Thông báo thành công -->
      <p v-if="successMessage" class="success-message">
        {{ successMessage }}
      </p>

      <form @submit.prevent="register">
        <!-- Họ và tên -->
        <div class="form-group">
          <label for="full_name">Họ và tên</label>
          <input
            id="full_name"
            v-model="full_name"
            type="text"
            placeholder="Nguyễn Văn A"
            autocomplete="name"
            required
          />
        </div>

        <!-- Email -->
        <div class="form-group">
          <label for="email">Email</label>
          <input
            id="email"
            v-model="email"
            type="email"
            placeholder="example@gmail.com"
            autocomplete="email"
            required
          />
        </div>

        <!-- Số điện thoại -->
        <div class="form-group">
          <label for="phone">Số điện thoại</label>
          <input
            id="phone"
            v-model="phone"
            type="tel"
            placeholder="09xxxxxxxx"
            autocomplete="tel"
            required
          />
        </div>

        <!-- Ngày sinh -->
        <div class="form-group">
          <label for="birth_date">Ngày sinh</label>
          <input
            id="birth_date"
            v-model="birth_date"
            type="date"
            :max="getTodayLocalDate()"
          />
        </div>

        <!-- Địa chỉ -->
        <div class="form-group">
          <label for="address">Địa chỉ</label>
          <textarea
            id="address"
            v-model="address"
            placeholder="Nhập địa chỉ"
            autocomplete="street-address"
          ></textarea>
        </div>

        <!-- Mật khẩu -->
        <div class="form-group">
          <label for="password">Mật khẩu</label>
          <input
            id="password"
            v-model="password"
            type="password"
            placeholder="Nhập mật khẩu"
            autocomplete="new-password"
            minlength="8"
            required
          />
        </div>

        <!-- Xác nhận mật khẩu -->
        <div class="form-group">
          <label for="confirmPassword">Xác nhận mật khẩu</label>
          <input
            id="confirmPassword"
            v-model="confirmPassword"
            type="password"
            placeholder="Nhập lại mật khẩu"
            autocomplete="new-password"
            minlength="8"
            required
          />
        </div>

        <!-- Nút đăng ký -->
        <button
          type="submit"
          class="btn-register"
          :disabled="loading"
        >
          {{ loading ? "ĐANG ĐĂNG KÝ..." : "ĐĂNG KÝ" }}
        </button>

        <!-- Liên kết đăng nhập -->
        <div class="login-link">
          Đã có tài khoản?
          <router-link to="/">Đăng nhập</router-link>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.register-container {
  width: 100%;
  min-height: 100vh;
  box-sizing: border-box;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px 16px;
  background: linear-gradient(
    to bottom,
    #2c3e50,
    #3498db,
    #eaf4ff,
    #ffffff
  );
}

.register-card {
  width: 100%;
  max-width: 500px;
  box-sizing: border-box;
  background: white;
  padding: 30px;
  border-radius: 15px;
  box-shadow: 0 0 15px rgba(0, 0, 0, 0.15);
}

h2 {
  text-align: center;
  margin: 0 0 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  margin-bottom: 15px;
}

label {
  margin-bottom: 5px;
  font-weight: 600;
}

input,
textarea,
select {
  width: 100%;
  box-sizing: border-box;
  padding: 10px;
  border: 1px solid #ccc;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
}

textarea {
  min-height: 80px;
  resize: vertical;
}

input:focus,
textarea:focus {
  outline: none;
  border-color: #2e86c1;
  box-shadow: 0 0 0 2px rgba(46, 134, 193, 0.15);
}

.btn-register {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 8px;
  background: #2e86c1;
  color: white;
  font-size: 16px;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-register:hover:not(:disabled) {
  background: #1f6ea5;
}

.btn-register:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.error-message {
  color: #d93025;
  text-align: center;
  margin: 0 0 15px;
  overflow-wrap: anywhere;
}

.success-message {
  color: #188038;
  text-align: center;
  margin: 0 0 15px;
  overflow-wrap: anywhere;
}

.login-link {
  margin-top: 15px;
  text-align: center;
}

.login-link a {
  color: #2e86c1;
  text-decoration: none;
  font-weight: 600;
}

.login-link a:hover {
  text-decoration: underline;
}

@media (max-width: 480px) {
  .register-card {
    padding: 22px 18px;
  }
}
</style>
