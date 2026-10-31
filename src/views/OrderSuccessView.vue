<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderState, formatCurrency, loadOrders, ORDER_STATUS } from '../stores/orderStore'
import FigmaProductCard from '../components/figma/product/FigmaProductCard.vue'
import FigmaProductGrid from '../components/figma/product/FigmaProductGrid.vue'
import { api } from "../services/apiClient";
import { notify } from '../stores/uiStore'

const route = useRoute()
const router = useRouter()

const order = computed(() => orderState.orders.find((o) => o.id === route.query.orderId))

const suggested = ref([])
const isLoadingSuggested = ref(true)
let orderPollTimer = null
const normalizePayment = (value) => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase().trim()
const paymentKey = computed(() => normalizePayment(order.value?.payment_status))
const paymentResult = computed(() => {
  if (['da thanh toan', 'da xac nhan', 'thanh cong', 'success', 'succeeded'].includes(paymentKey.value)) return 'SUCCESS'
  if (['that bai', 'da huy', 'failed', 'cancelled', 'canceled'].includes(paymentKey.value)) return 'FAILED'
  if (paymentKey.value.includes('cho hoan tien')) return 'REFUND'
  return 'PENDING'
})
const heroTitle = computed(() => paymentResult.value === 'SUCCESS' ? 'THANH TOÁN THÀNH CÔNG' : paymentResult.value === 'FAILED' ? 'THANH TOÁN THẤT BẠI' : paymentResult.value === 'REFUND' ? 'ĐANG XỬ LÝ HOÀN TIỀN' : 'ĐƠN HÀNG ĐÃ ĐƯỢC TẠO')
const heroMessage = computed(() => paymentResult.value === 'SUCCESS'
  ? 'Cửa hàng đã xác nhận thanh toán. Bạn có thể theo dõi các bước xử lý đơn hàng bên dưới.'
  : paymentResult.value === 'FAILED'
    ? 'Cửa hàng đã hủy hoặc từ chối giao dịch. Vui lòng mở chi tiết đơn hàng để xem trạng thái và ghi chú.'
    : paymentResult.value === 'REFUND'
      ? 'Đơn hàng đã hủy sau khi thanh toán. Cửa hàng đang xử lý hoàn tiền.'
      : 'Đơn hàng được lưu thành công và đang chờ Admin/Nhân viên xác nhận giao dịch chuyển khoản.')

const fetchSuggested = async () => {
  try {
    const data = await api.get('/products')
    if (!Array.isArray(data)) throw new Error('Dữ liệu sản phẩm không hợp lệ')
    suggested.value = data
      .filter((p) => p.active)
      .slice(0, 4)
      .map((p) => ({
        id_product: p.id, product_name: p.name, price: p.price,
        id_category: p.category_id, category_name: p.category, sport: p.sport,
        material_name: p.material_name, image_url: p.image_url,
        brand_name: p.brand_name || p.brand || '', id_brand: p.id_brand || 1, colors: p.colors || [], variants: p.variants || [],
      }))
  } catch (e) {
    // Trang xác nhận đơn vẫn giữ đúng hợp đồng dữ liệu: khi API lỗi thì
    // hiển thị trạng thái rỗng thay vì đưa dữ liệu sản phẩm mẫu lên UI.
    suggested.value = []
  } finally {
    isLoadingSuggested.value = false
  }
}

const goOrders = () => router.push('/account?tab=orders')
const goHome = () => router.push('/')

watch(paymentKey, (next, previous) => {
  if (!previous || !next || next === previous) return
  if (['da thanh toan', 'da xac nhan', 'thanh cong', 'success', 'succeeded'].includes(next)) {
    notify({ type: 'success', title: 'Thanh toán thành công', message: `Đơn #${order.value?.serverId || order.value?.id} đã được cửa hàng xác nhận.` })
  } else if (['that bai', 'da huy', 'failed', 'cancelled', 'canceled'].includes(next)) {
    notify({ type: 'error', title: 'Thanh toán thất bại', message: `Đơn #${order.value?.serverId || order.value?.id} đã bị từ chối hoặc hủy.` })
  }
})

onMounted(async () => {
  window.scrollTo(0, 0)
  if (!order.value) { router.replace('/') ; return }
  await fetchSuggested()
  // Giữ trang xác nhận đồng bộ với trạng thái ở Admin/Nhân viên; cũng hỗ trợ
  // khách mở nhiều đơn liên tiếp mà không phụ thuộc localStorage cũ.
  await loadOrders()
  orderPollTimer = window.setInterval(async () => {
    try { await loadOrders() } catch (_) { /* retry on next poll */ }
  }, 5000)
})
onUnmounted(() => {
  if (orderPollTimer) window.clearInterval(orderPollTimer)
})
</script>

