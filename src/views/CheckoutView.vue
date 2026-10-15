
<script setup lang="ts">
import { computed, reactive } from "vue";
import { useCart } from "../composables/useCart";

const { items, total } = useCart();

const customer = reactive({
  fullName: "",
  phone: "",
  email: "",
  address: "",
  province: "",
  district: "",
  note: "",
  paymentMethod: "cod",
});

const shippingFee = 0;

const grandTotal = computed(() => total.value + shippingFee);

const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);
</script>

<template>
  <main class="container inner-page checkout-page">
    <div class="breadcrumbs">
      <RouterLink to="/">Trang chủ</RouterLink>
      /
      <RouterLink to="/cart">Giỏ hàng</RouterLink>
      / Thanh toán
    </div>

    <header class="checkout-heading">
      <span class="eyebrow">SHOEHUB CHECKOUT</span>
      <h1 class="page-title">Thanh toán đơn hàng</h1>
      <p>
        Kiểm tra thông tin giao hàng và sản phẩm trước khi hoàn tất.
      </p>
    </header>

    <div class="checkout-steps">
      <div class="checkout-step completed">
        <span>1</span>
        <div>
          <strong>Giỏ hàng</strong>
          <small>Chọn sản phẩm</small>
        </div>
      </div>

      <div class="step-divider"></div>

      <div class="checkout-step active">
        <span>2</span>
        <div>
          <strong>Thanh toán</strong>
          <small>Thông tin nhận hàng</small>
        </div>
      </div>

      <div class="step-divider"></div>

      <div class="checkout-step">
        <span>3</span>
        <div>
          <strong>Hoàn tất</strong>
          <small>Chưa tích hợp</small>
        </div>
      </div>
    </div>

    <section v-if="items.length" class="checkout-layout">
      <div class="checkout-main">
        <section class="checkout-card">
          <div class="checkout-card-heading">
            <span class="checkout-number">01</span>
            <div>
              <h2>Thông tin người nhận</h2>
              <p>Nhập thông tin để hiển thị đơn hàng demo.</p>
            </div>
          </div>

          <form class="checkout-form" @submit.prevent>
            <div class="checkout-fields">
              <div class="checkout-field full-field">
                <label for="fullName">Họ và tên *</label>
                <input
                  id="fullName"
                  v-model.trim="customer.fullName"
                  type="text"
                  autocomplete="name"
                  placeholder="Nguyễn Văn A"
                />
              </div>

              <div class="checkout-field">
                <label for="phone">Số điện thoại *</label>
                <input
                  id="phone"
                  v-model.trim="customer.phone"
                  type="tel"
                  autocomplete="tel"
                  placeholder="09xxxxxxxx"
                />
              </div>

              <div class="checkout-field">
                <label for="email">Email</label>
                <input
                  id="email"
                  v-model.trim="customer.email"
                  type="email"
                  autocomplete="email"
                  placeholder="example@gmail.com"
                />
              </div>

              <div class="checkout-field full-field">
                <label for="address">Địa chỉ cụ thể *</label>
                <input
                  id="address"
                  v-model.trim="customer.address"
                  autocomplete="street-address"
                  placeholder="Số nhà, tên đường..."
                />
              </div>

              <div class="checkout-field">
                <label for="province">Tỉnh / Thành phố</label>
                <input
                  id="province"
                  v-model.trim="customer.province"
                  placeholder="Ví dụ: Hà Nội"
                />
              </div>

              <div class="checkout-field">
                <label for="district">Quận / Huyện</label>
                <input
                  id="district"
                  v-model.trim="customer.district"
                  placeholder="Nhập quận / huyện"
                />
              </div>

              <div class="checkout-field full-field">
                <label for="note">Ghi chú đơn hàng</label>
                <textarea
                  id="note"
                  v-model.trim="customer.note"
                  rows="3"
                  placeholder="Ghi chú cho người giao hàng (nếu có)"
                ></textarea>
              </div>
            </div>
          </form>
        </section>

        <section class="checkout-card">
          <div class="checkout-card-heading">
            <span class="checkout-number">02</span>
            <div>
              <h2>Phương thức vận chuyển</h2>
              <p>Lựa chọn mô phỏng cho giao diện.</p>
            </div>
          </div>

          <label class="option-card selected-option">
            <input type="radio" checked disabled />
            <span class="option-icon">🚚</span>
            <span class="option-copy">
              <strong>Giao hàng tiêu chuẩn</strong>
              <small>Thời gian giao dự kiến 2–5 ngày</small>
            </span>
            <strong>Miễn phí</strong>
          </label>
        </section>

        <section class="checkout-card">
          <div class="checkout-card-heading">
            <span class="checkout-number">03</span>
            <div>
              <h2>Phương thức thanh toán</h2>
              <p>Chọn phương thức để xem trước giao diện.</p>
            </div>
          </div>

          <label
            class="option-card"
            :class="{ 'selected-option': customer.paymentMethod === 'cod' }"
          >
            <input
              v-model="customer.paymentMethod"
              type="radio"
              value="cod"
            />
            <span class="option-icon">💵</span>
            <span class="option-copy">
              <strong>Thanh toán khi nhận hàng (COD)</strong>
              <small>Thanh toán trực tiếp cho người giao hàng</small>
            </span>
          </label>

          <label
            class="option-card"
            :class="{ 'selected-option': customer.paymentMethod === 'bank' }"
          >
            <input
              v-model="customer.paymentMethod"
              type="radio"
              value="bank"
            />
            <span class="option-icon">🏦</span>
            <span class="option-copy">
              <strong>Chuyển khoản ngân hàng</strong>
              <small>Chỉ là lựa chọn giao diện demo</small>
            </span>
          </label>

          <label
            class="option-card"
            :class="{ 'selected-option': customer.paymentMethod === 'online' }"
          >
            <input
              v-model="customer.paymentMethod"
              type="radio"
              value="online"
            />
            <span class="option-icon">💳</span>
            <span class="option-copy">
              <strong>Thanh toán trực tuyến</strong>
              <small>Chưa tích hợp cổng thanh toán</small>
            </span>
          </label>
        </section>
      </div>

      <aside class="order-summary checkout-summary">
        <h2>Đơn hàng của bạn</h2>

        <p class="checkout-product-count">
          {{ items.length }} dòng sản phẩm
        </p>

        <div
          v-for="item in items"
          :key="`${item.product_id}-${item.size}`"
          class="checkout-item"
        >
          <div class="checkout-item-image">
            <img :src="item.image" :alt="item.name" />
            <span>{{ item.quantity }}</span>
          </div>

          <div class="checkout-item-info">
            <strong>{{ item.name }}</strong>
            <small>Size EU {{ item.size }}</small>
            <small>Số lượng: {{ item.quantity }}</small>
            <b>{{ money(item.price * item.quantity) }}</b>
          </div>
        </div>

        <RouterLink to="/cart" class="edit-cart-link">
          ← Chỉnh sửa giỏ hàng
        </RouterLink>

        <div class="checkout-summary-lines">
          <div class="summary-line">
            <span>Tạm tính</span>
            <span>{{ money(total) }}</span>
          </div>

          <div class="summary-line">
            <span>Phí vận chuyển</span>
            <span>{{ money(shippingFee) }}</span>
          </div>
        </div>

        <div class="summary-total">
          <span>Tổng thanh toán</span>
          <strong>{{ money(grandTotal) }}</strong>
        </div>

        <button
          type="button"
          class="primary-button full-button checkout-submit"
          disabled
          title="Chức năng thanh toán chưa được tích hợp"
        >
          XÁC NHẬN THANH TOÁN
        </button>

        <p class="demo-notice">
          Đây là giao diện demo. Chưa tạo đơn hàng, chưa gửi dữ liệu và
          chưa phát sinh giao dịch thanh toán.
        </p>

        <div class="checkout-security">
          <span>🔒</span>
          <span>Thông tin chỉ được nhập để xem trước giao diện.</span>
        </div>
      </aside>
    </section>

    <section v-else class="empty-state">
      <div class="empty-icon">🛍</div>
      <h2>Chưa có sản phẩm để thanh toán</h2>
      <p>Hãy thêm sản phẩm vào giỏ hàng trước khi tiếp tục.</p>
      <RouterLink to="/" class="primary-button">
        QUAY LẠI MUA SẮM
      </RouterLink>
    </section>
  </main>
