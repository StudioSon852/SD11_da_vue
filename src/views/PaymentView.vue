
<script setup lang="ts">
import { computed, ref } from "vue";
import { useCart } from "../composables/useCart";

const { items, total, updateQuantity, remove } = useCart();

const shipping = ref({
  fullName: "",
  phone: "",
  email: "",
  address: "",
  note: "",
});

const selectedPayment = ref("cod");
const promoCode = ref("");
const discount = ref(0);
const promoError = ref("");
const message = ref("");

const shippingFee = 0;

const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);

const finalTotal = computed(() =>
  Math.max(0, total.value - discount.value + shippingFee),
);

function applyPromo() {
  if (promoCode.value.trim().toUpperCase() === "DEMO100K") {
    discount.value = Math.min(100000, total.value);
    promoError.value = "";
  } else {
    discount.value = 0;
    promoError.value = promoCode.value.trim()
      ? "Mã khuyến mãi không hợp lệ."
      : "Vui lòng nhập mã khuyến mãi.";
  }
}

function confirmOrder() {
  message.value = "";

  if (!items.value.length) {
    message.value = "Giỏ hàng đang trống.";
    return;
  }

  if (
    !shipping.value.fullName.trim() ||
    !shipping.value.phone.trim() ||
    !shipping.value.address.trim()
  ) {
    message.value = "Vui lòng nhập họ tên, số điện thoại và địa chỉ giao hàng.";
    return;
  }

  message.value =
    "Thông tin đã hợp lệ. Đây là giao diện demo; đơn hàng và thanh toán chưa được gửi lên máy chủ.";
}
</script>

