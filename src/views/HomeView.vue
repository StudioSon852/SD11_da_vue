<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../services/api";
import type { Product } from "../types";
import { addToCart, cartCount, showDrawer } from "../stores/cartStore.js";
import { notify } from "../stores/uiStore.js";
import fallbackShoe from "../../img/hero-sneakers.jpg";

const route = useRoute();
const router = useRouter();
const products = ref<Product[]>([]);
const loading = ref(false);
const loadError = ref("");
const activeCategory = ref("Tất cả");
const categories = computed(() => ["Tất cả", ...new Set(products.value.map((p) => String(p.category || "").trim()).filter(Boolean))]);

function normalizeProduct(p: Record<string, unknown>): Product {
  const id = (p.id ?? p.product_id ?? p.ProductID ?? p.id_product ?? "") as number | string;
  const rawImage = p.image_url ?? p.image ?? p.ImageURL ?? p.thumbnail ?? "";
  let imageUrl = typeof rawImage === "string" ? rawImage : "";
  if (imageUrl.includes("<a")) imageUrl = imageUrl.match(/href="([^"]+)"/)?.[1] || "";
  const price = Number(p.price ?? p.BasePrice ?? 0);
  const oldPrice = p.old_price == null ? undefined : Number(p.old_price);
  const rawSizes = Array.isArray(p.sizes) ? p.sizes : [];
  return {
    ...p,
    id,
    id_product: id,
    name: String(p.name ?? p.product_name ?? p.ProductName ?? "Sản phẩm"),
    product_name: String(p.name ?? p.product_name ?? p.ProductName ?? "Sản phẩm"),
    brand: String(p.brand ?? p.brand_name ?? p.BrandName ?? "ShoeGroup"),
    category: String(p.category ?? p.category_name ?? p.CategoryName ?? "Thời trang"),
    price,
    old_price: oldPrice,
    image: imageUrl,
    image_url: imageUrl,
    description: String(p.description ?? p.Description ?? "Giày chất lượng, đồng hành cùng phong cách hằng ngày."),
    sizes: rawSizes.map((s) => Number(typeof s === "object" && s !== null ? ((s as Record<string, unknown>).size ?? (s as Record<string, unknown>).name) : s)).filter((n) => Number.isFinite(n) && n > 0),
    badge: p.badge ? String(p.badge) : (p.is_featured ? "NỔI BẬT" : ""),
    sold_count: Math.max(0, Number(p.sold_count ?? p.soldCount ?? 0)),
  } as Product;
}

async function loadProducts() {
  loading.value = true;
  loadError.value = "";
  try {
    const response = await api.get("/products");
    const rows = Array.isArray(response) ? response : ((response as Record<string, unknown>)?.products ?? (response as Record<string, unknown>)?.data ?? []);
    products.value = Array.isArray(rows) ? rows.map((p) => normalizeProduct(p as Record<string, unknown>)) : [];
    if (!products.value.length) loadError.value = "Chưa có sản phẩm đang kinh doanh trong cơ sở dữ liệu.";
  } catch (error) {
    console.error("Không tải được sản phẩm:", error);
    products.value = [];
    const apiError = error as { message?: string; data?: { message?: string } };
    const message = apiError.data?.message || apiError.message || "";
    loadError.value = /failed to fetch|network|load failed|econnrefused|proxy error/i.test(message)
      ? "Chưa kết nối được Spring Boot. Hãy chạy npm run dev:api và mở http://localhost:8080/api/health để xem lỗi kết nối SQL Server."
      : (message || "Không tải được sản phẩm. Mở http://localhost:8080/api/health để kiểm tra backend và SQL Server.");
  } finally {
    loading.value = false;
  }
}

const filteredProducts = computed(() => {
  const q = String(route.query.q || "").toLowerCase().trim();
  const selectedCategory = String(route.query.category || activeCategory.value);
  return products.value.filter((p) => {
    const category = String(p.category || "").toLowerCase();
    const haystack = `${p.name} ${p.brand} ${p.category}`.toLowerCase();
    return (selectedCategory === "Tất cả" || category === selectedCategory.toLowerCase()) && (!q || haystack.includes(q));
  });
});
watch(() => route.query.category, (value) => { activeCategory.value = value ? String(value) : "Tất cả"; }, { immediate: true });
onMounted(loadProducts);