</template>

<style scoped>
.checkout-page {
  padding-bottom: 64px;
}

.checkout-heading {
  margin-bottom: 28px;
}

.checkout-heading > p,
.checkout-card-heading p {
  color: #777;
  font-size: 14px;
  line-height: 1.6;
}

.checkout-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin: 30px 0 36px;
}

.checkout-step {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #999;
}

.checkout-step > span {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid #ddd;
  border-radius: 50%;
  font-weight: 700;
}

.checkout-step.active,
.checkout-step.completed {
  color: #171717;
}

.checkout-step.active > span,
.checkout-step.completed > span {
  background: #171717;
  border-color: #171717;
  color: white;
}

.checkout-step strong,
.checkout-step small {
  display: block;
}

.checkout-step strong {
  font-size: 13px;
}

.checkout-step small {
  margin-top: 4px;
  font-size: 11px;
  color: #888;
}

.step-divider {
  width: 55px;
  height: 1px;
  background: #ddd;
}

.checkout-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  align-items: start;
  gap: 28px;
}

.checkout-main {
  display: grid;
  gap: 20px;
  min-width: 0;
}

.checkout-card {
  padding: 24px;
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
}

.checkout-card-heading {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 24px;
}

.checkout-number {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  background: #f2f2f2;
  border-radius: 8px;
  font-weight: 700;
}

