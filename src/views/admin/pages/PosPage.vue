<!-- Trang: Bán Hàng Tại Quầy (POS) -->
<script setup>
import { ref } from 'vue'
import {
  activePosOrder, resetPosOrder,
  posPayModal, confirmPosPaid, cancelPosPay,
  posSearch, posVariants, addToCart, removeCartItem,
  posSubtotal, posCouponDiscountAmount, posMemberDiscountAmount, posMemberTier, posDiscountAmount, posGrandTotal, posChange,
  posCouponList, applyPosCoupon, clearPosCoupon,
  posCustomerSearch, posCustomerResults, pickPosCustomer,
  checkoutPos, formatPrice, formatDate, validateCartItemQty, posSubmitting,
  posInvoiceModal, closePosInvoice, printPosInvoice, openPosOrderInPayments, formatPosCountdown,
} from '../adminStore'

const qtyInputs = ref({})
function addWithQty(v) {
  const n = Number(qtyInputs.value[v.id]) || 1
  addToCart(v, n)
  qtyInputs.value[v.id] = 1
}
</script>

<template>
  <div class="fade-in">
    <div class="grid grid-cols-12 gap-4">
      <!-- CỘT TRÁI -->
      <div class="lg:col-span-7">
        <!-- Khách hàng -->
        <div class="bg-white rounded-1 shadow-sm p-4 mb-4">
          <div class="flex justify-between items-center mb-3">
            <h6 class="font-bold mb-0 text-gray-900"><i class="icon icon-person-circle mr-2"></i>Khách hàng</h6>
          </div>
          <div class="flex gap-2 mb-3">
            <button @click="activePosOrder.customer_type = 'Có tài khoản'; activePosOrder.customer_id = null" class="btn btn-sm rounded-1 px-3 border" :class="activePosOrder.customer_type === 'Có tài khoản' ? 'btn-dark text-white border-dark' : 'btn-white text-gray-600'">Có tài khoản</button>
            <button @click="activePosOrder.customer_type = 'Khách lẻ'; activePosOrder.customer_id = null" class="btn btn-sm rounded-1 px-3 border" :class="activePosOrder.customer_type === 'Khách lẻ' ? 'btn-dark text-white border-dark' : 'btn-white text-gray-600'">Khách lẻ</button>
          </div>

          <!-- Khách lẻ: không cần nút lưu thủ công, hệ thống tự động lưu đơn hàng khi thanh toán -->
          <div v-if="activePosOrder.customer_type === 'Khách lẻ'" class="grid grid-cols-12 gap-3">
            <div class="md:col-span-6"><label class="block text-sm font-medium uppercase text-gray-600">Tên khách (tùy chọn)</label><input v-model="activePosOrder.customer_name" type="text" class="sg-input rounded-2" placeholder="Khách lẻ"></div>
            <div class="md:col-span-6"><label class="block text-sm font-medium uppercase text-gray-600">Số điện thoại (tùy chọn)</label><input v-model="activePosOrder.customer_phone" type="tel" class="sg-input rounded-2" placeholder="VD: 0901234567" maxlength="11"></div>
            <div class="col-span-12"><label class="block text-sm font-medium uppercase text-gray-600">Ghi chú</label><textarea v-model="activePosOrder.customer_note" rows="2" class="sg-input rounded-2" placeholder="Ghi chú đơn hàng..."></textarea></div>
          </div>

          <!-- Có tài khoản: chỉ hiện danh sách KHI ĐÃ TÌM KIẾM -->
          <div v-else>
            <label class="block text-sm font-medium uppercase text-gray-600">Tìm khách hàng</label>
            <div class="relative mb-2">
              <i class="icon icon-search absolute text-gray-600" style="left:12px;top:50%;transform:translateY(-50%);"></i>
              <input v-model="posCustomerSearch" type="text" class="sg-input rounded-2 pl-5" placeholder="Nhập tên hoặc SĐT để tìm...">
            </div>
            <!-- Chưa gõ gì: ẩn hoàn toàn danh sách khách hàng -->
            <p v-if="!posCustomerSearch.trim()" class="text-gray-600 text-sm fst-italic mb-0">
              Nhập từ khóa để hiển thị danh sách khách hàng có tên gần giống.
            </p>
            <template v-else>
              <!-- Khung cuộn cao vừa đủ 3 khách hàng gần nhất -->
              <div class="border rounded-2" style="max-height:159px;overflow-y:auto;">
                <div v-if="posCustomerResults.length === 0" class="text-center text-gray-600 text-sm py-3">Không có khách phù hợp.</div>
                <button v-for="c in posCustomerResults" :key="c.id" @click="pickPosCustomer(c)" class="btn w-full text-start flex items-center gap-2 border-0 border-b rounded-0 py-2" style="height:53px;" :class="String(activePosOrder.customer_id) === String(c.id) ? 'bg-light-gray' : 'bg-white'">
                  <span class="rounded-full bg-light-gray inline-flex items-center justify-center shrink-0" style="width:34px;height:34px;"><i class="icon icon-person text-gray-600"></i></span>
                  <span class="grow overflow-hidden">
                    <span class="block text-sm font-medium text-gray-900 text-truncate" v-text="c.name"></span>
                    <span class="block text-gray-600 text-truncate" style="font-size:0.72rem;" v-text="c.phone || '—'"></span>
                  </span>
                  <i v-if="String(activePosOrder.customer_id) === String(c.id)" class="icon icon-check-circle-fill text-gray-900"></i>
                </button>
              </div>
              <p v-if="posCustomerResults.length > 3" class="text-gray-600 mb-0 mt-1" style="font-size:0.72rem;">
                Cuộn để xem thêm (<span v-text="posCustomerResults.length"></span> kết quả)
              </p>
            </template>
          </div>
          <div v-if="posMemberTier" class="mt-3 border rounded-2 p-3 bg-light-gray" aria-live="polite">
            <div class="flex justify-between items-center gap-2 mb-1">
              <strong class="text-sm text-gray-900">Hạng {{ posMemberTier.label }}</strong>
              <span class="badge" :class="posMemberTier.class">Ưu đãi {{ posMemberTier.discountPercent }}%</span>
            </div>
            <p class="text-xs text-gray-600 mb-1">Tổng chi tiêu đã ghi nhận: {{ formatPrice(posMemberTier.customer.spent) }}</p>
            <p v-if="posMemberTier.discountPercent > 0" class="text-xs text-green-700 mb-0">Đã tự áp dụng giảm {{ posMemberTier.discountPercent }}% vào hóa đơn tại quầy.</p>
            <p v-else class="text-xs text-gray-600 mb-0">Hạng Mới chưa có giảm giá thành viên; mốc hạng Mới là {{ formatPrice(posMemberTier.minSpend) }}.</p>
          </div>
          <div v-else-if="activePosOrder.customer_type === 'Khách lẻ'" class="mt-3 text-xs text-gray-600">Khách lẻ không có tài khoản tích lũy nên chưa áp dụng ưu đãi hạng thành viên.</div>
        </div>

        <!-- Sản phẩm -->
        <div class="bg-white rounded-1 shadow-sm p-4">
          <h6 class="font-bold mb-3 text-gray-900"><i class="icon icon-box-seam mr-2"></i>Sản phẩm</h6>
          <div class="relative mb-3">
            <i class="icon icon-search absolute text-gray-600" style="left:12px;top:50%;transform:translateY(-50%);"></i>
            <input v-model="posSearch" type="text" class="sg-input rounded-2 pl-5" placeholder="Tìm theo tên, mã, màu, size...">
          </div>
          <div class="grid grid-cols-12 gap-3" style="max-height:52vh;overflow:auto;">
            <div v-if="posVariants.length === 0" class="col-span-12 text-center text-gray-600 py-4 text-sm">Không tìm thấy sản phẩm.</div>
            <div v-for="v in posVariants" :key="v.id" class="col-span-6 md:col-span-4">
              <div class="admin-pos-product border rounded-2 p-2 h-full flex flex-col">
                <div class="relative mb-2">
                  <img :src="v.image || 'https://via.placeholder.com/160'" class="rounded-2 w-full" style="height:96px;object-fit:cover;" @error="$event.target.src='https://via.placeholder.com/160'">
                  <span class="rounded-full border absolute" :style="{ width:'16px', height:'16px', bottom:'6px', right:'6px', background: v.color_hex || '#d1d5db' }"></span>
                </div>
                <p class="text-sm font-medium mb-0 text-gray-900 text-truncate" v-text="v.product_name"></p>
                <p class="text-gray-600 mb-1 text-truncate" style="font-size:0.72rem;" v-text="[v.color, v.size, v.sku].filter(Boolean).join(' · ')"></p>
                <p class="font-bold text-gray-900 mb-1 text-sm" v-text="formatPrice(v.price)"></p>
                <div class="flex gap-1 mt-auto">
                  <input type="number" min="1" v-model.number="qtyInputs[v.id]" class="sg-input sg-input text-center rounded-2" style="width:52px;" placeholder="1">
                  <button @click="addWithQty(v)" class="btn btn-sm btn-dark rounded-2 grow"><i class="icon icon-plus-lg"></i> Thêm</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- CỘT PHẢI -->
      <div class="lg:col-span-5">
        <!-- Đơn hiện tại -->
        <div class="bg-white rounded-1 shadow-sm p-4">
          <div class="flex justify-between items-center mb-3">
            <h6 class="font-bold mb-0 text-gray-900"><i class="icon icon-receipt mr-2"></i>Đơn hiện tại</h6>
            <div class="flex items-center gap-2">
              <button
                v-if="posInvoiceModal.orderId"
                type="button"
                @click="posInvoiceModal.open = true"
                class="btn btn-sm btn-outline-dark rounded-1 px-2 flex items-center gap-1"
                title="Xem lại hóa đơn vừa xuất"
              >
                <i class="icon icon-receipt"></i>
                <span style="font-size:0.75rem;">Hóa đơn vừa xuất</span>
              </button>
              <span class="badge rounded-1 bg-gray-100 text-gray-600 border" v-text="'#' + activePosOrder.code"></span>
              <button @click="resetPosOrder()" class="btn btn-sm btn-white border rounded-1 px-3" title="Làm mới đơn">
                <i class="icon icon-arrow-counterclockwise"></i>
              </button>
            </div>
          </div>
          <div class="grid grid-cols-12 gap-2 mb-3">
            <div class="col-span-4"><div class="bg-light-gray rounded-2 p-2"><div class="text-gray-600 uppercase" style="font-size:0.62rem;">Khách hàng</div><div class="text-sm font-medium text-truncate" v-text="activePosOrder.customer_name || 'Khách lẻ'"></div></div></div>
            <div class="col-span-4"><div class="bg-light-gray rounded-2 p-2"><div class="text-gray-600 uppercase" style="font-size:0.62rem;">SĐT</div><div class="text-sm font-medium text-truncate" v-text="activePosOrder.customer_phone || '—'"></div></div></div>
            <div class="col-span-4"><div class="bg-light-gray rounded-2 p-2"><div class="text-gray-600 uppercase" style="font-size:0.62rem;">Loại đơn</div><div class="text-sm font-medium">TẠI QUẦY</div></div></div>
          </div>

          <div class="text-gray-600 uppercase mb-2" style="font-size:0.68rem;">Sản phẩm trong đơn</div>
          <div v-if="activePosOrder.cart.length === 0" class="text-center text-gray-600 text-sm py-3 border rounded-2 mb-3">Chưa có sản phẩm trong đơn</div>
          <div v-else class="mb-3" style="max-height:200px;overflow:auto;">
            <div v-for="(c, i) in activePosOrder.cart" :key="i" class="flex items-center gap-2 py-2 border-b">
              <img :src="c.image || 'https://via.placeholder.com/40'" class="rounded-2 border" style="width:40px;height:40px;object-fit:cover;" @error="$event.target.src='https://via.placeholder.com/40'">
              <div class="grow">
                <p class="text-sm font-medium mb-0 text-gray-900 text-truncate" v-text="c.name"></p>
                <p class="text-gray-600 mb-0" style="font-size:0.72rem;" v-text="[c.color, c.size].filter(Boolean).join(' · ') + ' · ' + formatPrice(c.price)"></p>
              </div>
              <input type="number" min="1" @change="validateCartItemQty(c)" v-model.number="c.quantity" class="sg-input sg-input text-center rounded-2" style="width:56px;">
              <button @click="removeCartItem(i)" class="btn btn-sm btn-link text-red-600 p-0"><i class="icon icon-trash"></i></button>
            </div>
          </div>

          <!-- Ưu đãi -->
          <div class="text-gray-600 uppercase mb-2" style="font-size:0.68rem;">Ưu đãi</div>
          <div class="flex gap-2 mb-3">
            <select v-model="activePosOrder.coupon_code" class="sg-input sg-input rounded-2">
              <option value="">Chọn ưu đãi có sẵn...</option>
              <option v-for="d in posCouponList" :key="d.id" :value="d.code" v-text="d.code + ' — ' + (d.name || (d.discount_type === 'Cố định' ? formatPrice(d.value) : d.value + '%'))"></option>
            </select>
            <button @click="applyPosCoupon()" class="btn btn-sm btn-dark rounded-2">Áp dụng</button>
            <button @click="clearPosCoupon()" class="btn btn-sm btn-light border rounded-2">Bỏ</button>
          </div>

          <!-- Tổng -->
          <div class="flex justify-between mb-1 text-sm"><span class="text-gray-600">Tạm tính</span><span class="font-medium" v-text="formatPrice(posSubtotal)"></span></div>
          <div v-if="posCouponDiscountAmount > 0" class="flex justify-between mb-1 text-sm"><span class="text-gray-600">Mã khuyến mãi</span><span class="font-medium text-red-600">− {{ formatPrice(posCouponDiscountAmount) }}</span></div>
          <div v-if="posMemberDiscountAmount > 0" class="flex justify-between mb-1 text-sm"><span class="text-gray-600">Hạng {{ posMemberTier?.label }} ({{ posMemberTier?.discountPercent }}%)</span><span class="font-medium text-red-600">− {{ formatPrice(posMemberDiscountAmount) }}</span></div>
          <div class="flex justify-between mb-2 text-sm"><span class="text-gray-600">Tổng giảm giá</span><span class="font-medium text-red-600" v-text="'- ' + formatPrice(posDiscountAmount)"></span></div>
          <div class="flex justify-between items-center mb-3"><span class="font-bold text-gray-900">Tổng thanh toán</span><h5 class="font-extrabold mb-0 text-gray-900" v-text="formatPrice(posGrandTotal)"></h5></div>

          <!-- Thanh toán -->
          <div class="text-gray-600 uppercase mb-2" style="font-size:0.68rem;">Phương thức thanh toán</div>
          <div class="grid grid-cols-12 gap-2 mb-3">
            <div class="col-span-6"><button @click="activePosOrder.payment_method = 'Tiền mặt'" class="btn w-full rounded-2 border py-2" :class="activePosOrder.payment_method === 'Tiền mặt' ? 'btn-dark text-white border-dark' : 'btn-white text-gray-600'"><i class="icon icon-cash-coin mr-1"></i>Tiền mặt</button></div>
            <div class="col-span-6"><button @click="activePosOrder.payment_method = 'Chuyển khoản'" class="btn w-full rounded-2 border py-2" :class="activePosOrder.payment_method === 'Chuyển khoản' ? 'btn-dark text-white border-dark' : 'btn-white text-gray-600'"><i class="icon icon-bank mr-1"></i>Chuyển khoản</button></div>
          </div>

          <div v-if="activePosOrder.payment_method === 'Tiền mặt'" class="rounded-2 border p-3 mb-3 bg-gray-50">
            <label class="block text-sm font-medium text-gray-700 mb-1" for="pos-cash-given">Tiền khách đưa (VND)</label>
            <input id="pos-cash-given" v-model.number="activePosOrder.cash_given" type="number" min="0" step="1000" class="sg-input rounded-2" placeholder="Nhập số tiền khách đưa">
            <div class="flex justify-between mt-2 text-sm"><span class="text-gray-600">Tiền thừa trả khách</span><strong :class="posChange < 0 ? 'text-red-600' : 'text-green-700'">{{ formatPrice(Math.max(0, posChange)) }}</strong></div>
            <p v-if="Number(activePosOrder.cash_given || 0) < posGrandTotal" class="text-red-600 text-xs mt-1 mb-0">Khách còn thiếu {{ formatPrice(posGrandTotal - (Number(activePosOrder.cash_given) || 0)) }}.</p>
          </div>
          <button @click="checkoutPos()" :disabled="posSubmitting || activePosOrder.cart.length === 0" class="btn btn-dark w-full rounded-2 font-bold py-2"><i class="icon icon-check2-circle mr-2"></i>{{ posSubmitting ? 'Đang xử lý...' : 'Tạo đơn / Thanh toán' }}</button>
        </div>
      </div>
    </div>

    <!-- MODAL QR chuyển khoản tại quầy (hiện 1 lần khi bấm thanh toán) -->
    <Teleport to="body">
      <div v-if="posPayModal.open" class="custom-modal-overlay" @click.self="cancelPosPay()">
        <div class="custom-modal-box fade-in-scale pos-qr-dialog" style="max-width:380px;">
          <div class="p-4 text-center">
            <h6 class="font-bold text-gray-900 mb-1"><i class="icon icon-qr-code mr-2"></i>Quét mã chuyển khoản</h6>
            <p class="text-gray-600 text-sm mb-3">Quét mã để chuyển khoản đúng số tiền và nội dung bên dưới. Sau 30 phút, biểu mẫu và ảnh QR tự làm mới; mã tham chiếu và số tiền vẫn được giữ nguyên để đối soát.</p>
            <div class="mb-3"><span class="text-gray-600 text-sm">Tổng tiền cần thanh toán</span><h4 class="font-extrabold text-gray-900 mb-0" v-text="formatPrice(posPayModal.amount)"></h4></div>
            <img :src="posPayModal.qr" class="pos-qr-image mb-3" alt="Mã QR thanh toán" :key="posPayModal.qrRefreshKey">
            <p class="text-gray-600 text-xs mb-2">Mã tham chiếu: <strong>{{ posPayModal.paymentReference }}</strong></p>
            <div v-if="posPayModal.isBankQr" class="rounded-2 border border-green-200 bg-green-50 text-green-800 text-xs text-left p-3 mb-3">QR chuyển khoản ngân hàng được tạo theo mẫu cấu hình VietQR. Vui lòng kiểm tra số tiền và nội dung chuyển khoản trước khi quét.</div>
            <div class="rounded-2 border p-3 mb-3 text-left" aria-live="polite" :class="posPayModal.result === 'SUCCESS' ? 'border-green-200 bg-green-50 text-green-800' : posPayModal.result === 'FAILED' ? 'border-red-200 bg-red-50 text-red-800' : ['REFUND','REFUNDED'].includes(posPayModal.result) ? 'border-amber-200 bg-amber-50 text-amber-900' : 'border-gray-200 bg-gray-50 text-gray-800'">
              <div class="font-semibold text-sm">{{ posPayModal.result === 'SUCCESS' ? 'Thanh toán thành công' : posPayModal.result === 'FAILED' ? 'Thanh toán thất bại hoặc đã hủy' : posPayModal.result === 'REFUND' ? 'Đang hoàn tiền' : posPayModal.result === 'REFUNDED' ? 'Đã hoàn tiền' : 'Đang chờ xác nhận thanh toán' }}</div>
              <p class="text-xs mt-1 mb-0">Đơn hàng: {{ posPayModal.orderStatus }} · Thanh toán: {{ posPayModal.paymentStatus }}</p>
            </div>
            <div v-if="posPayModal.result === 'PENDING'" class="rounded-2 border bg-light-gray p-3 mb-3" aria-live="polite"><div class="text-gray-600 text-xs mb-1">Tự làm mới biểu mẫu sau</div><div class="font-mono font-bold text-gray-900" style="font-size:1.7rem;letter-spacing:.08em;" v-text="formatPosCountdown(posPayModal.secondsRemaining)"></div></div>
            <div class="grid gap-2">
              <button @click="confirmPosPaid()" class="btn btn-dark rounded-2 font-bold py-2"><i class="icon icon-list-check mr-2"></i>{{ posPayModal.result === 'SUCCESS' ? 'Mở đơn hàng & thanh toán' : 'Đơn hàng & thanh toán để xác nhận' }}</button>
              <button @click="cancelPosPay()" class="btn btn-light border rounded-2">Đóng biểu mẫu</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- MODAL HÓA ĐƠN sau khi thanh toán thành công -->
    <Teleport to="body">
      <div v-if="posInvoiceModal.open" class="custom-modal-overlay" style="z-index:1060;" @click.self="closePosInvoice()">
        <div class="custom-modal-box fade-in-scale" style="max-width:520px;" role="dialog" aria-modal="true" aria-label="Hóa đơn thanh toán">
          <!-- Header hóa đơn -->
          <div class="p-4 border-b flex justify-between items-center bg-gray-50 rounded-t-2">
            <div class="flex items-center gap-2">
              <span class="rounded-full bg-black text-white inline-flex items-center justify-center shrink-0" style="width:32px;height:32px;">
                <i class="icon icon-receipt text-sm"></i>
              </span>
              <div>
                <h6 class="font-bold mb-0 text-gray-900 leading-tight">HÓA ĐƠN BÁN HÀNG</h6>
                <small class="text-gray-600" style="font-size:0.72rem;">Cửa hàng giày dép FREEDOMS</small>
              </div>
            </div>
            <button @click="closePosInvoice()" class="btn btn-sm btn-light border-0" type="button" aria-label="Đóng hóa đơn">
              <i class="icon icon-x-lg"></i>
            </button>
          </div>

          <!-- Thân hóa đơn -->
          <div class="p-4" style="max-height:72vh;overflow-y:auto;">
            <!-- Badge trạng thái thành công -->
            <div class="text-center mb-4 pb-3 border-b">
              <div class="mx-auto mb-2 rounded-full inline-flex items-center justify-center bg-green-50 text-green-700" style="width:50px;height:50px;">
                <i class="icon icon-check-circle-fill" style="font-size:1.75rem;"></i>
              </div>
              <h6 class="font-bold text-gray-900 mb-0">Thanh toán thành công!</h6>
              <p class="text-gray-600 text-sm mb-0">Mã đơn hàng: <strong class="text-gray-900">#{{ posInvoiceModal.orderId }}</strong></p>
            </div>

            <!-- Thông tin đơn hàng & Khách hàng -->
            <div class="bg-light-gray rounded-2 p-3 mb-3 text-sm">
              <div class="flex justify-between mb-1.5">
                <span class="text-gray-600">Thời gian:</span>
                <span class="font-medium text-gray-900">{{ formatDate(posInvoiceModal.created_at) }}</span>
              </div>
              <div class="flex justify-between mb-1.5">
                <span class="text-gray-600">Khách hàng:</span>
                <span class="font-medium text-gray-900">{{ posInvoiceModal.customer_name || 'Khách lẻ' }}</span>
              </div>
              <div v-if="posInvoiceModal.customer_phone" class="flex justify-between mb-1.5">
                <span class="text-gray-600">Số điện thoại:</span>
                <span class="font-medium text-gray-900">{{ posInvoiceModal.customer_phone }}</span>
              </div>
              <div class="flex justify-between mb-1.5">
                <span class="text-gray-600">Phương thức:</span>
                <span class="font-medium text-gray-900">{{ posInvoiceModal.payment_method }}</span>
              </div>
              <div class="flex justify-between mb-1.5">
                <span class="text-gray-600">Thu ngân:</span>
                <span class="font-medium text-gray-900">{{ posInvoiceModal.handled_by || 'Quầy' }}</span>
              </div>
              <div v-if="posInvoiceModal.payment_reference" class="flex justify-between mb-1.5 gap-2">
                <span class="text-gray-600">Mã tham chiếu:</span><span class="font-medium text-gray-900 break-all">{{ posInvoiceModal.payment_reference }}</span>
              </div>
              <template v-if="posInvoiceModal.payment_method === 'Tiền mặt'">
                <div class="flex justify-between mb-1.5"><span class="text-gray-600">Tiền khách đưa:</span><span class="font-medium text-gray-900">{{ formatPrice(posInvoiceModal.cash_received) }}</span></div>
                <div class="flex justify-between"><span class="text-gray-600">Tiền thừa:</span><span class="font-bold text-green-700">{{ formatPrice(posInvoiceModal.cash_change) }}</span></div>
              </template>
            </div>

            <!-- Danh sách sản phẩm -->
            <div class="text-gray-600 uppercase mb-2" style="font-size:0.68rem;letter-spacing:0.5px;">Chi tiết sản phẩm</div>
            <div class="border rounded-2 mb-3 overflow-hidden">
              <table class="w-full text-sm">
                <thead>
                  <tr class="bg-gray-100 text-gray-600 border-b" style="font-size:0.75rem;">
                    <th class="py-2 px-3 text-start">Sản phẩm</th>
                    <th class="py-2 px-2 text-center" style="width:48px;">SL</th>
                    <th class="py-2 px-3 text-end" style="width:90px;">Đơn giá</th>
                    <th class="py-2 px-3 text-end" style="width:100px;">Thành tiền</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(item, i) in posInvoiceModal.items" :key="i" class="border-b last:border-b-0">
                    <td class="py-2 px-3">
                      <div class="font-medium text-gray-900 leading-snug">{{ item.name }}</div>
                      <div class="text-gray-600" style="font-size:0.72rem;">{{ [item.color, item.size ? 'Size ' + item.size : ''].filter(Boolean).join(' · ') }}</div>
                    </td>
                    <td class="py-2 px-2 text-center text-gray-700">{{ item.quantity }}</td>
                    <td class="py-2 px-3 text-end text-gray-700">{{ formatPrice(item.price) }}</td>
                    <td class="py-2 px-3 text-end font-medium text-gray-900">{{ formatPrice(item.price * item.quantity) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Tổng kết tiền -->
            <div class="border-t pt-2.5 text-sm space-y-1.5">
              <div class="flex justify-between text-gray-600">
                <span>Tạm tính:</span>
                <span class="font-medium text-gray-900">{{ formatPrice(posInvoiceModal.subtotal) }}</span>
              </div>
              <div v-if="posInvoiceModal.discount > 0" class="flex justify-between text-red-600">
                <span>Giảm giá:</span>
                <span class="font-medium">- {{ formatPrice(posInvoiceModal.discount) }}</span>
              </div>
              <div class="flex justify-between items-baseline pt-2 border-t mt-2">
                <span class="font-bold text-gray-900 text-base">Tổng thanh toán:</span>
                <h4 class="font-extrabold text-gray-900 mb-0">{{ formatPrice(posInvoiceModal.total) }}</h4>
              </div>
            </div>

            <p class="text-center text-gray-600 text-xs fst-italic mt-4 mb-0">Cảm ơn quý khách đã mua hàng tại FREEDOMS!</p>
          </div>

          <!-- Nút bấm Xem / In / Đóng -->
          <div class="p-4 border-t flex gap-2 bg-gray-50 rounded-b-2">
            <button @click="closePosInvoice()" type="button" class="btn btn-light border rounded-2 font-medium">
              <i class="icon icon-x-lg mr-1"></i> Đóng
            </button>
            <button @click="openPosOrderInPayments()" type="button" class="btn btn-outline-dark rounded-2 grow font-bold">
              <i class="icon icon-list-check mr-1"></i> Đơn hàng & thanh toán
            </button>
            <button @click="printPosInvoice()" type="button" class="btn btn-dark rounded-2 grow font-bold">
              <i class="icon icon-printer mr-1"></i> In hóa đơn
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style>
/* Teleport moves the dialog to body, so modal primitives must not require an .admin-shell ancestor. */
.pos-qr-dialog { border-radius: 16px !important; overflow: hidden; }
.pos-qr-dialog .pos-qr-image { width: 240px; height: 240px; object-fit: contain; border-radius: 12px; border: 1px solid #e5e7eb; background: #fff; }
</style>