function money(value: number) {
  return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(value);
}
async function addProduct(product: Product) {
  const size = product.sizes?.[0];
  if (size == null) {
    await router.push(`/product/${product.id}`);
    return;
  }
  const result = await addToCart({
    product: { ...product, id_product: product.id, product_name: product.name, image_url: product.image, price: product.price },
    quantity: 1,
    size: { size_name: String(size) },
    color: { color_label: "Tiêu chuẩn", color_name: "Tiêu chuẩn", image: product.image },
    variantId: null,
  });
  if (!result.ok) {
    notify({ type: "error", title: "Chưa thêm được vào giỏ", message: result.message });
    return;
  }
  notify({ type: "success", title: "Đã thêm vào giỏ hàng", message: product.name, duration: 2200 });
  showDrawer();
}
function chooseCategory(category: string) {
  activeCategory.value = category;
  router.replace({ name: "home", query: { ...(route.query.q ? { q: route.query.q } : {}), ...(category !== "Tất cả" ? { category } : {}) } });
}
</script>

<template>
  <div class="container home-content">
    <section class="hero">
      <div class="hero-copy">
        <span class="eyebrow">BỘ SƯU TẬP MỚI NĂM 2026</span>
        <h1>Mỗi bước chân<br />là một <em>sự tự do.</em></h1>
        <p>Khám phá những đôi giày đồng hành cùng phong cách và nhịp sống của bạn.</p>
        <a href="#products" class="primary-button">KHÁM PHÁ NGAY <span>→</span></a>
        <div class="hero-note"><span>✓</span> Sản phẩm chọn lọc <span>✓</span> Hỗ trợ đổi size</div>
      </div>
      <div class="hero-art">
        <div class="hero-circle"></div>
        <img src="/img/hero-sneakers.jpg" alt="Bộ sưu tập giày thể thao" @error="($event.target as HTMLImageElement).src = fallbackShoe" />
        <div class="floating-tag"><strong>NEW DROP</strong><span>Fresh looks. Fresh steps.</span></div>
      </div>
    </section>

    <section class="benefits" aria-label="Dịch vụ cửa hàng">
      <div><span class="benefit-icon">↗</span><div><strong>Giao hàng toàn quốc</strong><small>Đóng gói cẩn thận</small></div></div>
      <div><span class="benefit-icon">◇</span><div><strong>Đổi size linh hoạt</strong><small>Chọn đúng kích thước vừa chân</small></div></div>
      <div><span class="benefit-icon">✓</span><div><strong>Thanh toán ổn định</strong><small>Hỗ trợ khách hàng tận tình</small></div></div>
    </section>

    <section id="products" class="products-section">
      <div class="section-heading"><div><span class="eyebrow">DÀNH RIÊNG CHO BẠN</span><h2>Mặt hàng nổi bật</h2></div><span class="product-count">{{ filteredProducts.length }} sản phẩm</span></div>
      <div class="category-tabs">
        <button v-for="category in categories" :key="category" type="button" :class="{ active: activeCategory === category && !route.query.category }" @click="chooseCategory(category)">{{ category }}</button>
      </div>
      <p v-if="loading" class="notice">Đang tải danh sách sản phẩm...</p>
      <p v-else-if="loadError" class="notice">{{ loadError }}</p>
      <div v-if="filteredProducts.length" class="product-grid">
        <article v-for="product in filteredProducts" :key="product.id" class="product-card">
          <RouterLink :to="`/product/${product.id}`" class="product-image-wrap">
            <span v-if="product.badge" class="product-badge">{{ product.badge }}</span>
            <img :src="product.image || fallbackShoe" :alt="product.name" loading="lazy" @error="($event.target as HTMLImageElement).src = fallbackShoe" />
            <span class="sold-count">Đã bán {{ product.sold_count ?? 0 }}</span>
            <span class="quick-view">Xem chi tiết →</span>
          </RouterLink>
          <div class="product-info">
            <div class="product-brand">{{ product.brand }} <span aria-label="Đánh giá nổi bật">★★★★★</span></div>
            <RouterLink :to="`/product/${product.id}`" class="product-name">{{ product.name }}</RouterLink>
            <div class="price-line"><strong>{{ money(product.price) }}</strong><del v-if="product.old_price && product.old_price > product.price">{{ money(product.old_price) }}</del></div>
            <div class="product-buttons"><RouterLink :to="`/product/${product.id}`" class="card-action">Chọn size <span>＋</span></RouterLink><button type="button" class="add-cart-button" @click="addProduct(product)">THÊM VÀO GIỎ</button></div>
          </div>
        </article>
      </div>
      <div v-else-if="!loading && !loadError" class="empty-state">Không tìm thấy sản phẩm phù hợp. Hãy thử từ khóa khác.</div>
      <div class="cart-shortcut"><RouterLink to="/cart" class="primary-button">XEM GIỎ HÀNG ({{ cartCount }}) →</RouterLink></div>
    </section>

    <section class="bottom-promo"><div><span class="eyebrow">YOUR EVERYDAY ESSENTIAL</span><h2>Thoải mái chuyển động.<br /><em>Tự tin thể hiện.</em></h2><p>Tìm đôi giày phù hợp với phong cách của bạn.</p></div><RouterLink to="/signup" class="light-button">TẠO TÀI KHOẢN →</RouterLink></section>
  </div>
