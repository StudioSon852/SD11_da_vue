<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../services/api";
import { sampleProducts } from "../data/products";
import type { Product } from "../types";
import { useCart } from "../composables/useCart";

const route = useRoute();
const router = useRouter();

const { add, count } = useCart();

const products = ref<Product[]>(sampleProducts);
const loading = ref(false);
const loadError = ref("");
const activeCategory = ref("Tất cả");

const categories = ["Tất cả", "Thời trang", "Chạy bộ", "Lifestyle"];

// Chuẩn hóa dữ liệu sản phẩm từ API.
function normalizeProduct(p: any): Product {
  let imageUrl =
    p.image_url ??
    p.image ??
    p.imageUrl ??
    p.thumbnail ??
    p.photo ??
    sampleProducts[0].image;

  if (typeof imageUrl === "string" && imageUrl.includes("<a")) {
    const match = imageUrl.match(/href="([^"]+)"/);
    if (match) {
      imageUrl = match[1];
    }
  }

  return {
    id: p.id ?? p.product_id,
    name: p.name ?? p.title ?? "Sản phẩm",
    brand: p.brand ?? "FreeDomShoeHub",
    category: p.category ?? "Thời trang",
    price: Number(p.price ?? 0),
    old_price: p.old_price != null ? Number(p.old_price) : undefined,
    image: imageUrl,
    description:
      p.description ??
      "Sản phẩm giày chất lượng, phù hợp sử dụng hằng ngày.",
    sizes:
      Array.isArray(p.sizes) && p.sizes.length > 0
        ? p.sizes.map(Number)
        : [39, 40, 41, 42, 43],
    badge: p.badge,
  };
}

// Tải sản phẩm từ API; nếu API lỗi thì vẫn dùng 8 sản phẩm mẫu.
async function loadProducts() {
  loading.value = true;
  loadError.value = "";

  try {
    const { data } = await api.get("/products");

    console.log("Dữ liệu API trả về:", data);

    const list = Array.isArray(data)
      ? data
      : data?.products ?? data?.data ?? [];

    if (Array.isArray(list) && list.length > 0) {
      products.value = list.map(normalizeProduct);
    } else {
      products.value = sampleProducts;
      loadError.value =
        "API chưa có sản phẩm. Đang hiển thị sản phẩm mẫu.";
    }
  } catch (error) {
    console.error("Lỗi tải sản phẩm:", error);
    products.value = sampleProducts;
    loadError.value =
      "Không kết nối được API. Đang hiển thị sản phẩm mẫu.";
  } finally {
    loading.value = false;
  }
}

// Thêm sản phẩm vào giỏ hàng.
function addToCart(product: Product) {
  const size = product.sizes?.[0] ?? 39;

  add(product, size, 1);

  console.log("Đã thêm vào giỏ hàng:", product.name, size);

  // Thông báo đơn giản cho người dùng.
  window.alert(`Đã thêm "${product.name}" vào giỏ hàng!`);
}

// Mở trang giỏ hàng.
function goToCart() {
  router.push("/cart");
}

// Lọc theo từ khóa và danh mục.
const filteredProducts = computed(() => {
  const q = String(route.query.q || "")
    .toLowerCase()
    .trim();

  const category = String(
    route.query.category || activeCategory.value,
  );

  return products.value.filter((p) => {
    const productCategory = String(p.category || "").toLowerCase();
    const keyword = `${p.name} ${p.brand} ${p.category}`.toLowerCase();

    return (
      (category === "Tất cả" ||
        productCategory === category.toLowerCase()) &&
      (!q || keyword.includes(q))
    );
  });
});

// Đồng bộ danh mục với query trên URL.
watch(
  () => route.query.category,
  (value) => {
    activeCategory.value = value ? String(value) : "Tất cả";
  },
  { immediate: true },
);

onMounted(loadProducts);

// Định dạng giá tiền Việt Nam.
function money(value: number) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);
}
</script>

