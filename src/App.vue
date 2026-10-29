<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter, RouterLink, RouterView } from "vue-router";
import logo from "../img/logogiay.png";
import { cartCount, showDrawer } from "./stores/cartStore.js";
import { currentUser, logout as signOut } from "./stores/authStore.js";
import CenterNotify from "./components/CenterNotify.vue";
import PromoModal from "./components/PromoModal.vue";
import ZaloChat from "./components/ZaloChat.vue";
import CartDrawer from "./components/CartDrawer.vue";

const route = useRoute();
const router = useRouter();
const searchText = ref("");
const user = computed(
  () =>
    currentUser.value as {
      full_name?: string;
      name?: string;
      email?: string;
    } | null,
);
const isAdmin = computed(() => route.path.startsWith("/admin"));
const isEmployee = computed(
  () => route.path === "/employee" || route.path.startsWith("/employee/"),
);
const isAuthPage = computed(() =>
  [
    "/login",
    "/signup",
    "/register",
    "/forgot-password",
    "/reset-password",
  ].includes(route.path),
);
function search() {
  router.push({
    name: "home",
    query: searchText.value.trim() ? { q: searchText.value.trim() } : {},
  });
}
function logout() {
  signOut();
  router.push("/");
}
</script>

<template>
  <div class="site-shell">
    <template v-if="!isAdmin && !isEmployee && !isAuthPage">
      <div class="top-strip">
        MIỄN PHÍ VẬN CHUYỂN CHO ĐƠN TỪ 1.000.000₫ · ĐỔI SIZE DỄ DÀNG
      </div>
      <header class="site-header">
        <div class="header-inner site-container">
          <RouterLink to="/" class="logo">
            <img :src="logo" alt="FREEDOMS" class="logo-image" />
            <span>
              <strong>
                <span class="freedom-text">FREEDOM</span
                ><span class="freedom-s">S</span>
              </strong>
              <small>STEP INTO YOUR STYLE</small>
            </span>
          </RouterLink>
          <form class="search-box" @submit.prevent="search">
            <input
              v-model="searchText"
              aria-label="Tìm kiếm sản phẩm"
              placeholder="Tìm Nike, Adidas, giày chạy bộ..."
            /><button type="submit" aria-label="Tìm kiếm">⌕</button>
          </form>
          <nav class="header-actions">
            <RouterLink v-if="!user" to="/login" class="header-link"
              >Đăng nhập</RouterLink
            >
            <RouterLink v-else to="/account" class="header-link"
              >Xin chào,
              {{
                user.full_name || user.name || user.email || "bạn"
              }}</RouterLink
            >
            <button
              v-if="user"
              class="header-link logout-button"
              @click="logout"
            >
              Thoát
            </button>
            <button
              type="button"
              class="cart-link"
              aria-label="Mở giỏ hàng"
              @click="showDrawer"
            >
              <svg
                aria-hidden="true"
                width="17"
                height="17"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
                <path d="M3 6h18M16 10a4 4 0 0 1-8 0" />
              </svg>
              <span>Giỏ hàng</span>
              <b>{{ cartCount }}</b>
            </button>
          </nav>
        </div>
        <div class="nav-row">
          <nav class="site-container main-nav">
            <RouterLink to="/">Trang chủ</RouterLink
            ><RouterLink to="/products">Sản phẩm</RouterLink
            ><RouterLink to="/about">Về chúng tôi</RouterLink
            ><RouterLink to="/contact">Liên hệ</RouterLink>
          </nav>
        </div>
      </header>
    </template>
    <main
      class="page-main"
      :class="{ 'auth-main': isAuthPage && !isAdmin }"
      :key="route.fullPath"
    >
      <RouterView />
    </main>
    <footer v-if="!isAdmin && !isEmployee && !isAuthPage" class="site-footer">
      <div class="site-container footer-grid">
        <div>
          <RouterLink to="/" class="logo footer-logo"
            ><img :src="logo" alt="FREEDOMS" class="logo-image" /><span
              class="admin-brand-wordmark"
            >
              <strong>
                <span class="freedom-text">FREEDOM</span
                ><span class="freedom-s">S</span>
              </strong>
              <small>STEP INTO YOUR STYLE</small>
            </span></RouterLink
          >
          <p>Chọn đôi giày phù hợp, tự do trên từng bước chân.</p>
        </div>
        <div>
          <h3>Hỗ trợ khách hàng</h3>
          <RouterLink to="/contact">Hướng dẫn chọn size</RouterLink
          ><RouterLink to="/returns">Chính sách đổi trả</RouterLink
          ><RouterLink to="/orders">Theo dõi đơn hàng</RouterLink>
        </div>
        <div>
          <h3>Liên hệ</h3>
          <p>Email: support@freedomshoe.local</p>
          <p>Hotline: 1900 0000</p>
          <p>Thứ 2 - Chủ nhật: 8:00 - 21:00</p>
        </div>
      </div>
      <div class="copyright">© 2026 Freedoms · SD_11</div>
    </footer>
    <template v-if="!isAdmin && !isEmployee && !isAuthPage"
      ><CartDrawer /><PromoModal /><ZaloChat
    /></template>
    <CenterNotify />
  </div>
