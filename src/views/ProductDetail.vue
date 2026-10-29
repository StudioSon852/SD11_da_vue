<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import FigmaProductCard from "../components/figma/product/FigmaProductCard.vue";
import FigmaProductGrid from "../components/figma/product/FigmaProductGrid.vue";
import { api } from "../services/apiClient";
import {
  addToCart,
  cartCount,
  formatCurrency,
  showDrawer,
} from "../stores/cartStore";
import { notify } from "../stores/uiStore";
import fallbackProductImage from "../../img/hero-sneakers.jpg";

const route = useRoute();
const router = useRouter();

const product = ref(null);
const relatedProducts = ref([]);
const isLoading = ref(true);
const activeImage = ref("");
const selectedColor = ref(null);
const selectedSize = ref(null);
const quantity = ref(1);

// Chuẩn hóa dữ liệu sản phẩm từ API.
const normalizeProduct = (raw) => ({
  ...raw,

  id_product: raw.id_product ?? raw.id ?? raw.product_id ?? raw.ProductID,

  product_name: raw.product_name ?? raw.name ?? raw.ProductName ?? "",

  category_name: raw.category_name ?? raw.category ?? "",

  id_category: raw.id_category ?? raw.category_id,

  brand_name: raw.brand_name ?? raw.brand ?? raw.BrandName ?? "",

  image_url: raw.image_url ?? raw.image ?? raw.ImageURL ?? "",

  variants: Array.isArray(raw.variants)
    ? raw.variants.map((variant) => ({
        ...variant,

        id:
          variant.id ??
          variant.variant_id ??
          variant.id_variant ??
          variant.VariantID ??
          null,

        size: String(
          variant.size ?? variant.size_name ?? variant.SizeName ?? "",
        ),

        color: String(
          variant.color ??
            variant.color_name ??
            variant.color_label ??
            variant.ColorName ??
            "",
        ),
      }))
    : [],

  colors: Array.isArray(raw.colors) ? raw.colors : [],

  sizes: Array.isArray(raw.sizes) ? raw.sizes : [],
});

// Danh sách màu, lấy từ dữ liệu API hoặc biến thể.
const colorOptions = computed(() => {
  if (!product.value) return [];

  const fromApi = product.value.colors
    .map((color) => ({
      name: String(
        color.name ??
          color.color_name ??
          color.color_label ??
          color.ColorName ??
          "",
      ),

      label: String(
        color.name ??
          color.color_label ??
          color.color_name ??
          color.ColorName ??
          "",
      ),

      hex: color.hex ?? color.color_hex ?? color.ColorHex ?? "#d4d4d4",

      image:
        color.image ??
        color.image_url ??
        color.ImageURL ??
        product.value.image_url,
    }))
    .filter((color) => color.name);

  if (fromApi.length) return fromApi;

  const options = new Map();

  for (const variant of product.value.variants) {
    if (!variant.color || options.has(variant.color)) {
      continue;
    }

    options.set(variant.color, {
      name: variant.color,
      label: variant.color,

      hex: variant.hex ?? variant.color_hex ?? "#d4d4d4",

      image: variant.image ?? variant.image_url ?? product.value.image_url,
    });
  }

  return [...options.values()];
});

// Danh sách size của sản phẩm.
const allSizes = computed(() => {
  if (!product.value) return [];

  const variantSizes = product.value.variants
    .map((variant) => variant.size)
    .filter(Boolean);

  if (variantSizes.length) {
    return [...new Set(variantSizes)];
  }

  return product.value.sizes
    .map((size) => String(size?.size_name ?? size?.name ?? size))
    .filter(Boolean);
});

// Size khả dụng ứng với màu đang chọn.
const availableSizes = computed(() => {
  if (!product.value?.variants.length || !selectedColor.value) {
    return allSizes.value;
  }

  return [
    ...new Set(
      product.value.variants
        .filter((variant) => variant.color === selectedColor.value.name)
        .map((variant) => variant.size)
        .filter(Boolean),
    ),
  ];
});