<template>
  <div class="container home-content">
    <section class="hero">
      <div class="hero-copy">
        <span class="eyebrow">BỘ SƯU TẬP MỚI 2026</span>

        <h1>
          Mỗi bước chân<br />
          là một <em>tuyên ngôn.</em>
        </h1>

        <p>
          Khám phá những đôi giày đồng hành cùng phong cách và nhịp sống của
          bạn.
        </p>

        <a href="#products" class="primary-button">
          KHÁM PHÁ NGAY <span>→</span>
        </a>

        <div class="hero-note">
          <span>✓</span> Sản phẩm chọn lọc
          <span>✓</span> Hỗ trợ đổi size
        </div>
      </div>

      <div class="hero-art">
        <div class="hero-circle"></div>

        <img
          src="https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=1200&q=90"
          alt="Giày thể thao đỏ"
        />

        <div class="floating-tag">
          <strong>NEW DROP</strong>
          <span>Fresh looks. Fresh steps.</span>
        </div>
      </div>
    </section>

    <section class="benefits">
      <div>
        <span class="benefit-icon">↗</span>
        <div>
          <strong>Giao hàng toàn quốc</strong>
          <small>Đóng gói cẩn thận</small>
        </div>
      </div>

      <div>
        <span class="benefit-icon">◇</span>
        <div>
          <strong>Đổi size linh hoạt</strong>
          <small>Chọn đúng đôi vừa chân</small>
        </div>
      </div>

      <div>
        <span class="benefit-icon">✓</span>
        <div>
          <strong>Thanh toán an tâm</strong>
          <small>Hỗ trợ khách hàng tận tình</small>
        </div>
      </div>
    </section>

    <section id="products" class="products-section">
      <div class="section-heading">
        <div>
          <span class="eyebrow">DÀNH RIÊNG CHO BẠN</span>
          <h2>Giày được yêu thích</h2>
        </div>

        <span class="product-count">
          {{ filteredProducts.length }} sản phẩm
        </span>
      </div>

      <div class="category-tabs">
        <button
          v-for="category in categories"
          :key="category"
          type="button"
          :class="{
            active:
              activeCategory === category &&
              !route.query.q &&
              !route.query.category,
          }"
          @click="activeCategory = category"
        >
          {{ category }}
        </button>
      </div>

      <p v-if="loading" class="notice">
        Đang tải danh sách sản phẩm...
      </p>

      <p v-if="loadError" class="notice">
        {{ loadError }}
      </p>

      <div v-if="filteredProducts.length" class="product-grid">
        <article
          v-for="product in filteredProducts"
          :key="product.id"
          class="product-card"
        >
          <RouterLink
            :to="`/products/${product.id}`"
            class="product-image-wrap"
          >
            <span v-if="product.badge" class="product-badge">
              {{ product.badge }}
            </span>

            <img
              :src="product.image"
              :alt="product.name"
              loading="lazy"
            />

            <span class="quick-view">Xem chi tiết →</span>
          </RouterLink>

          <div class="product-info">
            <div class="product-brand">
              {{ product.brand }}
              <span>★★★★★</span>
            </div>

            <RouterLink
              :to="`/products/${product.id}`"
              class="product-name"
            >
              {{ product.name }}
            </RouterLink>

            <div class="price-line">
              <strong>{{ money(product.price) }}</strong>
              <del v-if="product.old_price">
                {{ money(product.old_price) }}
              </del>
            </div>

            <div class="product-buttons">
              <RouterLink
                :to="`/products/${product.id}`"
                class="card-action"
              >
                Chọn size <span>＋</span>
              </RouterLink>

              <button
                type="button"
                class="add-cart-button"
                @click="addToCart(product)"
              >
                THÊM VÀO GIỎ
              </button>
            </div>
          </div>
        </article>
      </div>

      <div v-else-if="!loading" class="empty-state">
        Không tìm thấy sản phẩm phù hợp. Hãy thử từ khóa khác.
      </div>

      <div class="cart-shortcut">
        <button
          type="button"
          class="primary-button"
          @click="goToCart"
        >
          XEM GIỎ HÀNG ({{ count }}) →
        </button>
      </div>
    </section>

    <section class="bottom-promo">
      <div>
        <span class="eyebrow">YOUR EVERYDAY ESSENTIAL</span>

        <h2>
          Thoải mái chuyển động.<br />
          <em>Tự tin thể hiện.</em>
        </h2>

        <p>Tìm đôi giày phù hợp với phong cách của bạn.</p>
      </div>

      <RouterLink to="/signup" class="light-button">
        TẠO TÀI KHOẢN →
      </RouterLink>
    </section>
  </div>
</template>

<style scoped>
.product-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.add-cart-button {
  width: 100%;
  padding: 11px 12px;
  border: 1px solid #222;
  border-radius: 6px;
  background: #222;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition:
    background 0.2s ease,
    color 0.2s ease;
}

.add-cart-button:hover {
  background: #fff;
  color: #222;
}

.cart-shortcut {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
</style>
