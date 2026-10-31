<script setup>
import { nextTick, onMounted } from "vue";
import { useRouter } from "vue-router";
import { showDrawer } from "../stores/cartStore";

const router = useRouter();

onMounted(async () => {
  // /cart là lối vào tương thích cho những liên kết cũ. Về trang chủ trước,
  // sau đó mới mở drawer để watcher đổi route không đóng giỏ ngay lập tức.
  if (router.currentRoute.value.path === "/cart") {
    await router.replace("/");
    await nextTick();
  }
  showDrawer();
});
</script>

<template>
  <div class="cart-redirect-page" aria-hidden="true"></div>
</template>

<style scoped>
.cart-redirect-page {
  min-height: 1px;
  background: #fff;
}
</style>
