import { computed, ref, watch } from "vue";
import type { CartItem, Product } from "../types";

function readCart(): CartItem[] {
  try {
    return JSON.parse(localStorage.getItem("cart") || "[]") as CartItem[];
  } catch {
    return [];
  }
}
const items = ref<CartItem[]>(readCart());
watch(items, (value) => localStorage.setItem("cart", JSON.stringify(value)), {
  deep: true,
});

export function useCart() {
  const count = computed(() =>
    items.value.reduce((sum, item) => sum + item.quantity, 0),
  );
  const total = computed(() =>
    items.value.reduce((sum, item) => sum + item.price * item.quantity, 0),
  );

  function add(product: Product, size: number, quantity = 1) {
    const existing = items.value.find(
      (item) => item.product_id === product.id && item.size === size,
    );
    if (existing) existing.quantity += quantity;
    else
      items.value.push({
        product_id: product.id,
        name: product.name,
        price: product.price,
        image: product.image,
        quantity,
        size,
      });
  }
  function updateQuantity(
    productId: number | string,
    size: number,
    quantity: number,
  ) {
    const item = items.value.find(
      (entry) => entry.product_id === productId && entry.size === size,
    );
    if (!item) return;
    if (quantity <= 0) remove(productId, size);
    else item.quantity = quantity;
  }
  function remove(productId: number | string, size: number) {
    items.value = items.value.filter(
      (item) => !(item.product_id === productId && item.size === size),
    );
  }
  function clear() {
    items.value = [];
  }
  return { items, count, total, add, updateQuantity, remove, clear };
}