<template>
  <main class="payment-page container inner-page">
    <div class="payment-heading">
      <div>
        <span class="eyebrow">CHECKOUT</span>
        <h1 class="page-title">Thanh toán</h1>
        <p class="payment-subtitle">
          Kiểm tra thông tin giao hàng và đơn hàng của bạn.
        </p>
      </div>
      <RouterLink to="/cart" class="back-link">
        ← Quay lại giỏ hàng
      </RouterLink>
    </div>

    <div v-if="items.length" class="payment-layout">
      <div class="payment-left">
        <section class="payment-card">
          <h2>Thông tin giao hàng</h2>

          <form class="shipping-form" @submit.prevent="confirmOrder">
            <label for="fullName">Họ và tên *</label>
            <input
              id="fullName"
              v-model.trim="shipping.fullName"
              autocomplete="name"
              placeholder="Nhập họ và tên"
              required
            />

            <div class="form-row">
              <div>
                <label for="phone">Số điện thoại *</label>
                <input
                  id="phone"
                  v-model.trim="shipping.phone"
                  type="tel"
                  autocomplete="tel"
                  placeholder="Nhập số điện thoại"
                  required
                />
              </div>

              <div>
                <label for="email">Email</label>
                <input
                  id="email"
                  v-model.trim="shipping.email"
                  type="email"
                  autocomplete="email"
                  placeholder="Email (không bắt buộc)"
                />
              </div>
            </div>

            <label for="address">Địa chỉ giao hàng *</label>
            <textarea
              id="address"
              v-model.trim="shipping.address"
              autocomplete="street-address"
              rows="3"
              placeholder="Số nhà, đường, phường/xã, tỉnh/thành phố"
              required
            />

            <label for="note">Ghi chú đơn hàng</label>
            <textarea
              id="note"
              v-model.trim="shipping.note"
              rows="2"
              placeholder="Ghi chú cho cửa hàng (nếu có)"
            />
          </form>
        </section>

        <section class="payment-card">
          <h2>Phương thức giao hàng</h2>
          <div class="shipping-choice">
            <span class="choice-icon">🚚</span>
            <div>
              <strong>Giao hàng tiêu chuẩn</strong>
              <p>Phí vận chuyển sẽ được xác nhận khi xử lý đơn.</p>
            </div>
            <span class="choice-price">Miễn phí*</span>
          </div>
        </section>

        <section class="payment-card">
          <h2>Phương thức thanh toán</h2>

          <label class="payment-option">
            <input v-model="selectedPayment" type="radio" value="cod" />
            <span>
              <strong>Thanh toán khi nhận hàng (COD)</strong>
              <small>Thanh toán trực tiếp khi nhận hàng.</small>
            </span>
          </label>

          <label class="payment-option">
            <input v-model="selectedPayment" type="radio" value="bank" />
            <span>
              <strong>Chuyển khoản ngân hàng</strong>
              <small>Chỉ chọn phương thức trong bản demo.</small>
            </span>
          </label>

          <label class="payment-option">
            <input v-model="selectedPayment" type="radio" value="online" />
            <span>
              <strong>Thanh toán trực tuyến</strong>
              <small>Chưa tích hợp cổng thanh toán thật.</small>
            </span>
          </label>
        </section>
      </div>

      <aside class="payment-right">
        <section class="payment-card order-card">
          <h2>Đơn hàng của bạn ({{ items.length }} sản phẩm)</h2>

          <article
            v-for="item in items"
            :key="`${item.product_id}-${item.size}`"
            class="payment-item"
          >
            <img :src="item.image" :alt="item.name" />

            <div class="payment-item-info">
              <strong>{{ item.name }}</strong>
              <small>Size {{ item.size }}</small>
              <span class="unit-price">{{ money(item.price) }}</span>

              <div class="quantity-control">
                <button
                  type="button"
                  :disabled="item.quantity <= 1"
                  aria-label="Giảm số lượng"
                  @click="
                    updateQuantity(
                      item.product_id,
                      item.size,
                      item.quantity - 1,
                    )
                  "
                >
                  −
                </button>

                <span>{{ item.quantity }}</span>

                <button
                  type="button"
                  aria-label="Tăng số lượng"
                  @click="
                    updateQuantity(
                      item.product_id,
                      item.size,
                      item.quantity + 1,
                    )
                  "
                >
                  +
                </button>

                <button
                  type="button"
                  class="remove-button"
                  @click="remove(item.product_id, item.size)"
                >
                  Xóa
                </button>
              </div>
            </div>

            <strong class="item-total">
              {{ money(item.price * item.quantity) }}
            </strong>
          </article>

          <div class="promo-form">
            <label for="promoCode">Mã khuyến mãi</label>
            <div class="promo-row">
              <input
                id="promoCode"
                v-model.trim="promoCode"
                placeholder="Nhập DEMO100K"
              />
              <button type="button" @click="applyPromo">Áp dụng</button>
            </div>
            <p v-if="promoError" class="form-error">{{ promoError }}</p>
            <p v-else class="promo-hint">Mã demo: DEMO100K</p>
          </div>

          <div class="summary-row">
            <span>Tạm tính</span>
            <span>{{ money(total) }}</span>
          </div>

          <div class="summary-row">
            <span>Phí vận chuyển</span>
            <span>{{ shippingFee === 0 ? "0 ₫ (demo)" : money(shippingFee) }}</span>
          </div>

          <div v-if="discount > 0" class="summary-row discount-row">
            <span>Giảm giá</span>
            <span>−{{ money(discount) }}</span>
          </div>

          <div class="summary-total">
            <span>Tổng thanh toán</span>
            <strong>{{ money(finalTotal) }}</strong>
          </div>

          <button
            type="button"
            class="primary-button full-button"
            @click="confirmOrder"
          >
            XÁC NHẬN ĐẶT HÀNG →
          </button>

          <p v-if="message" class="demo-message" role="status">
            {{ message }}
          </p>

          <p class="summary-note">
            Trang demo giao diện. Chưa tạo đơn hàng hoặc thực hiện giao dịch
            thanh toán thật.
          </p>
        </section>
      </aside>
    </div>

    <section v-else class="empty-state payment-empty">
      <h2>Giỏ hàng đang trống</h2>
      <p>Hãy thêm sản phẩm trước khi tiến hành thanh toán.</p>
      <RouterLink to="/" class="primary-button">
        TIẾP TỤC MUA SẮM
      </RouterLink>
    </section>
  </main>
</template>

<style scoped>
.payment-page {
  padding-top: 30px;
  padding-bottom: 56px;
}

.payment-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 28px;
}

.payment-subtitle {
  margin: 8px 0 0;
  color: var(--text);
  font-size: 14px;
}

.back-link {
  color: var(--text-h);
  text-decoration: none;
  font-size: 14px;
  white-space: nowrap;
}

.back-link:hover {
  text-decoration: underline;
}

.payment-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(340px, 0.85fr);
  gap: 24px;
  align-items: start;
}

.payment-left,
.payment-right {
  display: grid;
  gap: 20px;
  min-width: 0;
}

.payment-card {
  min-width: 0;
  padding: 24px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--bg);
  box-shadow: var(--shadow);
}

