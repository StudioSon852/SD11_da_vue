<script setup>
import { ref } from 'vue'

const paymentMethod = ref('vnpay')

const customerName = ref('')
const phone = ref('')
const address = ref('')
const note = ref('')

const errors = ref({
  customerName: '',
  phone: '',
  address: ''
})

const formatPrice = (price) => {
  return price.toLocaleString('vi-VN') + ' ₫'
}

const subtotal = 1290000
const shipping = 30000
const discount = 100000
const total = subtotal + shipping - discount

const payment = () => {
  errors.value.customerName = ''
  errors.value.phone = ''
  errors.value.address = ''

  if (!customerName.value) {
    errors.value.customerName = 'Name is required'
  }

  if (!phone.value) {
    errors.value.phone = 'Phone number is required'
  }

  if (!address.value) {
    errors.value.address = 'Address is required'
  }

  if (
    !errors.value.customerName &&
    !errors.value.phone &&
    !errors.value.address
  ) {
    console.log('Payment success')
    console.log('Payment method:', paymentMethod.value)
  }
}
</script>

<template>
  <div id="app" class="payment-container">

    <h3 class="payment-header">Payment</h3>

    <form @submit.prevent="payment" class="payment-form">

      <!-- THÔNG TIN NHẬN HÀNG -->
      <div class="section-title">
        Shipping information
      </div>

      <div class="form-group">
        <label for="customerName" class="form-label">
          Full name
        </label>

        <input
          type="text"
          id="customerName"
          v-model="customerName"
          :class="{ 'is-invalid': errors.customerName }"
          class="form-control form-input"
        />

        <div v-if="errors.customerName" class="invalid-feedback">
          {{ errors.customerName }}
        </div>
      </div>

      <div class="form-group">
        <label for="phone" class="form-label">
          Phone number
        </label>

        <input
          type="tel"
          id="phone"
          v-model="phone"
          :class="{ 'is-invalid': errors.phone }"
          class="form-control form-input"
        />

        <div v-if="errors.phone" class="invalid-feedback">
          {{ errors.phone }}
        </div>
      </div>

      <div class="form-group">
        <label for="address" class="form-label">
          Address
        </label>

        <input
          type="text"
          id="address"
          v-model="address"
          :class="{ 'is-invalid': errors.address }"
          class="form-control form-input"
        />

        <div v-if="errors.address" class="invalid-feedback">
          {{ errors.address }}
        </div>
      </div>

      <!-- PHƯƠNG THỨC THANH TOÁN -->
      <div class="section-title">
        Payment method
      </div>

      <div class="payment-method">

        <label
          class="payment-option"
          :class="{ active: paymentMethod === 'vnpay' }"
        >
          <input
            type="radio"
            value="vnpay"
            v-model="paymentMethod"
          />

          <div>
            <strong>VNPAY</strong>
            <p>Pay online via VNPAY</p>
          </div>
        </label>

        <label
          class="payment-option"
          :class="{ active: paymentMethod === 'cod' }"
        >
          <input
            type="radio"
            value="cod"
            v-model="paymentMethod"
          />

          <div>
            <strong>Cash on Delivery</strong>
            <p>Pay when receiving the order</p>
          </div>
        </label>

      </div>

      <!-- ĐƠN HÀNG -->
      <div class="section-title">
        Order summary
      </div>

      <div class="order-summary">

        <div class="order-row">
          <span>Subtotal</span>
          <span>{{ formatPrice(subtotal) }}</span>
        </div>

        <div class="order-row">
          <span>Shipping</span>
          <span>{{ formatPrice(shipping) }}</span>
        </div>

        <div class="order-row discount">
          <span>Discount</span>
          <span>-{{ formatPrice(discount) }}</span>
        </div>

        <div class="order-divider"></div>

        <div class="order-row total">
          <span>Total</span>
          <strong>{{ formatPrice(total) }}</strong>
        </div>

      </div>

      <!-- GHI CHÚ -->
      <div class="form-group">
        <label for="note" class="form-label">
          Note
        </label>

        <textarea
          id="note"
          v-model="note"
          class="form-control form-input textarea"
          placeholder="Enter a note for your order"
        ></textarea>
      </div>

      <button type="submit" class="btn btn-primary">
        {{ paymentMethod === 'vnpay' ? 'Pay with VNPAY' : 'Place order' }}
      </button>

      <p class="back-link">
        <a href="#">Back to cart</a>
      </p>

    </form>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Roboto&display=swap');