</template>

<style>
.site-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f9fcff;
  color: #17324d;
}
.page-main {
  flex: 1;
}
.auth-main {
  min-height: 100vh;
  background: linear-gradient(to bottom, #2c3e50, #3498db, #eaf4ff, #fff);
}
.site-container {
  width: min(1240px, calc(100% - 48px));
  margin: 0 auto;
}
.top-strip {
  background: #17324d;
  color: white;
  text-align: center;
  padding: 9px 12px;
  font-size: 9px;
  letter-spacing: 0.12em;
  font-weight: 700;
}
.site-header {
  background: #fff;
  position: relative;
  z-index: 10;
}
.header-inner {
  display: flex;
  align-items: center;
  gap: 32px;
  min-height: 83px;
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #111;
  text-decoration: none;
  font-weight: 900;
  letter-spacing: 0.05em;
  font-size: 16px;
  white-space: nowrap;
}
.logo-image {
  width: 41px;
  height: 41px;
  object-fit: contain;
}
.logo-accent {
  color: #3498db;
}
.logo small {
  display: block;
  font-size: 8px;
  font-weight: 600;
  letter-spacing: 0.13em;
  margin-top: 3px;
  color: #777;
}
.search-box {
  display: flex;
  align-items: center;
  flex: 1;
  max-width: 510px;
  border: 1px solid #dedede;
  border-radius: 5px;
  height: 40px;
}
.search-box input {
  border: 0;
  outline: 0;
  padding: 0 13px;
  flex: 1;
  min-width: 0;
  font-size: 11px;
  background: transparent;
}
.search-box button {
  border: 0;
  background: transparent;
  padding: 0 12px;
  font-size: 22px;
  cursor: pointer;
}
.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  margin-left: auto;
}
.header-link,
.cart-link {
  font-size: 11px;
  color: #222;
  text-decoration: none;
  white-space: nowrap;
}
.logout-button {
  border: 0;
  background: transparent;
  cursor: pointer;
}
.cart-link {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 0;
  border-left: 1px solid #e5e5e5;
  padding: 0 0 0 18px;
  background: transparent;
  font-family: inherit;
  cursor: pointer;
}
.cart-link b {
  display: grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #2f8bcb;
  color: #fff;
  font-size: 9px;
}
.nav-row {
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #e8e8e8;
}
.main-nav {
  display: flex;
  align-items: center;
  gap: 28px;
  min-height: 43px;
}
.main-nav a {
  font-size: 10px;
  font-weight: 700;
  color: #444;
  text-decoration: none;
}
.main-nav a:hover {
  color: #2785bd;
}
.site-footer {
  border-top: 1px solid #d8e5ef;
  background: #f2f8ff;
  margin-top: 30px;
}
.footer-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr;
  gap: 38px;
  padding: 35px 0;
}
.footer-grid p,
.footer-grid a {
  font-size: 11px;
  color: #666;
  line-height: 1.7;
}
.footer-grid a:not(.logo) {
  display: block;
  text-decoration: none;
  margin: 5px 0;
}
.footer-grid a:hover {
  color: #171717;
}
.footer-grid h3 {
  font-size: 12px;
  margin: 6px 0 10px;
}
.footer-logo {
  font-size: 15px;
}
.copyright {
  border-top: 1px solid #d8e5ef;
  text-align: center;
  padding: 13px;
  font-size: 9px;
  color: #777;
}
@media (max-width: 920px) {
  .header-inner {
    gap: 16px;
    flex-wrap: wrap;
    padding: 15px 0;
  }
  .search-box {
    order: 3;
    max-width: none;
    flex-basis: 100%;
  }
  .header-actions {
    gap: 12px;
  }
  .main-nav {
    gap: 20px;
  }
  .footer-grid {
    gap: 20px;
  }
}
@media (max-width: 600px) {
  .site-container {
    width: calc(100% - 28px);
  }
  .top-strip {
    font-size: 8px;
    letter-spacing: 0.05em;
  }
  .header-inner {
    gap: 12px;
  }
  .logo {
    font-size: 13px;
  }
  .logo-image {
    width: 32px;
    height: 32px;
  }
  .header-actions {
    gap: 9px;
  }
  .header-link {
    font-size: 10px;
  }
  .cart-link {
    padding-left: 9px;
    gap: 4px;
  }
  .cart-link > span:nth-child(2) {
    display: none;
  }
  .main-nav {
    gap: 18px;
    overflow: auto;
  }
  .main-nav a {
    white-space: nowrap;
  }
  .footer-grid {
    grid-template-columns: 1fr;
    gap: 14px;
    padding: 25px 0;
  }
}
/* Logo */
.logo-image {
  width: 38px;
  height: 38px;
  padding: 4px;
  object-fit: contain;
  box-sizing: border-box;
  border: none;
  background: transparent;
  border-radius: 8px;
}

.freedom-text {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0.5px;
  white-space: nowrap;
  color: #65bfff;
}

.freedom-s {
  color: #e74c3c;
  font-weight: 900;
}
</style>