.checkout-card-heading h2 {
  margin: 0;
  font-size: 19px;
}

.checkout-card-heading p {
  margin: 6px 0 0;
}

.checkout-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.checkout-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.full-field {
  grid-column: 1 / -1;
}

.checkout-field label {
  font-size: 13px;
  font-weight: 600;
}

.checkout-field input,
.checkout-field textarea {
  box-sizing: border-box;
  width: 100%;
  padding: 12px 13px;
  border: 1px solid #ddd;
  border-radius: 7px;
  background: #fff;
  color: #222;
  font: inherit;
  font-size: 14px;
}

.checkout-field input:focus,
.checkout-field textarea:focus {
  outline: none;
  border-color: #333;
}

.checkout-field textarea {
  resize: vertical;
}

.option-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  margin-top: 12px;
  border: 1px solid #e4e4e4;
  border-radius: 9px;
  cursor: pointer;
}

.selected-option {
  border-color: #292929;
  background: #fafafa;
}

.option-icon {
  font-size: 23px;
}

.option-copy {
  flex: 1;
  min-width: 0;
}

.option-copy strong,
.option-copy small {
  display: block;
}

.option-copy strong {
  font-size: 13px;
}

.option-copy small {
  margin-top: 5px;
  color: #777;
  font-size: 12px;
  line-height: 1.5;
}

.option-card > strong {
  font-size: 12px;
}

.checkout-summary {
  position: sticky;
  top: 24px;
  min-width: 0;
}

.checkout-summary h2 {
  margin-top: 0;
}

.checkout-product-count {
  margin: -8px 0 20px;
  color: #777;
  font-size: 13px;
}

.checkout-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #eee;
}

.checkout-item-image {
  position: relative;
  width: 76px;
  height: 76px;
  flex-shrink: 0;
  background: #f5f5f5;
  border-radius: 8px;
}

.checkout-item-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}

.checkout-item-image > span {
  position: absolute;
  top: -7px;
  right: -7px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #222;
  color: #fff;
  font-size: 11px;
}

.checkout-item-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  min-width: 0;
}

.checkout-item-info strong {
  font-size: 13px;
  line-height: 1.5;
}

.checkout-item-info small {
  color: #777;
  font-size: 12px;
}

.checkout-item-info b {
  margin-top: 3px;
  font-size: 13px;
}

.edit-cart-link {
  display: inline-block;
  margin: 18px 0;
  color: #333;
  font-size: 13px;
  text-decoration: underline;
}

.checkout-summary-lines {
  padding: 16px 0;
  border-top: 1px solid #eee;
}

.checkout-summary .summary-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 10px 0;
  font-size: 13px;
}

.checkout-summary .summary-total {
  padding: 18px 0;
  border-top: 1px solid #eee;
}

.checkout-submit {
  margin-top: 16px;
  opacity: 0.55;
  cursor: not-allowed;
}

.demo-notice {
  margin: 14px 0 0;
  color: #777;
  font-size: 12px;
  line-height: 1.7;
}

.checkout-security {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #eee;
  color: #777;
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .checkout-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .checkout-summary {
    position: static;
  }
}

@media (max-width: 520px) {
  .checkout-card {
    padding: 17px;
  }

  .checkout-fields {
    grid-template-columns: minmax(0, 1fr);
  }

  .full-field {
    grid-column: auto;
  }

  .checkout-steps {
    gap: 8px;
  }

  .checkout-step {
    gap: 6px;
  }

  .checkout-step small {
    display: none;
  }

  .checkout-step > span {
    width: 28px;
    height: 28px;
  }

  .step-divider {
    width: 16px;
  }
}
</style>
