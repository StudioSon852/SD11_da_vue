<script setup lang="ts">
import axios from "axios";
import { ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();

const full_name = ref("");
const email = ref("");
const password = ref("");
const confirmPassword = ref("");
const phone = ref("");
const address = ref("");
const role = ref("customer");

const errorMessage = ref("");
const successMessage = ref("");

const register = async () => {

  errorMessage.value = "";
  successMessage.value = "";

  if (password.value !== confirmPassword.value) {
    errorMessage.value = "Mật khẩu xác nhận không khớp";
    return;
  }

  try {

    const payload = {
      full_name: full_name.value,
      email: email.value,
      password: password.value,
      phone: phone.value,
      address: address.value,
      role: role.value
    };

    console.log("REGISTER PAYLOAD:", payload);

    const { data } = await axios.post(
      "http://localhost:3000/api/auth/register",
      payload
    );

    console.log("REGISTER RESPONSE:", data);

    if (data.success) {

      successMessage.value = data.message;

      alert(data.message);

      router.push("/");

    }

  } catch (err: any) {

    console.error(err);

    errorMessage.value =
      err?.response?.data?.message ||
      err?.message ||
      "Đăng ký thất bại";

  }

};
</script>
<template>
  <div class="register-container">

    <div class="register-card">

      <h2>CREATE ACCOUNT</h2>
      <p
  v-if="errorMessage"
  style="color:red;text-align:center"
>
  {{ errorMessage }}
</p>

<p
  v-if="successMessage"
  style="color:green;text-align:center"
>
  {{ successMessage }}
</p>

      <form @submit.prevent="register">

        <div class="form-group">
  <label>Họ và tên</label>
  <input
    v-model="full_name"
    type="text"
    placeholder="Nguyễn Văn A"
    required
  />
</div>

<div class="form-group">
  <label>Email</label>
  <input
    v-model="email"
    type="email"
    placeholder="example@gmail.com"
    required
  />
</div>

<div class="form-group">
  <label>Số điện thoại</label>
  <input
    v-model="phone"
    type="text"
    placeholder="09xxxxxxxx"
    required
  />
</div>

<div class="form-group">
  <label>Địa chỉ</label>
  <textarea
    v-model="address"
    placeholder="Nhập địa chỉ"
  ></textarea>
</div>

<div class="form-group">
  <label>Loại tài khoản</label>

  <select v-model="role">

    <option value="customer">
      Khách hàng
    </option>

    <option value="employee">
      Nhân viên
    </option>

    <option value="admin">
      Quản trị viên
    </option>

  </select>
</div>

<div class="form-group">
  <label>Mật khẩu</label>
  <input
    v-model="password"
    type="password"
    placeholder="Nhập mật khẩu"
    required
  />
</div>

<div class="form-group">
  <label>Xác nhận mật khẩu</label>
  <input
    v-model="confirmPassword"
    type="password"
    placeholder="Nhập lại mật khẩu"
    required
  />
</div>

        <button
          type="submit"
          class="btn-register"
        >
          Đăng ký
        </button>

        <div class="login-link">

          Đã có tài khoản?

          <router-link to="/">
            Đăng nhập
          </router-link>

        </div>

      </form>

    </div>

  </div>
</template>

<style scoped>

.register-container {
  width: 100vw;
  min-height: 100vh;

  display: flex;
  justify-content: center;
  align-items: center;

  background: linear-gradient(
    to bottom,
    #2c3e50,
    #3498db,
    #eaf4ff,
    #ffffff
  );
}

.register-card {

  width: 500px;

  background: white;

  padding: 30px;

  border-radius: 15px;

  box-shadow: 0 0 15px rgba(0,0,0,.15);

}

h2{
  text-align:center;
  margin-bottom:20px;
}

.form-group{
  display:flex;
  flex-direction:column;
  margin-bottom:15px;
}

label{
  margin-bottom:5px;
  font-weight:600;
}

input,
textarea,
select{

  width:100%;

  padding:10px;

  border:1px solid #ccc;

  border-radius:8px;

  font-size:14px;
}

textarea{
  min-height:80px;
}

.btn-register{

  width:100%;

  padding:12px;

  border:none;

  border-radius:8px;

  background:#2E86C1;

  color:white;

  font-size:16px;

  cursor:pointer;
}

.btn-register:hover{

  background:#1f6ea5;
}

.login-link{

  margin-top:15px;

  text-align:center;
}

</style>