<script setup lang="ts">
import { useCart } from "../composables/useCart";
const { items, total, updateQuantity, remove } = useCart();
const money = (n: number) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(n);
</script>

<template>
  <div class="container inner-page">
    <div class="section-heading"><div><span class="eyebrow">YOUR SELECTION</span><h1 class="page-title">Giỏ hàng</h1></div><RouterLink to="/" class="text-link">← Tiếp tục mua sắm</RouterLink></div>
    <div v-if="items.length" class="cart-layout">
      <section class="cart-list"><article v-for="item in items" :key="`${item.product_id}-${item.size}`" class="cart-item">
        <img :src="item.image" :alt="item.name" /><div class="cart-item-info"><h3>{{ item.name }}</h3><p>Size: {{ item.size }}</p><strong>{{ money(item.price) }}</strong><button class="remove-link" @click="remove(item.product_id, item.size)">Xóa</button></div>
        <div class="quantity-control"><button @click="updateQuantity(item.product_id, item.size, item.quantity - 1)">−</button><span>{{ item.quantity }}</span><button @click="updateQuantity(item.product_id, item.size, item.quantity + 1)">＋</button></div>
        <strong class="line-total">{{ money(item.price * item.quantity) }}</strong>
      </article></section>
      <aside class="order-summary"><h2>Tóm tắt đơn hàng</h2><div class="summary-line"><span>Tạm tính</span><span>{{ money(total) }}</span></div><div class="summary-line"><span>Vận chuyển</span><span>Thỏa thuận</span></div><div class="summary-total"><span>Tổng cộng</span><strong>{{ money(total) }}</strong></div><RouterLink to="/checkout" class="primary-button full-button">TIẾN HÀNH ĐẶT HÀNG →</RouterLink><p class="summary-note">Phí vận chuyển (nếu có) sẽ được xác nhận khi đặt hàng.</p></aside>
    </div>
    <div v-else class="empty-state"><div class="empty-icon">♧</div><h2>Giỏ hàng đang trống</h2><p>Hãy tìm đôi giày tiếp theo của bạn.</p><RouterLink to="/" class="primary-button">KHÁM PHÁ SẢN PHẨM</RouterLink></div>
  </div>
</template>