.payment-card h2 {
  margin: 0 0 20px;
  color: var(--text-h);
  font-size: 19px;
}

.shipping-form {
  display: grid;
  gap: 10px;
}

.shipping-form label,
.promo-form label {
  color: var(--text-h);
  font-size: 13px;
  font-weight: 600;
}

.shipping-form input,
.shipping-form textarea,
.promo-row input {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg);
  color: var(--text-h);
  font: inherit;
  font-size: 14px;
}

.shipping-form textarea {
  resize: vertical;
}

.shipping-form input:focus,
.shipping-form textarea:focus,
.promo-row input:focus {
  outline: 2px solid var(--accent-border);
  outline-offset: 1px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-row > div {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.shipping-choice {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 10px;
}

.choice-icon {
  font-size: 25px;
}

.shipping-choice > div {
  flex: 1;
}

.shipping-choice strong,
.payment-option strong {
  display: block;
  color: var(--text-h);
  font-size: 14px;
}

.shipping-choice p,
.payment-option small {
  display: block;
  margin: 5px 0 0;
  color: var(--text);
  font-size: 12px;
  line-height: 1.5;
}

.choice-price {
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.payment-option {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 15px 12px;
  border: 1px solid var(--border);
  border-radius: 9px;
  margin-top: 10px;
  cursor: pointer;
}

.payment-option:has(input:checked) {
  border-color: var(--text-h);
  background: var(--code-bg);
}

.payment-option input {
  margin-top: 3px;
  accent-color: var(--text-h);
}

.payment-item {
  display: grid;
  grid-template-columns: 66px minmax(0, 1fr) auto;
  gap: 12px;
  padding: 0 0 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--border);
}

.payment-item > img {
  width: 66px;
  height: 76px;
  border-radius: 8px;
  object-fit: cover;
  background: var(--code-bg);
}

.payment-item-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}

.payment-item-info > strong {
  color: var(--text-h);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.payment-item-info small,
.unit-price {
  font-size: 12px;
  color: var(--text);
}

.item-total {
  color: var(--text-h);
  font-size: 13px;
  white-space: nowrap;
}

.quantity-control {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 3px;
}

.quantity-control button {
  display: grid;
  place-items: center;
  min-width: 27px;
  height: 27px;
  padding: 0 6px;
  border: 1px solid var(--border);
  border-radius: 5px;
  background: var(--bg);
  color: var(--text-h);
  cursor: pointer;
}

.quantity-control button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.quantity-control span {
  font-size: 13px;
}

.quantity-control .remove-button {
  border: 0;
  color: var(--text);
  font-size: 12px;
}

.promo-form {
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--border);
}

.promo-form label {
  display: block;
  margin-bottom: 9px;
}

.promo-row {
  display: flex;
  gap: 8px;
}

.promo-row button {
  padding: 0 14px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--text-h);
  color: var(--bg);
  font-weight: 600;
  cursor: pointer;
}

.promo-hint,
.form-error {
  margin: 8px 0 0;
  font-size: 12px;
}

.promo-hint {
  color: var(--text);
}

.form-error {
  color: #c62828;
}

.summary-row,
.summary-total {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 15px;
  font-size: 14px;
}

.summary-total {
  align-items: center;
  padding-top: 18px;
  margin-top: 18px;
  border-top: 1px solid var(--border);
  color: var(--text-h);
}

.summary-total strong {
  font-size: 21px;
  text-align: right;
}

.discount-row {
  color: #188038;
}

.full-button {
  display: flex;
  box-sizing: border-box;
  justify-content: center;
  width: 100%;
  margin-top: 22px;
  text-align: center;
  text-decoration: none;
  cursor: pointer;
}

.demo-message {
  padding: 12px;
  margin: 14px 0 0;
  border-radius: 8px;
  background: var(--code-bg);
  color: var(--text-h);
  font-size: 13px;
  line-height: 1.6;
}

.summary-note {
  margin: 14px 0 0;
  color: var(--text);
  font-size: 12px;
  line-height: 1.6;
}

.payment-empty {
  text-align: center;
}

@media (max-width: 800px) {
  .payment-layout {
    grid-template-columns: 1fr;
  }

  .payment-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .payment-right {
    order: -1;
  }
}

@media (max-width: 480px) {
  .payment-card {
    padding: 17px;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .payment-item {
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .payment-item > img {
    width: 56px;
    height: 66px;
  }

  .item-total {
    grid-column: 2;
  }
}
</style>
