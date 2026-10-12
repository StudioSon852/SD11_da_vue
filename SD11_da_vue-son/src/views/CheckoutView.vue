<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import axios from "axios";
import api from "../services/api";
import { useCart } from "../composables/useCart";

const router = useRouter();
const { items, total, clear } = useCart();
const user = computed(() => {
  try { return JSON.parse(localStorage.getItem("user") || "{}") as { full_name?: string; email?: string; phone?: string; address?: string }; }
  catch { return {}; }
});
const fullName = ref(user.value.full_name || "");
const phone = ref(user.value.phone || "");
const address = ref(user.value.address || "");
const note = ref("");
const errorMessage = ref("");
const successMessage = ref("");
const loading = ref(false);
const money = (n: number) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(n);

async function placeOrder() {
  errorMessage.value = "";
  successMessage.value = "";
  if (!items.value.length) { errorMessage.value = "Giỏ hàng đang trống."; return; }
  if (!fullName.value.trim() || !phone.value.trim() || !address.value.trim()) { errorMessage.value = "Vui lòng điền họ tên, số điện thoại và địa chỉ."; return; }
  loading.value = true;
  try {
    const { data } = await api.post("/orders", {
      customer_name: fullName.value.trim(),
      phone: phone.value.trim(),
      shipping_address: address.value.trim(),
      note: note.value.trim(),
      items: items.value.map((item) => ({ product_id: item.product_id, name: item.name, price: item.price, quantity: item.quantity, size: item.size })),
      total: total.value
    });
    successMessage.value = data.message || "Đặt hàng thành công.";
    clear();
    window.setTimeout(() => router.push("/account"), 1200);
  } catch (error: unknown) {
    if (axios.isAxiosError(error)) errorMessage.value = error.response?.data?.message || "Chưa đặt được hàng. Backend cần triển khai POST /orders.";
    else errorMessage.value = "Đặt hàng thất bại.";
  } finally { loading.value = false; }
}
</script>

<template>
  <div class="container inner-page"><span class="eyebrow">CHECKOUT</span><h1 class="page-title">Thông tin đặt hàng</h1>
    <div v-if="items.length" class="checkout-layout"><form class="checkout-form" @submit.prevent="placeOrder"><h2>Thông tin giao hàng</h2><label>Họ và tên</label><input v-model.trim="fullName" required /><label>Số điện thoại</label><input v-model.trim="phone" type="tel" required /><label>Địa chỉ nhận hàng</label><textarea v-model.trim="address" rows="3" required placeholder="Số nhà, đường, phường/xã, tỉnh/thành phố"></textarea><label>Ghi chú đơn hàng (không bắt buộc)</label><textarea v-model.trim="note" rows="2" placeholder="Ghi chú cho cửa hàng"></textarea><p v-if="errorMessage" class="form-error">{{ errorMessage }}</p><p v-if="successMessage" class="form-success">{{ successMessage }}</p><button class="primary-button full-button" :disabled="loading">{{ loading ? "ĐANG GỬI ĐƠN..." : "XÁC NHẬN ĐẶT HÀNG →" }}</button></form>
      <aside class="order-summary"><h2>Đơn hàng của bạn</h2><div v-for="item in items" :key="`${item.product_id}-${item.size}`" class="checkout-item"><img :src="item.image" :alt="item.name" /><div><strong>{{ item.name }}</strong><small>Size {{ item.size }} · SL {{ item.quantity }}</small></div><b>{{ money(item.price * item.quantity) }}</b></div><div class="summary-total"><span>Tổng cộng</span><strong>{{ money(total) }}</strong></div><p class="summary-note">Demo thanh toán khi xác nhận đơn. Chưa tích hợp cổng thanh toán trực tuyến.</p></aside></div>
    <div v-else class="empty-state"><h2>Không có sản phẩm để đặt hàng</h2><RouterLink to="/" class="primary-button">QUAY LẠI MUA SẮM</RouterLink></div>
  </div>
</template>