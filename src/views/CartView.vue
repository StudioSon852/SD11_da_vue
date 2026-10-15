```vue
<script setup lang="ts">
import { useCart } from "../composables/useCart";

const {
  items,
  count,
  total,
  updateQuantity,
  remove,
} = useCart();

const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);

// Giảm số lượng. Khi đang là 1 và bấm dấu trừ,
// gọi updateQuantity với 0 để tự động xóa sản phẩm.
function decreaseQuantity(
  productId: number | string,
  size: number | string,
  quantity: number,
) {
  updateQuantity(productId, size, quantity - 1);
}

// Xóa sản phẩm có xác nhận.
function removeItem(
  productId: number | string,
  size: number | string,
) {
  remove(productId, size);
}
</script>

<template>
  <main class="container inner-page">
    <div class="breadcrumbs">
      <RouterLink to="/">Trang chủ</RouterLink>
      / Giỏ hàng
    </div>

    <header class="section-heading">
      <div>
        <span class="eyebrow">YOUR SELECTION</span>
        <h1 class="page-title">Giỏ hàng của bạn</h1>
        <p>{{ count }} sản phẩm trong giỏ hàng</p>
      </div>

      <RouterLink to="/" class="text-link">
        ← Tiếp tục mua sắm
      </RouterLink>
    </header>

    <!-- Giỏ hàng có sản phẩm -->
    <section v-if="items.length > 0" class="cart-layout">
      <div class="cart-list">
        <article
          v-for="item in items"
          :key="`${item.product_id}-${item.size}`"
          class="cart-item"
        >
          <RouterLink
            :to="`/products/${item.product_id}`"
            class="cart-product-image"
          >
            <img :src="item.image" :alt="item.name" />
          </RouterLink>

          <div class="cart-item-info">
            <h3>{{ item.name }}</h3>

            <p>Size EU: {{ item.size }}</p>

            <strong class="item-price">
              {{ money(item.price) }}
            </strong>

            <button
              type="button"
              class="remove-link"
              @click="removeItem(item.product_id, item.size)"
            >
              <span aria-hidden="true">🗑</span>
              Xóa sản phẩm
            </button>
          </div>

          <!-- Điều chỉnh số lượng -->
          <div class="cart-quantity">
            <span class="field-label">Số lượng</span>

            <div class="quantity-control">
              <button
                type="button"
                aria-label="Giảm số lượng"
                @click="
                  decreaseQuantity(
                    item.product_id,
                    item.size,
                    item.quantity
                  )
                "
              >
                −
              </button>

              <span class="quantity-value">
                {{ item.quantity }}
              </span>

              <button
                type="button"
                aria-label="Tăng số lượng"
                :disabled="item.quantity >= 10"
                @click="
                  updateQuantity(
                    item.product_id,
                    item.size,
                    item.quantity + 1
                  )
                "
              >
                +
              </button>
            </div>

            <small class="quantity-hint" v-if="item.quantity === 1">
              Bấm − để xóa sản phẩm
            </small>
          </div>

          <!-- Thành tiền của từng sản phẩm -->
          <div class="cart-line-price">
            <span class="field-label">Thành tiền</span>

            <strong>
              {{ money(item.price * item.quantity) }}
            </strong>
          </div>
        </article>
      </div>

      <!-- Tổng kết đơn hàng -->
      <aside class="order-summary">
        <h2>Tóm tắt đơn hàng</h2>

        <div class="summary-line">
          <span>Số lượng sản phẩm</span>
          <span>{{ count }}</span>
        </div>

        <div class="summary-line">
          <span>Tạm tính</span>
          <span>{{ money(total) }}</span>
        </div>

        <div class="summary-line">
          <span>Phí vận chuyển</span>
          <span>Miễn phí</span>
        </div>

        <div class="summary-total">
          <span>Tổng cộng</span>
          <strong>{{ money(total) }}</strong>
        </div>

        <p class="summary-note">
          Phí vận chuyển đang được đặt bằng 0 đồng để demo.
        </p>

        <RouterLink
          to="/payment"
          class="primary-button full-button"
        >
          THANH TOÁN →
        </RouterLink>

        <RouterLink to="/" class="continue-shopping">
          ← Tiếp tục chọn sản phẩm
        </RouterLink>
      </aside>
    </section>

    <!-- Hiển thị khi không còn sản phẩm -->
    <section v-else class="empty-state">
      <div class="empty-icon">🛍</div>

      <h2>Giỏ hàng của bạn đang trống</h2>

      <p>
        Bạn chưa có sản phẩm nào trong giỏ hàng.
        Hãy chọn một đôi giày phù hợp với phong cách của bạn.
      </p>

      <RouterLink to="/" class="primary-button">
        KHÁM PHÁ SẢN PHẨM
      </RouterLink>
    </section>
  </main>
</template>

<style scoped>
.cart-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 28px;
  align-items: start;
}

.cart-list {
  min-width: 0;
}

.cart-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 22px 0;
  border-bottom: 1px solid #e8e8e8;
}

.cart-product-image {
  display: block;
  width: 130px;
  flex-shrink: 0;
  background: #f5f5f5;
  border-radius: 10px;
  overflow: hidden;
}

.cart-product-image img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
}

.cart-item-info {
  flex: 1;
  min-width: 0;
}

.cart-item-info h3 {
  margin: 0 0 8px;
  font-size: 16px;
  overflow-wrap: anywhere;
}

.cart-item-info p {
  margin: 0 0 8px;
  color: #777;
  font-size: 14px;
}

.item-price {
  display: block;
  margin-bottom: 12px;
}

.remove-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: none;
  background: transparent;
  color: #dc2626;
  font-size: 13px;
  cursor: pointer;
}

.remove-link:hover {
  text-decoration: underline;
}

.cart-quantity,
.cart-line-price {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field-label {
  color: #777;
  font-size: 13px;
}

.quantity-control {
  display: flex;
  align-items: center;
  gap: 12px;
}

.quantity-control button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid #ddd;
  border-radius: 6px;
  background: white;
  color: #222;
  font-size: 20px;
  cursor: pointer;
}

.quantity-control button:hover {
  border-color: #222;
}

.quantity-control button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.quantity-value {
  min-width: 16px;
  text-align: center;
}

.quantity-hint {
  max-width: 120px;
  color: #888;
  font-size: 11px;
}

.cart-line-price {
  min-width: 120px;
  text-align: right;
}

.order-summary {
  padding: 24px;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  background: #fff;
}

.order-summary h2 {
  margin: 0 0 22px;
  font-size: 20px;
}

.summary-line,
.summary-total {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 16px 0;
}

.summary-line {
  color: #666;
  font-size: 14px;
}

.summary-total {
  align-items: center;
  padding-top: 18px;
  border-top: 1px solid #e8e8e8;
}

.summary-total strong {
  font-size: 19px;
}

.summary-note {
  color: #888;
  font-size: 12px;
  line-height: 1.6;
}

.full-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  box-sizing: border-box;
  margin-top: 20px;
  text-align: center;
  text-decoration: none;
}

.continue-shopping {
  display: block;
  margin-top: 16px;
  text-align: center;
  color: #444;
  font-size: 14px;
  text-decoration: none;
}

.continue-shopping:hover {
  text-decoration: underline;
}

.empty-state {
  padding: 60px 20px;
  text-align: center;
}

.empty-icon {
  margin-bottom: 16px;
  font-size: 48px;
}

.empty-state h2 {
  margin-bottom: 12px;
}

.empty-state p {
  margin: 0 auto 24px;
  max-width: 460px;
  color: #777;
  line-height: 1.7;
}

@media (max-width: 1000px) {
  .cart-layout {
    grid-template-columns: minmax(0, 1fr) 290px;
    gap: 20px;
  }

  .cart-item {
    gap: 14px;
  }

  .cart-product-image {
    width: 100px;
  }

  .cart-line-price {
    min-width: 100px;
  }
}

@media (max-width: 768px) {
  .cart-layout {
    grid-template-columns: 1fr;
  }

  .cart-item {
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 14px;
  }

  .cart-product-image {
    width: 100px;
  }

  .cart-item-info {
    flex: 1;
    min-width: 150px;
  }

  .cart-quantity {
    margin-left: 114px;
  }

  .cart-line-price {
    margin-left: auto;
    text-align: right;
  }

  .order-summary {
    padding: 20px;
  }
}

@media (max-width: 420px) {
  .cart-quantity {
    margin-left: 0;
  }

  .cart-line-price {
    min-width: auto;
  }
}
</style>
```