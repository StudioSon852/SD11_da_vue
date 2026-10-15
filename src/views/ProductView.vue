<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import api from "../services/api";
import { sampleProducts } from "../data/products";
import type { Product } from "../types";
import { useCart } from "../composables/useCart";

const route = useRoute();
const { add } = useCart();
const product = ref<Product | null>(null);
const selectedSize = ref<number | null>(null);
const quantity = ref(1);
const message = ref("");
const money = (n: number) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(n);

onMounted(async () => {
  const id = String(route.params.id);
  const local = sampleProducts.find((p) => String(p.id) === id);
  product.value = local || null;
  try {
    const { data } = await api.get(`/products/${encodeURIComponent(id)}`);
    const p = data.product || data;
    product.value = { id: p.id ?? p.product_id, name: p.name ?? p.title, brand: p.brand ?? "ShoeHub", category: p.category ?? "Thời trang", price: Number(p.price), old_price: p.old_price ? Number(p.old_price) : undefined, image: p.image ?? p.image_url ?? "", description: p.description ?? "", sizes: Array.isArray(p.sizes) ? p.sizes.map(Number) : [39,40,41,42,43], badge: p.badge };
  } catch { /* Dữ liệu mẫu vẫn dùng được nếu API chưa triển khai. */ }
});
const related = computed(() => sampleProducts.filter((p) => p.id !== product.value?.id).slice(0, 4));

function addToCart() {
  if (!product.value || selectedSize.value === null) {
    message.value = "Vui lòng chọn size giày.";
    return;
  }

  add(
    product.value,
    selectedSize.value,
    quantity.value
  );

  message.value = "Đã thêm sản phẩm vào giỏ hàng.";
}
</script>

<template>
  <div class="container inner-page" v-if="product">
    <div class="breadcrumbs">
      <RouterLink to="/">Trang chủ</RouterLink> / {{ product.category }} /
      {{ product.name }}
    </div>
    <section class="detail-layout">
      <div class="detail-image">
        <img :src="product.image" :alt="product.name" />
      </div>
      <div class="detail-info">
        <span class="eyebrow"
          >{{ product.brand }} · {{ product.category }}</span
        >
        <h1>{{ product.name }}</h1>
        <div class="detail-rating">★★★★★ <span>(Sản phẩm nổi bật)</span></div>
        <div class="detail-price">
          {{ money(product.price) }}
          <del v-if="product.old_price">{{ money(product.old_price) }}</del>
        </div>
        <p class="detail-description">{{ product.description }}</p>
        <label class="detail-label">Chọn size (EU)</label>
        <div class="size-list">
          <button
            v-for="size in product.sizes"
            :key="size"
            :class="{ selected: selectedSize === size }"
            @click="selectedSize = size"
          >
            {{ size }}
          </button>
        </div>
        <label class="detail-label">Số lượng</label>
        <div class="quantity-control">
          <button @click="quantity = Math.max(1, quantity - 1)">−</button
          ><span>{{ quantity }}</span
          ><button @click="quantity = Math.min(10, quantity + 1)">＋</button>
        </div>
        <p
          v-if="message"
          :class="message.startsWith('Đã') ? 'form-success' : 'form-error'"
        >
          {{ message }}
        </p>
        <button class="primary-button full-button" @click="addToCart">
          THÊM VÀO GIỎ HÀNG →
        </button>
        <ul class="detail-promises">
          <li>✓ Sản phẩm được kiểm tra trước khi gửi</li>
          <li>✓ Hỗ trợ tư vấn chọn size</li>
          <li>✓ Liên hệ hỗ trợ nếu cần đổi size</li>
        </ul>
      </div>
    </section>
    <section class="products-section related-section">
      <div class="section-heading">
        <div>
          <span class="eyebrow">CÓ THỂ BẠN SẼ THÍCH</span>
          <h2>Sản phẩm liên quan</h2>
        </div>
      </div>
      <div class="product-grid">
        <article v-for="item in related" :key="item.id" class="product-card">
          <RouterLink :to="`/products/${item.id}`" class="product-image-wrap"
            ><img :src="item.image" :alt="item.name"
          /></RouterLink>
          <div class="product-info">
            <div class="product-brand">{{ item.brand }}</div>
            <RouterLink :to="`/products/${item.id}`" class="product-name">{{
              item.name
            }}</RouterLink
            ><strong>{{ money(item.price) }}</strong>
          </div>
        </article>
      </div>
    </section>
  </div>
  <div v-else class="container inner-page empty-state">
    Không tìm thấy sản phẩm.</div>
</template>
