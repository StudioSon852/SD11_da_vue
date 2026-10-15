<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useCart } from "./composables/useCart";
import logo from "./assets/dove-vector-logo.png";

const route = useRoute();
const router = useRouter();
const { count } = useCart();
const searchText = ref("");
const user = computed(() => {
  try {
    return JSON.parse(localStorage.getItem("user") || "null") as {
      full_name?: string;
      name?: string;
    } | null;
  } catch {
    return null;
  }
});
function search() {
  router.push({
    name: "home",
    query: searchText.value.trim() ? { q: searchText.value.trim() } : {},
  });
}
function logout() {
  localStorage.removeItem("user");
  localStorage.removeItem("token");
  router.push("/");
}
</script>

<template>
  <div class="site-shell">
    <div class="top-strip">
      MIỄN PHÍ VẬN CHUYỂN CHO ĐƠN TỪ 1.000.000₫ · ĐỔI SIZE DỄ DÀNG
    </div>
    <header class="site-header">
      <div class="header-inner container">
        <RouterLink to="/" class="logo">
          <img :src="logo" alt="FreedomShoes" class="logo-image" />
          <span>
            FREEDOM<span class="logo-accent">SHOE</span>
            <small>STEP INTO YOUR STYLE</small>
          </span>
        </RouterLink>
        <form class="search-box" @submit.prevent="search">
          <input
            v-model="searchText"
            aria-label="Tìm kiếm"
            placeholder="Tìm Nike, Adidas, giày chạy bộ..."
          />
          <button aria-label="Tìm kiếm">⌕</button>
        </form>
        <nav class="header-actions">
          <RouterLink v-if="!user" to="/login" class="header-link"
            >Đăng nhập</RouterLink
          >
          <RouterLink v-else to="/account" class="header-link"
            >Xin chào, {{ user.full_name || user.name || "bạn" }}</RouterLink
          >
          <button v-if="user" class="header-link logout-button" @click="logout">
            Thoát
          </button>
          <RouterLink to="/cart" class="cart-link"
            >♧ <span>Giỏ hàng</span><b>{{ count }}</b></RouterLink
          >
        </nav>
      </div>
      <div class="nav-row">
        <nav class="container main-nav">
          <RouterLink to="/">Trang chủ</RouterLink>
          <RouterLink to="/?category=Thời trang">Giày thời trang</RouterLink>
          <RouterLink to="/?category=Chạy bộ">Giày chạy bộ</RouterLink>
          <RouterLink to="/?category=Lifestyle">Lifestyle</RouterLink>
          <RouterLink to="/cart">Giỏ hàng</RouterLink>
        </nav>
      </div>
    </header>
    <main class="page-main" :key="route.fullPath"><RouterView /></main>
    <footer class="site-footer">
      <div class="container footer-grid">
        <div>
          <RouterLink to="/" class="logo">
          <img :src="logo" alt="FreedomShoes" class="logo-image" />
          <span>
            FREEDOM<span class="logo-accent">SHOE</span>
            <small>STEP INTO YOUR STYLE</small>
          </span>
        </RouterLink>
          <p>Chọn đôi giày phù hợp, tự do trên từng bước chân.</p>
        </div>
        <div>
          <h3>Hỗ trợ khách hàng</h3>
          <p>Hướng dẫn chọn size</p>
          <p>Chính sách đổi trả</p>
          <p>Chính sách giao hàng</p>
        </div>
        <div>
          <h3>Liên hệ</h3>
          <p>Email: support@freedomshoe.local</p>
          <p>Hotline: 1900 0000</p>
          <p>Thứ 2 - Chủ nhật: 8:00 - 21:00</p>
        </div>
      </div>
      <div class="copyright">© 2026 FreedomShoes. SD_11.</div>
    </footer>
  </div>
</template>
<style scoped>
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
}

.logo-image {
  width: 40px;
  height: 40px;
  object-fit: contain;
  flex-shrink: 0;
}

</style>