<template>
  <div class="success-page">
    <div v-if="order" class="w-full px-4 py-5" style="max-width: 1200px; margin: 0 auto;">

      <!-- Success header -->
      <div class="suc-hero">
        <div class="suc-icon"><i class="icon icon-check-lg"></i></div>
        <h1 class="suc-title" :class="{ 'suc-title-failed': paymentResult === 'FAILED' }">{{ heroTitle }}</h1>
        <p class="text-gray-600 mb-0">{{ heroMessage }}</p>
      </div>

      <!-- Order info -->
      <div class="suc-info">
        <div class="suc-info-row"><span>Mã đơn:</span><strong>{{ order.serverId || order.id }}</strong></div>
        <div class="suc-info-row"><span>Tổng tiền:</span><strong>{{ formatCurrency(order.total) }}</strong></div>
        <div class="suc-info-row"><span>Trạng thái đơn:</span><strong>{{ ORDER_STATUS[order.status] || order.status }}</strong></div>
        <div class="suc-info-row"><span>Thanh toán:</span><strong :class="{ 'text-green-700': paymentResult === 'SUCCESS', 'text-red-700': paymentResult === 'FAILED' }">{{ order.payment_status || 'Chờ xác nhận thanh toán' }}</strong></div>
        <div class="suc-info-row"><span>Giao hàng:</span><strong>{{ order.shippingMethod?.name }}</strong></div>
        <div class="suc-info-row"><span>Dự kiến:</span><strong>{{ order.shippingMethod?.eta }}</strong></div>
      </div>

      <div class="flex gap-3 mt-4 suc-actions">
        <button class="btn-sg grow" @click="goOrders">XEM ĐƠN HÀNG</button>
        <button class="btn-sg-outline grow" @click="goHome">VỀ TRANG CHỦ</button>
      </div>

      <!-- Suggested products -->
      <section class="suc-suggest">
        <div class="sec-head">
          <div>
            <div class="sg-title-bar mb-2"></div>
            <h2 class="sec-title">Có thể bạn cũng thích</h2>
            <p class="text-gray-600 mb-0">Khám phá thêm các mẫu giày nổi bật khác.</p>
          </div>
          <router-link to="/products" class="btn-sg-outline hidden md:inline-flex">Xem tất cả <i class="icon icon-arrow-right ml-1"></i></router-link>
        </div>

        <div v-if="isLoadingSuggested" class="text-center py-5">
          <div class="sg-spinner text-gray-900"></div>
        </div>
        <FigmaProductGrid v-else :columns="4">
          <div v-for="product in suggested" :key="product.id_product" class="min-w-0">
            <FigmaProductCard :product="product" />
          </div>
        </FigmaProductGrid>
      </section>

    </div>
  </div>
</template>

<style scoped>
.success-page {
  background: #ffffff;
  min-height: 100vh;
}

.suc-hero {
  text-align: center;
  padding: 20px 0 32px;
}
.suc-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 20px;
  border-radius: 50%;
  background: #D4001A;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.8rem;
}
.suc-title {
  font-weight: 700;
  font-size: 1.6rem;
  letter-spacing: 0.1em;
  color: #1a1a1a;
  margin-bottom: 8px;
}

.suc-title-failed { color: #b91c1c; }
.suc-info {
  max-width: 560px;
  margin: 0 auto;
  background: #fafafa;
  border: 1px solid #e5e5e5;
  padding: 24px;
  border-radius: 4px;
  text-align: left;
}
.suc-info-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 0.9rem;
}
.suc-info-row:last-child {
  margin-bottom: 0;
}
.suc-info-row span {
  color: #666;
}
.suc-info-row strong {
  color: #1a1a1a;
  font-weight: 600;
}

.suc-actions {
  max-width: 560px;
  margin: 16px auto 0;
}

.suc-suggest {
  margin-top: 64px;
  padding-top: 40px;
  border-top: 1px solid #e5e5e5;
}
.sec-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 28px;
  flex-wrap: wrap;
  gap: 12px;
}
.sec-title {
  font-weight: 900;
  font-size: 1.6rem;
  letter-spacing: -0.02em;
  margin: 0;
}
</style>