// Tìm đúng biến thể theo màu và size.
const selectedVariant = computed(() => {
  if (!product.value?.variants.length) {
    return null;
  }

  return (
    product.value.variants.find(
      (variant) =>
        variant.color === selectedColor.value?.name &&
        variant.size === String(selectedSize.value),
    ) || null
  );
});

// Ảnh sản phẩm và các ảnh biến thể.
const galleryImages = computed(() => {
  if (!product.value) return [];

  return [
    ...new Set(
      [
        product.value.image_url,
        ...colorOptions.value.map((color) => color.image),
        ...product.value.variants.map(
          (variant) => variant.image ?? variant.image_url,
        ),
      ].filter(Boolean),
    ),
  ].slice(0, 4);
});

// Giá và khuyến mại.
const hasDiscount = computed(() => {
  const regular =
    Number(selectedVariant.value?.price ?? product.value?.price) || 0;

  const sale =
    Number(selectedVariant.value?.sale_price ?? product.value?.sale_price) || 0;

  return sale > 0 && sale < regular;
});

const regularPrice = computed(
  () => Number(selectedVariant.value?.price ?? product.value?.price) || 0,
);

const displayPrice = computed(() =>
  hasDiscount.value
    ? Number(selectedVariant.value?.sale_price ?? product.value?.sale_price)
    : regularPrice.value,
);

const attributes = computed(() =>
  [
    ["Thương hiệu", product.value?.brand_name],
    ["Danh mục", product.value?.category_name],
    ["Bộ môn", product.value?.sport],
    ["Chất liệu", product.value?.material_name],
    ["Bộ sưu tập", product.value?.collection_name],
  ].filter(([, value]) => value),
);

const trustItems = [
  {
    title: "Chính hãng 100%",
    sub: "Cam kết hoàn tiền",
    icon: "shield",
  },
  {
    title: "Giao hàng toàn quốc",
    sub: "Theo dõi đơn dễ dàng",
    icon: "truck",
  },
  {
    title: "Yêu cầu trả hàng",
    sub: "Trong 14 ngày từ khi nhận",
    icon: "return",
  },
];

// Chọn màu.
const selectColor = (color) => {
  selectedColor.value = color;

  activeImage.value = color.image || product.value?.image_url || "";

  selectedSize.value = availableSizes.value[0] ?? null;

  quantity.value = 1;
};

// Chọn size.
const selectSize = (size) => {
  selectedSize.value = size;
  quantity.value = 1;
};

// Giới hạn số lượng trong một biến thể tối đa 99.
const incrementQuantity = () => {
  quantity.value = Math.min(99, quantity.value + 1);
};

// Đóng gói chính xác sản phẩm, biến thể và số lượng
// trước khi đưa vào cartStore.
const buildCartPayload = () => ({
  product: {
    ...product.value,

    price: selectedVariant.value?.price ?? product.value.price,

    sale_price: selectedVariant.value?.sale_price ?? product.value.sale_price,
  },

  quantity: quantity.value,

  size: {
    size_name: String(selectedSize.value ?? ""),
  },

  color: {
    color_name: selectedColor.value?.name ?? "Tiêu chuẩn",

    color_label: selectedColor.value?.label ?? "Tiêu chuẩn",

    color_hex: selectedColor.value?.hex ?? "",

    image: selectedColor.value?.image ?? product.value.image_url,
  },

  variantId: selectedVariant.value?.id ?? null,
});

/**
 * Thêm sản phẩm đang chọn vào giỏ hàng.
 *
 * openCart = true:
 *   Mở khung giỏ hàng sau khi thêm thành công.
 *
 * openCart = false:
 *   Không mở khung giỏ hàng, để dùng cho nút Mua ngay.
 */