:root {
  --primary-color: #2E86C1;
  --danger-color: #dc3545;
  --gray-color: #f2f2f2;
}

* {
  box-sizing: border-box;
  font-family: 'Roboto', sans-serif;
}

body {
  margin: 0;
}

/* CONTAINER */
.payment-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background-color: var(--gray-color);
  padding: 30px 15px;
}

/* HEADER */
.payment-header {
  font-size: 2rem;
  margin-bottom: 1rem;
  color: #333;
  text-align: center;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-weight: bold;
}

/* FORM */
.payment-form {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 500px;
  padding: 2rem;
  background-color: white;
  border-radius: 5px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.08);
}

/* SECTION TITLE */
.section-title {
  width: 100%;
  margin-bottom: 1rem;
  margin-top: 0.5rem;
  color: #333;
  font-size: 1rem;
  font-weight: bold;
}

.section-title:first-child {
  margin-top: 0;
}

/* FORM GROUP */
.form-group {
  display: flex;
  flex-direction: column;
  width: 100%;
  margin-bottom: 1rem;
}

.form-label {
  font-size: 1rem;
  margin-bottom: 0.5rem;
  color: #333;
}

/* INPUT */
.form-control {
  width: 100%;
  padding: 0.6rem;
  font-size: 1rem;
  background-color: #f8f9fa;
  color: #333;
  border-radius: 3px;
  border: 1px solid #ccc;
  transition: all 0.2s ease;
}

.form-control:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 0.2rem rgba(0, 123, 255, 0.25);
  outline: none;
}

/* TEXTAREA */
.textarea {
  min-height: 80px;
  resize: vertical;
}

/* PAYMENT METHOD */
.payment-method {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 1rem;
}

.payment-option {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 12px;
  border: 1px solid #ccc;
  border-radius: 5px;
  background-color: #f8f9fa;
  cursor: pointer;
  transition: all 0.2s ease;
}

.payment-option:hover {
  border-color: var(--primary-color);
}

.payment-option.active {
  border-color: var(--primary-color);
  background-color: #eef7ff;
}

.payment-option input {
  margin-right: 12px;
  accent-color: var(--primary-color);
  cursor: pointer;
}

.payment-option strong {
  display: block;
  color: #333;
  margin-bottom: 3px;
}

.payment-option p {
  margin: 0;
  color: #777;
  font-size: 0.8rem;
}

/* ORDER SUMMARY */
.order-summary {
  width: 100%;
  padding: 1rem;
  margin-bottom: 1rem;
  background-color: #f8f9fa;
  border-radius: 5px;
}

.order-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  color: #555;
  font-size: 0.95rem;
}

.order-row.discount {
  color: #198754;
}

.order-divider {
  height: 1px;
  background-color: #ddd;
  margin: 12px 0;
}

.order-row.total {
  margin-bottom: 0;
  color: #333;
  font-size: 1.05rem;
}

.order-row.total strong {
  font-size: 1.2rem;
  color: var(--primary-color);
}

/* BUTTON */
.btn {
  padding: 0.6rem 1rem;
  font-size: 1.2rem;
  border-radius: 50px;
  border: none;
  color: white;
  background-color: var(--primary-color);
  cursor: pointer;
  transition: all 0.2s ease;
  width: 100%;
}

.btn:hover {
  background-color: #0069d9;
}

/* ERROR */
.is-invalid {
  border-color: var(--danger-color) !important;
}

.invalid-feedback {
  font-size: 0.8rem;
  color: var(--danger-color);
  margin-top: 4px;
}

/* BACK LINK */
.back-link {
  margin-top: 20px;
  margin-bottom: 0;
  text-align: center;
  font-size: 14px;
}

.back-link a {
  color: #333;
  text-decoration: underline;
}

.back-link a:hover {
  text-decoration: none;
}

/* RESPONSIVE */
@media (max-width: 600px) {
  .payment-form {
    padding: 1.5rem;
  }

  .payment-header {
    font-size: 1.5rem;
  }
}
</style>