
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

const money = (n: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(n);

const productCode = computed(() => {
  if (!product.value) return "";
  return `SP${String(product.value.id).padStart(3, "0")}`;
});

onMounted(async () => {
  const id = String(route.params.id);
  const local = sampleProducts.find((p) => String(p.id) === id);
  product.value = local || null;

  try {
    const { data } = await api.get(
      `/products/${encodeURIComponent(id)}`
    );

    const p = data.product || data;

    product.value = {
      id: p.id ?? p.product_id,
      name: p.name ?? p.title,
      brand: p.brand ?? "",
      category: p.category ?? "",
      price: Number(p.price),
      old_price:
        p.old_price == null ? undefined : Number(p.old_price),
      image: p.image ?? p.image_url ?? "",
      description: p.description ?? "",
      sizes: Array.isArray(p.sizes)
        ? p.sizes.map(Number)
        : [39, 40, 41, 42, 43],
      badge: p.badge,
    };
  } catch (error) {
    console.error("Không tải được chi tiết sản phẩm:", error);
  }
});

const related = computed(() =>
  sampleProducts
    .filter((p) => p.id !== product.value?.id)
    .slice(0, 4)
);

function addToCart() {
  if (!product.value || selectedSize.value === null) {
    message.value = "Vui lòng chọn size giày.";
    return;
  }

  add(product.value, selectedSize.value, quantity.value);
  message.value = "Đã thêm sản phẩm vào giỏ hàng.";
}
</script>

<template>
  <div v-if="product" class="container inner-page">
    <div class="breadcrumbs">
      <RouterLink to="/">Trang chủ</RouterLink>
      / {{ product.category }}
      / {{ product.name }}
    </div>

    <section class="detail-layout">
      <div class="detail-image">
        <img :src="product.image" :alt="product.name" />
      </div>

      <div class="detail-info">
        <span class="eyebrow">
          {{ product.brand }} · {{ product.category }}
        </span>

        <h1>{{ product.name }}</h1>

        <div class="detail-rating">
          ★★★★★
          <span>(Sản phẩm nổi bật)</span>
        </div>

        <div class="detail-price">
          {{ money(product.price) }}
          <del v-if="product.old_price">
            {{ money(product.old_price) }}
          </del>
        </div>

        <p class="detail-description">
          {{ product.description || "Chưa có mô tả sản phẩm." }}
        </p>

        <section class="product-specifications">
          <h2>Thông số sản phẩm</h2>

          <div class="specification-row">
            <span>Mã sản phẩm</span>
            <strong>{{ productCode }}</strong>
          </div>

          <div class="specification-row">
            <span>Tên sản phẩm</span>
            <strong>{{ product.name }}</strong>
          </div>

          <div class="specification-row">
            <span>Thương hiệu</span>
            <strong>{{ product.brand || "Chưa cập nhật" }}</strong>
          </div>

          <div class="specification-row">
            <span>Danh mục</span>
            <strong>{{ product.category || "Chưa cập nhật" }}</strong>
          </div>

          <div class="specification-row">
            <span>Giá bán</span>
            <strong>{{ money(product.price) }}</strong>
          </div>

          <div class="specification-row">
            <span>Size hiện có</span>
            <strong>
              {{
                product.sizes.length
                  ? product.sizes.join(", ")
                  : "Chưa cập nhật"
              }}
            </strong>
          </div>

          <div class="specification-row">
            <span>Mô tả</span>
            <strong>
              {{ product.description || "Chưa cập nhật" }}
            </strong>
          </div>
        </section>

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
          <button
            @click="quantity = Math.max(1, quantity - 1)"
          >
            −
          </button>

          <span>{{ quantity }}</span>

          <button
            @click="quantity = Math.min(10, quantity + 1)"
          >
            ＋
          </button>
        </div>

        <p
          v-if="message"
          :class="
            message.startsWith('Đã')
              ? 'form-success'
              : 'form-error'
          "
        >
          {{ message }}
        </p>

        <button
          class="primary-button full-button"
          @click="addToCart"
        >
          THÊM VÀO GIỎ HÀNG →
        </button>

        <ul class="detail-promises">
          <li>Sản phẩm được kiểm tra trước khi gửi</li>
          <li>Hỗ trợ tư vấn chọn size</li>
          <li>Liên hệ hỗ trợ nếu cần đổi size</li>
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
        <article
          v-for="item in related"
          :key="item.id"
          class="product-card"
        >
          <RouterLink
            :to="`/products/${item.id}`"
            class="product-image-wrap"
          >
            <img :src="item.image" :alt="item.name" />
          </RouterLink>

          <div class="product-info">
            <div class="product-brand">{{ item.brand }}</div>

            <RouterLink
              :to="`/products/${item.id}`"
              class="product-name"
            >
              {{ item.name }}
            </RouterLink>

            <strong>{{ money(item.price) }}</strong>
          </div>
        </article>
      </div>
    </section>
  </div>

  <div v-else class="container inner-page empty-state">
    Không tìm thấy sản phẩm.
  </div>
</template>

<style scoped>
.product-specifications {
  margin: 24px 0;
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}

.product-specifications h2 {
  margin: 0 0 12px;
  font-size: 20px;
  font-weight: 700;
}

.specification-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  padding: 12px 0;
  border-bottom: 1px solid #eee;
}

.specification-row:last-child {
  border-bottom: none;
}

.specification-row span {
  flex: 0 0 35%;
  color: #666;
}

.specification-row strong {
  flex: 1;
  text-align: right;
  overflow-wrap: anywhere;
  color: #222;
  font-weight: 600;
}
</style>