</template>

<style scoped>
.home-content{max-width:1240px;margin:0 auto;padding:28px 24px 58px;color:#17324d}
.eyebrow{font-size:10px;letter-spacing:.18em;font-weight:800;color:#54748f}
.hero{min-height:445px;display:grid;grid-template-columns:1fr 1fr;align-items:center;gap:28px;padding:42px 3.5% 48px;background:linear-gradient(125deg,#f4faff 0%,#e1f1ff 100%);border:1px solid #d8eafa;border-radius:16px;overflow:hidden}
.hero-copy h1{margin:18px 0;font-family:Fraunces,serif;font-size:clamp(38px,4vw,62px);line-height:.99;font-weight:600;letter-spacing:-.045em;color:#17324d}
.hero-copy h1 em,.bottom-promo h2 em{font-style:italic;font-weight:500;color:#2d8bc5}
.hero-copy p{max-width:400px;color:#526c83;line-height:1.75;margin-bottom:25px}
.primary-button,.light-button{display:inline-flex;align-items:center;justify-content:center;gap:14px;padding:14px 20px;border:1px solid #216b9d;border-radius:6px;background:#216b9d;color:#fff;text-decoration:none;font-size:11px;font-weight:800;letter-spacing:.07em;cursor:pointer;transition:background .2s,transform .2s}
.primary-button:hover{background:#174e77;transform:translateY(-1px)}
.hero-note{display:flex;gap:10px;align-items:center;margin-top:28px;font-size:11px;color:#526c83}.hero-note span{color:#2785bd;font-weight:900}
.hero-art{position:relative;min-height:330px;display:flex;align-items:center;justify-content:center}
.hero-circle{position:absolute;width:min(31vw,355px);aspect-ratio:1;border-radius:50%;background:radial-gradient(circle,#d2ecff,#9ed2f4)}
.hero-art img{position:relative;z-index:1;width:100%;max-height:375px;object-fit:contain;mix-blend-mode:multiply;transform:rotate(-8deg);filter:drop-shadow(0 24px 25px #1c5e8830)}
.floating-tag{position:absolute;z-index:2;right:0;bottom:25px;background:#fff;padding:13px 17px;border:1px solid #e1eef8;border-radius:8px;box-shadow:0 8px 24px #23577916;display:flex;flex-direction:column;gap:4px}.floating-tag strong{font-size:10px;letter-spacing:.12em;color:#17324d}.floating-tag span{font-size:10px;color:#6d859a}
.benefits{display:grid;grid-template-columns:repeat(3,1fr);padding:26px 0;border-bottom:1px solid #dce8f2;gap:22px}.benefits>div{display:flex;align-items:center;justify-content:center;gap:12px}.benefit-icon{width:38px;height:38px;border:1px solid #d4e8f7;background:#f3faff;color:#287fb5;display:grid;place-items:center;border-radius:50%;font-size:19px}.benefits strong,.benefits small{display:block}.benefits strong{font-size:12px;color:#17324d}.benefits small{font-size:10px;color:#7a91a5;margin-top:4px}
.products-section{padding-top:45px}.section-heading{display:flex;justify-content:space-between;align-items:end;margin-bottom:22px}.section-heading h2{font-family:Fraunces,serif;font-size:34px;letter-spacing:-.04em;margin:8px 0 0;font-weight:600;color:#17324d}.product-count{font-size:11px;color:#6e879c}
.category-tabs{display:flex;gap:8px;overflow:auto;margin-bottom:22px;padding-bottom:4px}.category-tabs button{padding:9px 15px;border:1px solid #d6e4ef;background:#fff;border-radius:30px;white-space:nowrap;color:#526c83;font-size:11px;cursor:pointer;transition:all .18s}.category-tabs button.active,.category-tabs button:hover{background:#216b9d;color:#fff;border-color:#216b9d}
.product-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:20px}.product-card{border:1px solid #e0eaf2;border-radius:9px;overflow:hidden;background:#fff;transition:transform .2s,box-shadow .2s,border-color .2s}.product-card:hover{transform:translateY(-3px);border-color:#beddf3;box-shadow:0 12px 28px #205d8011}.product-image-wrap{position:relative;display:block;aspect-ratio:1/1;background:#f1f8fd;overflow:hidden}.product-image-wrap>img{width:100%;height:100%;object-fit:cover;transition:transform .35s}.product-card:hover .product-image-wrap>img{transform:scale(1.04)}.product-badge{position:absolute;left:10px;top:10px;z-index:2;background:#216b9d;color:#fff;padding:5px 7px;border-radius:3px;font-size:9px;letter-spacing:.06em}.sold-count{position:absolute;left:9px;bottom:9px;z-index:2;color:#fff;background:rgba(23,50,77,.78);border-radius:4px;padding:4px 7px;font-size:10px;font-weight:600;backdrop-filter:blur(3px)}.quick-view{position:absolute;right:9px;bottom:9px;background:#fff;color:#17324d;border:1px solid #dbeaf5;border-radius:4px;padding:5px 7px;font-size:9px;opacity:0;transition:opacity .2s}.product-card:hover .quick-view{opacity:1}
.product-info{padding:13px}.product-brand{display:flex;justify-content:space-between;color:#71879a;font-size:10px;margin-bottom:8px}.product-brand span{color:#f1a92f;letter-spacing:1px}.product-name{display:block;min-height:38px;text-decoration:none;color:#17324d;font-size:12px;font-weight:700;line-height:1.5}.product-name:hover{color:#247fb7}.price-line{display:flex;align-items:center;gap:8px;margin-top:8px}.price-line strong{font-size:13px;color:#1f6f9f}.price-line del{font-size:10px;color:#9aacba}.product-buttons{display:flex;flex-direction:column;gap:7px;margin-top:13px}.card-action{display:flex;justify-content:space-between;align-items:center;text-decoration:none;border:1px solid #d6e4ef;border-radius:5px;padding:9px 10px;color:#254b68;font-size:10px}.card-action:hover{border-color:#7dbce4;background:#f4faff}.add-cart-button{width:100%;border:1px solid #216b9d;border-radius:5px;background:#216b9d;color:#fff;padding:10px;font-size:10px;font-weight:800;letter-spacing:.04em;cursor:pointer}.add-cart-button:hover{background:#174e77;border-color:#174e77}
.notice,.empty-state{text-align:center;padding:30px;color:#617a90;font-size:13px}.cart-shortcut{display:flex;justify-content:center;margin-top:28px}.bottom-promo{margin-top:54px;padding:35px 40px;background:linear-gradient(115deg,#17324d,#216b9d);color:white;display:flex;align-items:center;justify-content:space-between;gap:24px;border-radius:12px;box-shadow:0 12px 30px #17324d16}.bottom-promo .eyebrow{color:#d3eaff}.bottom-promo h2{color:white;font-family:Fraunces,serif;font-size:32px;font-weight:500;margin:10px 0}.bottom-promo h2 em{color:#9edbff}.bottom-promo p{color:#d3e4f1;font-size:12px}.light-button{background:#fff;color:#185782;border-color:#fff;white-space:nowrap}.light-button:hover{background:#eaf6ff;transform:translateY(-1px)}
@media(max-width:900px){.product-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.hero{grid-template-columns:1fr;gap:0}.hero-art{min-height:250px}.hero-art img{max-height:300px}.hero-circle{width:250px}.benefits{gap:12px}.benefits>div{justify-content:flex-start}}
@media(max-width:620px){.home-content{padding:16px 14px 34px}.hero{padding:28px 22px 10px;min-height:0}.hero-copy h1{font-size:42px}.hero-art{min-height:225px}.floating-tag{bottom:8px;right:0}.benefits{grid-template-columns:1fr;padding:18px 4px}.benefits>div{justify-content:flex-start}.section-heading h2{font-size:28px}.product-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.product-info{padding:10px}.product-name{font-size:11px}.bottom-promo{padding:26px 22px;align-items:flex-start;flex-direction:column}.bottom-promo h2{font-size:28px}}
</style>
