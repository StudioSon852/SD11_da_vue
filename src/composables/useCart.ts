
import { computed, ref, watch } from "vue";
import type { Product } from "../types";

export interface CartItem {
  product_id: number | string;
  name: string;
  price: number;
  image: string;
  quantity: number;
  size: number | string;
}

function loadCart(): CartItem[] {
  try {
    const saved = localStorage.getItem("cart");
    if (!saved) return [];

    const parsed: unknown = JSON.parse(saved);
    if (!Array.isArray(parsed)) return [];

    return parsed.filter(
      (item): item is CartItem =>
        item !== null &&
        typeof item === "object" &&
        "product_id" in item &&
        "name" in item &&
        "price" in item &&
        "quantity" in item &&
        "size" in item &&
        Number(item.quantity) > 0
    );
  } catch {
    return [];
  }
}

// State dùng chung toàn ứng dụng.
const items = ref<CartItem[]>(loadCart());

// Tự lưu giỏ hàng sau khi thêm, xóa hoặc đổi số lượng.
watch(
  items,
  (value) => {
    localStorage.setItem("cart", JSON.stringify(value));
  },
  { deep: true }
);

// Tổng số lượng sản phẩm.
const count = computed(() =>
  items.value.reduce((sum, item) => sum + item.quantity, 0)
);

// Tổng tiền.
const total = computed(() =>
  items.value.reduce(
    (sum, item) => sum + item.price * item.quantity,
    0
  )
);

// Thêm sản phẩm vào giỏ.
function add(
  product: Product,
  size: number | string,
  quantity = 1
) {
  const productId = product.id;

  if (
    productId == null ||
    !Number.isFinite(Number(product.price)) ||
    quantity <= 0
  ) {
    console.error("Sản phẩm không hợp lệ:", product);
    return;
  }

  const existing = items.value.find(
    (item) =>
      String(item.product_id) === String(productId) &&
      String(item.size) === String(size)
  );

  if (existing) {
    existing.quantity = Math.min(
      10,
      existing.quantity + Math.floor(quantity)
    );
    return;
  }

  items.value.push({
    product_id: productId,
    name: product.name,
    price: Number(product.price),
    image: product.image,
    quantity: Math.min(10, Math.max(1, Math.floor(quantity))),
    size,
  });
}

// Cập nhật số lượng.
// Nếu số lượng <= 0 thì tự động xóa sản phẩm.
function updateQuantity(
  productId: number | string,
  size: number | string,
  quantity: number
) {
  const item = items.value.find(
    (item) =>
      String(item.product_id) === String(productId) &&
      String(item.size) === String(size)
  );

  if (!item) return;

  if (quantity <= 0) {
    remove(productId, size);
    return;
  }

  item.quantity = Math.min(10, Math.floor(quantity));
}

// Xóa sản phẩm theo mã và size.
function remove(
  productId: number | string,
  size: number | string
) {
  items.value = items.value.filter(
    (item) =>
      !(
        String(item.product_id) === String(productId) &&
        String(item.size) === String(size)
      )
  );
}

// Xóa toàn bộ giỏ hàng.
function clearCart() {
  items.value = [];
}

export function useCart() {
  return {
    items,
    count,
    total,
    add,
    updateQuantity,
    remove,
    clearCart,
  };
}