const addCurrentSelection = async ({ openCart = true } = {}) => {
  if (!product.value) {
    notify({
      type: "warning",
      message: "Chưa có thông tin sản phẩm.",
    });
    return false;
  }

  if (colorOptions.value.length && !selectedColor.value) {
    notify({
      type: "warning",
      message: "Vui lòng chọn màu sắc.",
    });
    return false;
  }

  if (allSizes.value.length && !selectedSize.value) {
    notify({
      type: "warning",
      message: "Vui lòng chọn kích cỡ.",
    });
    return false;
  }

  if (product.value.variants.length && !selectedVariant.value) {
    notify({
      type: "warning",
      message: "Biến thể đã chọn không tồn tại.",
    });
    return false;
  }

  if (
    !Number.isInteger(quantity.value) ||
    quantity.value < 1 ||
    quantity.value > 99
  ) {
    notify({
      type: "warning",
      message: "Số lượng phải từ 1 đến 99.",
    });
    return false;
  }

  const result = await addToCart(buildCartPayload());

  if (!result.ok) {
    notify({
      type: "error",
      message: result.message,
    });
    return false;
  }

  if (openCart) {
    showDrawer();
  }

  notify({
    type: "success",
    title: "Đã thêm vào giỏ hàng",
    message: product.value.product_name,
    duration: 2500,
  });

  quantity.value = 1;
  return true;
};

/**
 * MUA NGAY:
 * 1. Thêm đúng sản phẩm/màu/size đang chọn vào cartStore.
 * 2. Giữ các mặt hàng đã thêm trước đó trong giỏ.
 * 3. Kiểm tra có ít nhất một sản phẩm.
 * 4. Chuyển tới trang thanh toán online /checkout.
 */
const buyNow = async () => {
  const added = await addCurrentSelection({
    openCart: false,
  });

  // Không đi tiếp nếu chưa thêm sản phẩm thành công.
  if (!added) return;

  // Giỏ hàng phải có ít nhất một sản phẩm.
  if (cartCount.value < 1) {
    notify({
      type: "warning",
      title: "Giỏ hàng đang trống",
      message: "Vui lòng chọn ít nhất một sản phẩm trước khi thanh toán.",
    });
    return;
  }

  await router.push("/checkout");
};

// Ảnh dự phòng.
const onImageError = (event) => {
  if (event.target.dataset.fallbackApplied) {
    return;
  }

  event.target.dataset.fallbackApplied = "true";
  event.target.src = fallbackProductImage;
};

// Đọc sản phẩm từ API.
const fetchData = async () => {
  isLoading.value = true;
  product.value = null;
  relatedProducts.value = [];

  try {
    const response = await api.get("/products");

    const rows = Array.isArray(response)
      ? response
      : Array.isArray(response?.data)
        ? response.data
        : Array.isArray(response?.products)
          ? response.products
          : Array.isArray(response?.items)
            ? response.items
            : Array.isArray(response?.data?.items)
              ? response.data.items
              : [];

    const id = Number(route.params.id);

    const raw = rows.find(
      (item) =>
        Number(
          item.id ?? item.id_product ?? item.product_id ?? item.ProductID,
        ) === id,
    );

    if (!raw) return;

    product.value = normalizeProduct(raw);

    relatedProducts.value = rows
      .filter(
        (item) =>
          Number(
            item.id ?? item.id_product ?? item.product_id ?? item.ProductID,
          ) !== id,
      )
      .filter((item) => {
        const categoryId = item.category_id ?? item.id_category;

        return product.value.id_category != null
          ? String(categoryId) === String(product.value.id_category)
          : String(item.category ?? item.category_name) ===
              product.value.category_name;
      })
      .filter(
        (item) =>
          item.active !== false && item.active !== 0 && item.active !== "0",
      )
      .map(normalizeProduct)
      .slice(0, 5);

    const firstColor = colorOptions.value[0] ?? null;

    if (firstColor) {
      selectColor(firstColor);
    } else {
      activeImage.value = product.value.image_url;

      selectedSize.value = availableSizes.value[0] ?? null;
    }
  } catch (error) {
    console.error("Lỗi tải sản phẩm:", error);

    notify({
      type: "error",
      message: "Không thể tải thông tin sản phẩm.",
    });
  } finally {
    isLoading.value = false;
  }
};

watch(() => route.params.id, fetchData);

onMounted(fetchData);
</script>

<template>
  <div class="min-h-screen bg-white text-[#0E0E0E]">
    <div
      v-if="isLoading"
      class="flex min-h-[520px] items-center justify-center"
      aria-live="polite"
    >
      <span class="sg-spinner" aria-hidden="true"></span>

      <span class="ml-3 text-sm text-[#737373]"> Đang tải sản phẩm... </span>
    </div>

    <div
      v-else-if="product"
      class="mx-auto w-full max-w-[1280px] px-5 pb-20 pt-6 sm:px-6 lg:px-10 lg:pt-8"
    >
      <nav
        class="mb-6 flex min-w-0 items-center gap-2 text-xs text-[#737373]"
        aria-label="Đường dẫn"
      >
        <router-link
          to="/"
          class="shrink-0 transition-colors hover:text-[#0E0E0E]"
        >
          Trang chủ
        </router-link>

        <span aria-hidden="true">/</span>

        <router-link
          to="/products"
          class="shrink-0 transition-colors hover:text-[#0E0E0E]"
        >
          Sản phẩm
        </router-link>

        <span aria-hidden="true">/</span>

        <span class="truncate text-[#0E0E0E]">
          {{ product.product_name }}
        </span>
      </nav>

      <div class="grid grid-cols-1 items-start gap-8 lg:grid-cols-2 lg:gap-14">
        <!-- Hình ảnh sản phẩm -->
        <section class="lg:sticky lg:top-[92px]" aria-label="Hình ảnh sản phẩm">
          <div
            class="relative aspect-square overflow-hidden rounded-2xl bg-[#F0F0F0]"
          >
            <span
              v-if="product.sport"
              class="absolute left-4 top-4 z-10 rounded bg-white px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.14em]"
            >
              {{ product.sport }}
            </span>

            <img
              :src="activeImage || product.image_url"
              :alt="product.product_name"
              class="h-full w-full object-cover"
              @error="onImageError"
            />
          </div>

          <div
            v-if="galleryImages.length > 1"
            class="mt-3 grid grid-cols-4 gap-3"
          >
            <button
              v-for="(image, index) in galleryImages"
              :key="image"
              type="button"
              class="aspect-square overflow-hidden rounded-xl border-2 bg-[#F0F0F0] p-0 transition-colors"
              :class="
                activeImage === image
                  ? 'border-[#0E0E0E]'
                  : 'border-transparent hover:border-[#D4D4D4]'
              "
              :aria-label="`Xem ảnh ${index + 1}`"
              @click="activeImage = image"
            >
              <img
                :src="image"
                :alt="`Ảnh ${index + 1} của ${product.product_name}`"
                class="h-full w-full object-cover"
                @error="onImageError"
              />
            </button>
          </div>
        </section>

        <!-- Thông tin và thao tác mua hàng -->
        <section>
          <div class="mb-3 flex flex-wrap items-center gap-2.5">
            <span
              class="text-[11px] font-bold uppercase tracking-[0.15em] text-[#737373]"
            >
              {{ product.brand_name || "ShoeGroup" }}
            </span>

            <span
              v-if="product.is_featured"
              class="rounded bg-[#0E0E0E] px-2 py-0.5 text-[10px] font-bold uppercase text-white"
            >
              Nổi bật
            </span>

            <span
              v-if="hasDiscount"
              class="rounded bg-[#F0F0F0] px-2 py-0.5 text-[10px] font-bold uppercase"
            >
              Sale
            </span>
          </div>

          <h1
            class="figma-display mb-3 text-3xl font-semibold leading-tight text-[#0E0E0E] md:text-4xl"
          >
            {{ product.product_name }}
          </h1>

          <div class="mb-5 flex items-center gap-2">
            <div class="flex items-center gap-0.5" aria-label="4 trên 5 sao">
              <svg
                v-for="star in 5"
                :key="star"
                width="15"
                height="15"
                viewBox="0 0 24 24"
                :fill="star <= 4 ? 'currentColor' : 'none'"
                stroke="currentColor"
                stroke-width="1.5"
              >
                <path
                  d="M12 2l2.9 6.3 6.9.7-5.1 4.6 1.4 6.8L12 17.8 5.9 20.4l1.4-6.8L2.2 9l6.9-.7z"
                />
              </svg>
            </div>

            <span class="text-xs text-[#737373]">
              4.0 · Đánh giá sản phẩm
            </span>
          </div>

          <!-- Giá -->
          <div class="mb-6 flex flex-wrap items-baseline gap-3">
            <span class="figma-display text-3xl font-semibold">
              {{ formatCurrency(displayPrice) }}
            </span>

            <span
              v-if="hasDiscount"
              class="text-base text-[#737373] line-through"
            >
              {{ formatCurrency(regularPrice) }}
            </span>
          </div>

          <p class="mb-6 text-sm leading-relaxed text-[#737373]">
            {{
              product.description ||
              `${product.product_name} có thiết kế hiện đại, phù hợp cho tập luyện và phong cách hằng ngày.`
            }}
          </p>

          <!-- Thuộc tính -->
          <dl
            v-if="attributes.length"
            class="mb-7 grid grid-cols-2 gap-x-6 gap-y-3 border-y border-[#E5E5E5] py-5 text-[13px]"
          >
            <div
              v-for="[label, value] in attributes"
              :key="label"
              class="min-w-0"
            >
              <dt
                class="text-[10px] font-bold uppercase tracking-[0.12em] text-[#737373]"
              >
                {{ label }}
              </dt>

              <dd class="mt-1 truncate font-semibold">
                {{ value }}
              </dd>
            </div>
          </dl>

          <!-- Chọn màu -->
          <div v-if="colorOptions.length" class="mb-6">
            <div class="mb-2.5 text-[13px] font-semibold">
              Màu sắc:

              <span class="font-normal text-[#737373]">
                {{ selectedColor?.label }}
              </span>
            </div>

            <div class="flex flex-wrap gap-2.5">
              <button
                v-for="color in colorOptions"
                :key="color.name"
                type="button"
                class="relative flex h-12 min-w-12 items-center justify-center overflow-hidden rounded-lg border-2 bg-white p-1 transition-colors"
                :class="
                  selectedColor?.name === color.name
                    ? 'border-[#0E0E0E]'
                    : 'border-[#E5E5E5] hover:border-[#737373]'
                "
                :title="color.label"
                @click="selectColor(color)"
              >
                <img
                  v-if="color.image"
                  :src="color.image"
                  :alt="color.label"
                  class="h-full w-full rounded object-cover"
                  @error="onImageError"
                />

                <span
                  v-else
                  class="h-7 w-7 rounded-full border border-black/10"
                  :style="{ backgroundColor: color.hex }"
                ></span>
              </button>
            </div>
          </div>

          <!-- Chọn size -->
          <div v-if="availableSizes.length" class="mb-6">
            <div class="mb-2.5 flex items-center justify-between gap-4">
              <span class="text-[13px] font-semibold"> Chọn size (UK) </span>

              <button
                type="button"
                class="border-0 bg-transparent p-0 text-xs text-[#737373] underline underline-offset-2 hover:text-[#0E0E0E]"
              >
                Hướng dẫn chọn size
              </button>
            </div>

            <div class="flex flex-wrap gap-2">
              <button
                v-for="size in availableSizes"
                :key="size"
                type="button"
                class="relative flex h-11 w-12 items-center justify-center rounded-lg border text-sm font-semibold transition-colors"
                :class="
                  selectedSize === size
                    ? 'border-[#0E0E0E] bg-[#0E0E0E] text-white'
                    : 'border-[#E5E5E5] bg-white text-[#0E0E0E] hover:border-[#0E0E0E]'
                "
                @click="selectSize(size)"
              >
                {{ size }}
              </button>
            </div>
          </div>

          <p v-if="!availableSizes.length" class="mb-3 text-xs text-[#657586]">
            Bạn vẫn có thể đặt hàng; cửa hàng sẽ xác nhận size/màu phù hợp khi
            tiếp nhận đơn.
          </p>

          <!-- Số lượng và Thêm vào giỏ -->
          <div class="mb-3 flex items-center gap-3">
            <div
              class="flex h-12 shrink-0 items-center rounded-lg border border-[#E5E5E5]"
            >
              <button
                type="button"
                aria-label="Giảm số lượng"
                class="flex h-full w-11 items-center justify-center rounded-l-lg border-0 bg-transparent hover:bg-[#F0F0F0]"
                @click="quantity = Math.max(1, quantity - 1)"
              >
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.4"
                  stroke-linecap="round"
                >
                  <path d="M5 12h14" />
                </svg>
              </button>

              <span class="w-10 text-center text-sm font-semibold">
                {{ quantity }}
              </span>

              <button
                type="button"
                aria-label="Tăng số lượng"
                class="flex h-full w-11 items-center justify-center rounded-r-lg border-0 bg-transparent hover:bg-[#F0F0F0]"
                :disabled="quantity >= 99"
                @click="incrementQuantity"
              >
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.4"
                  stroke-linecap="round"
                >
                  <path d="M12 5v14M5 12h14" />
                </svg>
              </button>
            </div>

            <button
              type="button"
              class="h-12 min-w-0 flex-1 rounded-lg border border-[#0E0E0E] bg-white px-4 text-[14px] font-semibold transition-colors hover:bg-[#F0F0F0]"
              @click="addCurrentSelection()"
            >
              Thêm vào giỏ
            </button>
          </div>

          <!-- Mua ngay -->
          <button
            type="button"
            class="mb-6 h-12 w-full rounded-lg border-0 bg-[#0E0E0E] text-[14px] font-semibold text-white transition-colors hover:bg-[#333333]"
            @click="buyNow"
          >
            Mua ngay
          </button>

          <!-- Cam kết dịch vụ -->
          <div class="space-y-3 border-t border-[#E5E5E5] pt-5">
            <div
              v-for="item in trustItems"
              :key="item.title"
              class="flex items-center gap-3"
            >
              <svg
                v-if="item.icon === 'shield'"
                width="17"
                height="17"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
                class="shrink-0"
              >
                <path d="M12 2 4 5v6c0 5 3.4 9 8 11 4.6-2 8-6 8-11V5z" />
                <path d="m9 12 2 2 4-4" />
              </svg>

              <svg
                v-else-if="item.icon === 'truck'"
                width="17"
                height="17"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
                class="shrink-0"
              >
                <path d="M1 3h15v13H1z" />
                <path d="M16 8h4l3 3v5h-7z" />
                <circle cx="5.5" cy="18.5" r="2.5" />
                <circle cx="18.5" cy="18.5" r="2.5" />
              </svg>

              <svg
                v-else
                width="17"
                height="17"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
                class="shrink-0"
              >
                <path d="M3 7v6h6" />
                <path d="M3 13a9 9 0 1 0 3-7.7L3 8" />
              </svg>

              <p class="text-[13px]">
                <span class="font-semibold">
                  {{ item.title }}
                </span>

                ·

                <span class="text-[#737373]">
                  {{ item.sub }}
                </span>
              </p>
            </div>
          </div>
        </section>
      </div>

      <!-- Sản phẩm liên quan -->
      <section
        v-if="relatedProducts.length"
        class="mt-16 border-t border-[#E5E5E5] pt-12"
      >
        <h2 class="figma-display mb-6 text-2xl font-semibold md:text-3xl">
          Sản phẩm liên quan
        </h2>

        <FigmaProductGrid :columns="5">
          <FigmaProductCard
            v-for="item in relatedProducts"
            :key="item.id_product"
            :product="item"
          />
        </FigmaProductGrid>
      </section>
    </div>

    <!-- Không tìm thấy sản phẩm -->
    <div
      v-else
      class="mx-auto flex min-h-[520px] max-w-xl flex-col items-center justify-center px-6 text-center"
    >
      <h1 class="figma-display text-3xl font-semibold">
        Không tìm thấy sản phẩm
      </h1>

      <p class="mt-3 text-sm text-[#737373]">
        Sản phẩm có thể đã ngừng bán hoặc đường dẫn không còn hợp lệ.
      </p>

      <router-link
        to="/products"
        class="mt-6 rounded-lg bg-[#0E0E0E] px-6 py-3 text-sm font-semibold text-white"
      >
        Xem sản phẩm
      </router-link>
    </div>
  </div>
</template>
