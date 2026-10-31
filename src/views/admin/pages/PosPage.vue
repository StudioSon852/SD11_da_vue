<!-- Trang: Bán Hàng Tại Quầy (POS) -->
<script setup>
import { ref, computed, watch } from 'vue'
import {
  db, posVariantCatalog, notify, activePosOrder, resetPosOrder,
  posPayModal, confirmPosPaid, cancelPosPay,
  posSearch, addToCart, removeCartItem,
  posSubtotal, posCouponDiscountAmount, posMemberDiscountAmount, posMemberTier, posDiscountAmount, posGrandTotal, posChange,
  posCouponList, applyPosCoupon, clearPosCoupon,
  posCustomerSearch, posCustomerResults, pickPosCustomer,
  checkoutPos, formatPrice, formatDate, validateCartItemQty, posSubmitting,
  posInvoiceModal, closePosInvoice, printPosInvoice, openPosOrderInPayments, formatPosCountdown,
} from '../adminStore'

const showOrderDrawer = ref(false)
const showFilters = ref(false)
const sortBy = ref('featured')
const activeFilter = ref('')
const selectedCategory = ref('')
const selectedBrand = ref('')
const selectedSports = ref([])
const selectedMaterials = ref([])
const selectedColors = ref([])
const selectedSizes = ref([])
const currentPage = ref(1)
const pageSize = 10

const selectedProduct = ref(null)
const selectedColor = ref('')
const selectedSize = ref('')
const selectedQty = ref(1)
const selectedProductModalOpen = ref(false)

const normalizeText = (value) => String(value ?? '')
  .normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').toLocaleLowerCase('vi-VN').trim()

const productCatalog = computed(() => db.products
  .filter((product) => product.active !== false && product.active !== 0 && product.active !== '0')
  .map((product) => {
    const variants = posVariantCatalog.value.filter((variant) => String(variant.product_id) === String(product.id))
    if (Array.isArray(product.variants) && product.variants.length > 0 && variants.length === 0) return null
    const lowest = variants.reduce((best, variant) => !best || Number(variant.price) < Number(best.price) ? variant : best, null)
    const colors = [...new Map(variants.filter((v) => v.color).map((v) => [v.color, { name: v.color, hex: v.color_hex, image: v.image }])).values()]
    const sizes = [...new Set(variants.map((v) => String(v.size || '')).filter(Boolean))]
    return {
      ...product,
      id_product: product.id,
      product_name: product.name,
      brand_name: product.brand || '',
      category_name: product.category_name || product.category || '',
      sport: db.categories.find((category) => String(category.id) === String(product.category_id))?.sport || '',
      image_url: product.image_url || '',
      variants,
      colors,
      sizes,
      f_colors: colors.map((color) => color.name),
      f_sizes: sizes,
      display_price: lowest?.price ?? Number(product.sale_price || product.price || 0),
      old_price: lowest?.old_price ?? null,
      lowest_variant: lowest,
      variant_count: variants.length,
    }
  })
  .filter(Boolean))

const categoryOptions = computed(() => [...new Set(productCatalog.value.map((p) => p.category_name).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'vi')))
const brandOptions = computed(() => [...new Set(productCatalog.value.map((p) => p.brand_name).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'vi')))
const sportOptions = computed(() => [...new Set(productCatalog.value.map((p) => p.sport).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'vi')))
const materialOptions = computed(() => [...new Set(productCatalog.value.map((p) => p.material_name).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'vi')))
const colorOptions = computed(() => [...new Set(productCatalog.value.flatMap((p) => p.f_colors))].sort((a, b) => a.localeCompare(b, 'vi')))
const sizeOptions = computed(() => [...new Set(productCatalog.value.flatMap((p) => p.f_sizes))].sort((a, b) => a.localeCompare(b, 'vi', { numeric: true })))

const filteredProducts = computed(() => {
  const q = normalizeText(posSearch.value)
  let list = productCatalog.value.filter((product) => {
    if (q) {
      const fields = [product.product_name, product.brand_name, product.category_name, product.material_name, product.parent_sku,
        product.sport, ...product.variants.flatMap((variant) => [variant.sku, variant.color, variant.size])]
      if (!fields.some((field) => normalizeText(field).includes(q))) return false
    }
    if (selectedCategory.value && product.category_name !== selectedCategory.value) return false
    if (selectedBrand.value && product.brand_name !== selectedBrand.value) return false
    if (selectedSports.value.length && !selectedSports.value.includes(product.sport)) return false
    if (selectedMaterials.value.length && !selectedMaterials.value.includes(product.material_name)) return false
    if (selectedColors.value.length && !selectedColors.value.some((color) => product.f_colors.includes(color))) return false
    if (selectedSizes.value.length && !selectedSizes.value.some((size) => product.f_sizes.includes(size))) return false
    if (activeFilter.value === 'sale' && !product.variants.some((variant) => Number(variant.old_price) > Number(variant.price))) return false
    if (activeFilter.value === 'new') {
      const created = product.created_at ? new Date(product.created_at).getTime() : 0
      if (!created || created < Date.now() - 30 * 24 * 60 * 60 * 1000) return false
    }
    return true
  })
  if (sortBy.value === 'price-asc') list.sort((a, b) => a.display_price - b.display_price)
  else if (sortBy.value === 'price-desc') list.sort((a, b) => b.display_price - a.display_price)
  else if (sortBy.value === 'name') list.sort((a, b) => (a.product_name || '').localeCompare(b.product_name || '', 'vi'))
  else list.sort((a, b) => Number(Boolean(b.is_featured)) - Number(Boolean(a.is_featured)) || (a.product_name || '').localeCompare(b.product_name || '', 'vi'))
  return list
})
const totalPages = computed(() => Math.max(1, Math.ceil(filteredProducts.value.length / pageSize)))
const paginatedProducts = computed(() => filteredProducts.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize))
const pageNumbers = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1))
const orderItemCount = computed(() => activePosOrder.value.cart.reduce((total, item) => total + (Number(item.quantity) || 0), 0))
const orderHasItems = computed(() => activePosOrder.value.cart.length > 0)

watch([posSearch, selectedCategory, selectedBrand, selectedSports, selectedMaterials, selectedColors, selectedSizes, activeFilter, sortBy], () => { currentPage.value = 1 }, { deep: true })
watch(totalPages, (pages) => { if (currentPage.value > pages) currentPage.value = pages })

function toggleFilter(list, value) {
  const index = list.indexOf(value)
  if (index >= 0) list.splice(index, 1)
  else list.push(value)
}
function clearFilters() {
  posSearch.value = ''
  selectedCategory.value = ''
  selectedBrand.value = ''
  selectedSports.value = []
  selectedMaterials.value = []
  selectedColors.value = []
  selectedSizes.value = []
  activeFilter.value = ''
  sortBy.value = 'featured'
}
function openProduct(product) {
  selectedProduct.value = product
  selectedColor.value = product.colors[0]?.name || product.variants[0]?.color || ''
  const sizes = product.variants.filter((variant) => !selectedColor.value || variant.color === selectedColor.value).map((variant) => String(variant.size || '')).filter(Boolean)
  const uniqueSizes = [...new Set(sizes)]
  selectedSize.value = uniqueSizes.length === 1 ? uniqueSizes[0] : ''
  selectedQty.value = 1
  selectedProductModalOpen.value = true
}
const availableProductSizes = computed(() => {
  if (!selectedProduct.value) return []
  const matching = selectedProduct.value.variants.filter((variant) => !selectedColor.value || variant.color === selectedColor.value)
  return [...new Set(matching.map((variant) => String(variant.size || '')).filter(Boolean))]
})
const selectedVariant = computed(() => {
  if (!selectedProduct.value) return null
  const variants = selectedProduct.value.variants || []
  if (!variants.length) return null
  return variants.find((variant) => (!selectedColor.value || variant.color === selectedColor.value) && (!availableProductSizes.value.length || String(variant.size) === String(selectedSize.value))) || null
})
function changeProductColor(color) {
  selectedColor.value = color
  const sizes = availableProductSizes.value
  selectedSize.value = sizes.length === 1 ? sizes[0] : ''
}
function addSelectedProduct() {
  const product = selectedProduct.value
  if (!product) return
  let variant = selectedVariant.value
  if (product.variants.length && availableProductSizes.value.length && !selectedSize.value) {
    notify('Vui lòng chọn kích thước trước khi chọn mua.', 'warning')
    return
  }
  if (!variant && product.variants.length === 1) variant = product.variants[0]
  if (!variant) {
    notify('Sản phẩm hiện chưa có biến thể hợp lệ để bán tại quầy.', 'warning')
    return
  }
  const quantity = Math.min(99, Math.max(1, Math.floor(Number(selectedQty.value) || 1)))
  selectedQty.value = quantity
  addToCart(variant, quantity)
  notify(`${product.product_name} đã được thêm vào đơn hàng tại quầy.`, 'success')
  selectedProductModalOpen.value = false
}
function addWithQty(variant) {
  addToCart(variant, 1)
  notify(`${variant.product_name} đã được thêm vào đơn hàng tại quầy.`, 'success')
}
function updatePage(page) {
  currentPage.value = Math.min(totalPages.value, Math.max(1, Number(page) || 1))
  if (typeof window !== 'undefined') window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <div class="fade-in employee-pos-storefront">
    <div class="pos-workspace-grid">
    <aside class="pos-customer-panel">
      <div class="pos-panel-heading">
        <div>
          <span class="pos-eyebrow">FREEDOMS · BÁN HÀNG TẠI QUẦY</span>
          <h2>Khách hàng</h2>
          <p>Chọn hồ sơ khách để áp dụng quyền lợi thành viên hoặc nhập thông tin khách lẻ.</p>
        </div>
        <button type="button" class="pos-order-open" @click="showOrderDrawer = true">
          <i class="icon icon-receipt"></i>
          Đơn của tôi
          <span class="pos-order-count">{{ orderItemCount }}</span>
        </button>
      </div>

      <div class="pos-customer-type">
        <button type="button" :class="{ active: activePosOrder.customer_type === 'Có tài khoản' }" @click="activePosOrder.customer_type = 'Có tài khoản'; activePosOrder.customer_id = null; activePosOrder.customer_name = ''; activePosOrder.customer_phone = ''">Có tài khoản</button>
        <button type="button" :class="{ active: activePosOrder.customer_type === 'Khách lẻ' }" @click="activePosOrder.customer_type = 'Khách lẻ'; activePosOrder.customer_id = null">Khách lẻ</button>
      </div>

      <div v-if="activePosOrder.customer_type === 'Khách lẻ'" class="pos-customer-fields">
        <label>Tên khách (tùy chọn)<input v-model.trim="activePosOrder.customer_name" type="text" maxlength="100" placeholder="Tên khách hàng"></label>
        <label>Số điện thoại (tùy chọn)<input v-model.trim="activePosOrder.customer_phone" type="tel" inputmode="numeric" maxlength="15" placeholder="VD: 0901234567"></label>
        <label class="pos-customer-note">Ghi chú<textarea v-model.trim="activePosOrder.customer_note" rows="2" maxlength="500" placeholder="Ghi chú cho đơn hàng..."></textarea></label>
      </div>

      <div v-else class="pos-account-customer">
        <label for="pos-customer-search">Tìm khách hàng theo tên hoặc số điện thoại</label>
        <div class="pos-search-input"><i class="icon icon-search"></i><input id="pos-customer-search" v-model="posCustomerSearch" type="search" placeholder="Nhập tên hoặc số điện thoại..."></div>
        <p v-if="!posCustomerSearch.trim()" class="pos-helper">Nhập từ khóa để tìm tài khoản khách hàng trong dữ liệu chung.</p>
        <div v-else class="pos-customer-results">
          <p v-if="posCustomerResults.length === 0" class="pos-helper">Không tìm thấy khách hàng phù hợp.</p>
          <button v-for="customer in posCustomerResults" :key="customer.id" type="button" :class="{ selected: String(activePosOrder.customer_id) === String(customer.id) }" @click="pickPosCustomer(customer)">
            <span class="pos-customer-avatar"><i class="icon icon-person"></i></span>
            <span><strong>{{ customer.name }}</strong><small>{{ customer.phone || customer.email || 'Chưa có số điện thoại' }}</small></span>
            <i v-if="String(activePosOrder.customer_id) === String(customer.id)" class="icon icon-check-circle-fill"></i>
          </button>
        </div>
        <p v-if="activePosOrder.customer_id" class="pos-selected-customer">Đã chọn: {{ activePosOrder.customer_name || posCustomerResults.find(c => String(c.id) === String(activePosOrder.customer_id))?.name || 'Khách hàng' }}</p>
      </div>

      <div v-if="posMemberTier" class="pos-member-info" aria-live="polite">
        <div><strong>Hạng {{ posMemberTier.label }}</strong><span>Ưu đãi {{ posMemberTier.discountPercent }}%</span></div>
        <p>Tổng chi tiêu đã ghi nhận: {{ formatPrice(posMemberTier.customer.spent) }}</p>
        <small>{{ posMemberTier.discountPercent > 0 ? `Sẽ trừ ${posMemberTier.discountPercent}% theo hạng ${posMemberTier.label} khi thanh toán.` : `Hạng Mới chưa có giảm giá thành viên; mốc tiếp theo ${formatPrice(posMemberTier.minSpend)}.` }}</small>
      </div>
      <p v-else-if="activePosOrder.customer_type === 'Khách lẻ'" class="pos-helper pos-guest-note">Khách lẻ không có tài khoản tích lũy nên chưa áp dụng ưu đãi hạng thành viên.</p>
    </aside>

    <section class="pos-catalog-panel">
      <div class="pos-catalog-header">
        <div>
          <span class="pos-eyebrow">DỮ LIỆU SẢN PHẨM ĐỒNG BỘ TỪ CỬA HÀNG</span>
          <h2>Sản phẩm</h2>
          <p>{{ filteredProducts.length }} sản phẩm phù hợp</p>
        </div>
        <div class="pos-catalog-actions">
          <button type="button" class="pos-filter-button" @click="showFilters = !showFilters"><i class="icon icon-funnel"></i>{{ showFilters ? 'Ẩn bộ lọc' : 'Bộ lọc' }}</button>
          <select v-model="sortBy" aria-label="Sắp xếp sản phẩm">
            <option value="featured">Nổi bật</option><option value="price-asc">Giá thấp → cao</option><option value="price-desc">Giá cao → thấp</option><option value="name">Tên A → Z</option>
          </select>
        </div>
      </div>

      <div class="pos-catalog-toolbar">
        <div class="pos-filter-chips">
          <button type="button" :class="{ active: !activeFilter }" @click="activeFilter = ''">Tất cả</button>
          <button type="button" :class="{ active: activeFilter === 'sale' }" @click="activeFilter = activeFilter === 'sale' ? '' : 'sale'">Sale</button>
          <button type="button" :class="{ active: activeFilter === 'new' }" @click="activeFilter = activeFilter === 'new' ? '' : 'new'">Hàng mới</button>
          <button v-for="sport in sportOptions" :key="sport" type="button" :class="{ active: selectedSports.includes(sport) }" @click="toggleFilter(selectedSports, sport)">{{ sport }}</button>
          <button v-for="brand in brandOptions" :key="brand" type="button" :class="{ active: selectedBrand === brand }" @click="selectedBrand = selectedBrand === brand ? '' : brand">{{ brand }}</button>
          <select v-model="selectedCategory" aria-label="Lọc danh mục"><option value="">Tất cả danh mục</option><option v-for="category in categoryOptions" :key="category" :value="category">{{ category }}</option></select>
        </div>
        <label class="pos-product-search"><i class="icon icon-search"></i><input v-model="posSearch" type="search" placeholder="Tìm tên, mã, màu, size..."></label>
      </div>

      <div v-if="showFilters" class="pos-advanced-filters">
        <div><strong>Màu sắc</strong><div class="pos-option-wrap"><button v-for="color in colorOptions" :key="color" type="button" :class="{ active: selectedColors.includes(color) }" @click="toggleFilter(selectedColors, color)">{{ color }}</button></div></div>
        <div><strong>Kích cỡ</strong><div class="pos-option-wrap"><button v-for="size in sizeOptions" :key="size" type="button" :class="{ active: selectedSizes.includes(size) }" @click="toggleFilter(selectedSizes, size)">{{ size }}</button></div></div>
        <div><strong>Chất liệu</strong><div class="pos-option-wrap"><button v-for="material in materialOptions" :key="material" type="button" :class="{ active: selectedMaterials.includes(material) }" @click="toggleFilter(selectedMaterials, material)">{{ material }}</button></div></div>
        <button type="button" class="pos-clear-filters" @click="clearFilters">Xóa bộ lọc</button>
      </div>

      <div v-if="!filteredProducts.length" class="pos-empty-products">
        <span>⌕</span><h3>Không tìm thấy sản phẩm</h3><p>Thử thay đổi từ khóa hoặc bộ lọc để xem thêm sản phẩm.</p><button type="button" @click="clearFilters">Xóa bộ lọc</button>
      </div>
      <div v-else class="pos-product-grid">
        <article v-for="product in paginatedProducts" :key="product.id_product" class="pos-product-card">
          <button type="button" class="pos-product-media" @click="openProduct(product)" :aria-label="`Xem chi tiết ${product.product_name}`">
            <span v-if="product.old_price" class="pos-sale-tag">SALE</span>
            <img :src="product.image_url || '/img/hero-sneakers.jpg'" :alt="product.product_name" @error="$event.target.src = '/img/hero-sneakers.jpg'">
          </button>
          <div class="pos-product-card-body">
            <small class="pos-brand-name">{{ product.brand_name || product.category_name || 'FREEDOMS' }}</small>
            <button type="button" class="pos-product-name" @click="openProduct(product)">{{ product.product_name }}</button>
            <small class="pos-product-meta">{{ product.colors.length }} màu · {{ product.sizes.length }} kích cỡ</small>
            <div class="pos-card-price">
              <del v-if="product.old_price">{{ formatPrice(product.old_price) }}</del>
              <strong>{{ formatPrice(product.display_price) }}</strong>
            </div>
            <div class="pos-card-actions"><button type="button" class="pos-buy-button" @click="openProduct(product)">Chọn mua</button><button type="button" class="pos-detail-button" @click="openProduct(product)">Xem chi tiết</button></div>
          </div>
        </article>
      </div>

      <nav v-if="totalPages > 1" class="pos-pagination" aria-label="Phân trang sản phẩm">
        <button type="button" :disabled="currentPage === 1" @click="updatePage(currentPage - 1)">‹</button>
        <button v-for="page in pageNumbers" :key="page" type="button" :class="{ active: page === currentPage }" @click="updatePage(page)">{{ page }}</button>
        <button type="button" :disabled="currentPage === totalPages" @click="updatePage(currentPage + 1)">›</button>
      </nav>
    </section>
    </div>

    <!-- Chi tiết sản phẩm tại quầy: chỉ thao tác với đơn POS, không đụng giỏ khách hàng. -->
    <Teleport to="body">
      <div v-if="selectedProductModalOpen && selectedProduct" class="pos-product-modal-backdrop" @click.self="selectedProductModalOpen = false">
        <section class="pos-product-modal" role="dialog" aria-modal="true" :aria-label="`Chi tiết ${selectedProduct.product_name}`">
          <button type="button" class="pos-modal-close" aria-label="Đóng" @click="selectedProductModalOpen = false"><i class="icon icon-x-lg"></i></button>
          <div class="pos-product-modal-image"><img :src="selectedVariant?.image || selectedProduct.image_url || '/img/hero-sneakers.jpg'" :alt="selectedProduct.product_name" @error="$event.target.src = '/img/hero-sneakers.jpg'"></div>
          <div class="pos-product-modal-content">
            <small class="pos-brand-name">{{ selectedProduct.brand_name || selectedProduct.category_name || 'FREEDOMS' }}</small>
            <h2>{{ selectedProduct.product_name }}</h2>
            <p class="pos-detail-meta">{{ [selectedProduct.category_name, selectedProduct.material_name, selectedProduct.parent_sku].filter(Boolean).join(' · ') }}</p>
            <p class="pos-product-description">{{ selectedProduct.description || 'Sản phẩm chính hãng từ dữ liệu danh mục cửa hàng.' }}</p>
            <div v-if="selectedProduct.colors.length" class="pos-modal-section"><strong>Màu sắc <span v-if="selectedColor">: {{ selectedColor }}</span></strong><div class="pos-modal-options"><button v-for="color in selectedProduct.colors" :key="color.name" type="button" :class="{ active: selectedColor === color.name }" @click="changeProductColor(color.name)"><span class="pos-color-dot" :style="{ background: color.hex || '#d1d5db' }"></span>{{ color.name }}</button></div></div>
            <div v-if="availableProductSizes.length" class="pos-modal-section"><strong>Kích cỡ <span v-if="selectedSize">: {{ selectedSize }}</span></strong><div class="pos-modal-options"><button v-for="size in availableProductSizes" :key="size" type="button" :class="{ active: selectedSize === size }" @click="selectedSize = size">{{ size }}</button></div></div>
            <div v-if="selectedVariant" class="pos-selected-price"><del v-if="selectedVariant.old_price">{{ formatPrice(selectedVariant.old_price) }}</del><strong>{{ formatPrice(selectedVariant.price) }}</strong><small v-if="selectedVariant.sku">SKU: {{ selectedVariant.sku }}</small></div>
            <div class="pos-modal-bottom"><label>Số lượng<input v-model.number="selectedQty" type="number" min="1" max="99"></label><button type="button" class="pos-buy-button" @click="addSelectedProduct"><i class="icon icon-bag-plus"></i> Chọn mua</button></div>
            <p class="pos-detail-footnote">Sản phẩm được thêm vào đơn hàng tại quầy; giỏ hàng và tài khoản mua sắm của khách không bị thay đổi.</p>
          </div>
        </section>
      </div>
    </Teleport>

    <!-- Đơn của tôi: ngăn trượt lớn, có thể ẩn/hiện như giỏ hàng. -->
    <Teleport to="body">
      <div v-if="showOrderDrawer" class="pos-order-backdrop" @click.self="showOrderDrawer = false">
        <aside class="pos-order-drawer" role="dialog" aria-modal="true" aria-label="Đơn hàng tại quầy">
          <header class="pos-order-drawer-header">
            <div><span class="pos-eyebrow">FREEDOMS · QUẦY THANH TOÁN</span><h2>Đơn của tôi</h2><small>Mã đơn tạm: #{{ activePosOrder.code }}</small></div>
            <div class="pos-order-header-actions">
              <button v-if="posInvoiceModal.orderId" type="button" class="pos-invoice-link" @click="posInvoiceModal.open = true; showOrderDrawer = false"><i class="icon icon-receipt"></i> Hóa đơn vừa xuất</button>
              <button type="button" class="pos-reset-order" title="Làm mới đơn" @click="resetPosOrder()"><i class="icon icon-arrow-counterclockwise"></i></button>
              <button type="button" class="pos-modal-close-inline" aria-label="Đóng đơn" @click="showOrderDrawer = false"><i class="icon icon-x-lg"></i></button>
            </div>
          </header>
          <div class="pos-order-drawer-body">
            <div class="pos-order-customer-summary"><div><small>Khách hàng</small><strong>{{ activePosOrder.customer_name || 'Khách lẻ' }}</strong></div><div><small>Số điện thoại</small><strong>{{ activePosOrder.customer_phone || '—' }}</strong></div><div><small>Loại đơn</small><strong>TẠI QUẦY</strong></div></div>
            <div class="pos-order-section-title">Sản phẩm trong đơn <span>{{ orderItemCount }} sản phẩm</span></div>
            <div v-if="!orderHasItems" class="pos-order-empty">Chưa có sản phẩm. Chọn “Chọn mua” ở danh sách sản phẩm để thêm vào đơn.</div>
            <div v-else class="pos-order-items">
              <div v-for="(item, index) in activePosOrder.cart" :key="item.key" class="pos-order-item">
                <img :src="item.image || '/img/hero-sneakers.jpg'" :alt="item.name" @error="$event.target.src = '/img/hero-sneakers.jpg'">
                <div class="pos-order-item-info"><strong>{{ item.name }}</strong><small>{{ [item.color, item.size ? `Size ${item.size}` : '', item.sku].filter(Boolean).join(' · ') }}</small><b>{{ formatPrice(item.price) }}</b></div>
                <input v-model.number="item.quantity" type="number" min="1" max="99" aria-label="Số lượng" @change="validateCartItemQty(item)">
                <button type="button" class="pos-remove-item" aria-label="Xóa sản phẩm" @click="removeCartItem(index)"><i class="icon icon-trash"></i></button>
              </div>
            </div>

            <div class="pos-order-section-title">Ưu đãi</div>
            <div class="pos-coupon-row"><select v-model="activePosOrder.coupon_code"><option value="">Chọn mã ưu đãi...</option><option v-for="discount in posCouponList" :key="discount.id" :value="discount.code">{{ discount.code }} — {{ discount.name || (discount.discount_type === 'Cố định' ? formatPrice(discount.value) : `${discount.value}%`) }}</option></select><button type="button" @click="applyPosCoupon">Áp dụng</button><button type="button" class="secondary" @click="clearPosCoupon">Bỏ</button></div>

            <div class="pos-order-totals"><div><span>Tạm tính</span><strong>{{ formatPrice(posSubtotal) }}</strong></div><div v-if="posCouponDiscountAmount > 0"><span>Mã khuyến mãi</span><strong class="discount">− {{ formatPrice(posCouponDiscountAmount) }}</strong></div><div v-if="posMemberDiscountAmount > 0"><span>Trừ {{ posMemberTier?.discountPercent }}% hạng {{ posMemberTier?.label }}</span><strong class="discount">− {{ formatPrice(posMemberDiscountAmount) }}</strong></div><div><span>Tổng giảm giá</span><strong class="discount">− {{ formatPrice(posDiscountAmount) }}</strong></div><div class="grand-total"><span>Tổng thanh toán</span><strong>{{ formatPrice(posGrandTotal) }}</strong></div></div>

            <div class="pos-order-section-title">Phương thức thanh toán</div>
            <div class="pos-payment-methods"><button type="button" :class="{ active: activePosOrder.payment_method === 'Tiền mặt' }" @click="activePosOrder.payment_method = 'Tiền mặt'"><i class="icon icon-cash-coin"></i> Tiền mặt</button><button type="button" :class="{ active: activePosOrder.payment_method === 'Chuyển khoản' }" @click="activePosOrder.payment_method = 'Chuyển khoản'"><i class="icon icon-bank"></i> Chuyển khoản</button></div>
            <div v-if="activePosOrder.payment_method === 'Tiền mặt'" class="pos-cash-box"><label for="pos-cash-given">Tiền khách đưa (VND)</label><input id="pos-cash-given" v-model.number="activePosOrder.cash_given" type="number" min="0" step="1000" placeholder="Nhập số tiền khách đưa"><div><span>Tiền thừa trả khách</span><strong :class="posChange < 0 ? 'negative' : ''">{{ formatPrice(Math.max(0, posChange)) }}</strong></div><small v-if="Number(activePosOrder.cash_given || 0) < posGrandTotal">Khách còn thiếu {{ formatPrice(posGrandTotal - (Number(activePosOrder.cash_given) || 0)) }}.</small></div>
          </div>
          <footer class="pos-order-drawer-footer"><button type="button" class="pos-continue-shopping" @click="showOrderDrawer = false">Tiếp tục chọn sản phẩm</button><button type="button" class="pos-checkout-button" :disabled="posSubmitting || !orderHasItems" @click="checkoutPos()"><i class="icon icon-check2-circle"></i> {{ posSubmitting ? 'Đang xử lý...' : 'Tạo đơn / Thanh toán' }}</button></footer>
        </aside>
      </div>
    </Teleport>

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
              <div v-if="posInvoiceModal.coupon_discount > 0" class="flex justify-between text-red-600">
                <span>Mã khuyến mãi:</span>
                <span class="font-medium">- {{ formatPrice(posInvoiceModal.coupon_discount) }}</span>
              </div>
              <div v-if="posInvoiceModal.member_discount > 0" class="flex justify-between text-red-600">
                <span>Ưu đãi hạng {{ posInvoiceModal.member_tier || 'thành viên' }} ({{ posInvoiceModal.member_discount_percent }}%):</span>
                <span class="font-medium">- {{ formatPrice(posInvoiceModal.member_discount) }}</span>
              </div>
              <div v-if="posInvoiceModal.discount > posInvoiceModal.coupon_discount + posInvoiceModal.member_discount" class="flex justify-between text-red-600">
                <span>Giảm giá khác:</span>
                <span class="font-medium">- {{ formatPrice(posInvoiceModal.discount - posInvoiceModal.coupon_discount - posInvoiceModal.member_discount) }}</span>
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

<style scoped>
.employee-pos-storefront { color: #171717; width: 100%; }
.pos-workspace-grid { display:grid; grid-template-columns:minmax(265px, 305px) minmax(0, 1fr); gap:20px; align-items:start; }
.pos-customer-panel { position:sticky; top:18px; min-width:0; }
.pos-customer-panel .pos-panel-heading { align-items:flex-start; flex-direction:column; }
.pos-customer-panel .pos-order-open { width:100%; justify-content:center; }
.pos-customer-fields { grid-template-columns:1fr; }
.pos-customer-note { grid-column:auto; }
.pos-account-customer { max-width:none; }
.pos-customer-type { display:grid; grid-template-columns:1fr 1fr; }
.pos-customer-panel, .pos-catalog-panel { background: #fff; border: 1px solid #e8e8e8; padding: clamp(16px, 2.3vw, 28px); margin-bottom: 22px; }
.pos-panel-heading, .pos-catalog-header { display:flex; justify-content:space-between; align-items:flex-end; gap:18px; margin-bottom:18px; }
.pos-eyebrow { display:block; color:#777; font-size:10px; font-weight:800; letter-spacing:.18em; text-transform:uppercase; margin-bottom:7px; }
.pos-panel-heading h2, .pos-catalog-header h2 { font-family:Georgia,serif; font-size:clamp(24px,3vw,34px); font-weight:500; line-height:1.1; margin:0; color:#0e0e0e; }
.pos-panel-heading p, .pos-catalog-header p { color:#777; font-size:12px; margin:7px 0 0; }
.pos-order-open { display:inline-flex; align-items:center; gap:10px; border:1px solid #111; background:#111; color:#fff; padding:12px 18px; font-size:12px; font-weight:800; transition:background .18s ease; white-space:nowrap; }
.pos-order-open:hover { background:#343434; }
.pos-order-count { display:inline-grid; place-items:center; min-width:23px; height:23px; padding:0 5px; border-radius:50%; background:#fff; color:#111; font-size:11px; }
.pos-customer-type { display:flex; gap:8px; margin-bottom:16px; }
.pos-customer-type button, .pos-filter-chips button { border:1px solid #dedede; border-radius:100px; background:#fff; padding:9px 16px; color:#444; font-size:11px; font-weight:700; }
.pos-customer-type button.active, .pos-filter-chips button.active { background:#111; color:#fff; border-color:#111; }
.pos-customer-fields { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:13px 16px; }
.pos-customer-fields label, .pos-account-customer label { display:grid; gap:7px; color:#555; font-size:10px; font-weight:800; letter-spacing:.06em; text-transform:uppercase; }
.pos-customer-fields input, .pos-customer-fields textarea, .pos-account-customer input { width:100%; min-width:0; border:1px solid #dedede; border-radius:0; padding:11px 12px; color:#111; background:#fff; font-size:13px; font-weight:400; letter-spacing:normal; text-transform:none; outline:none; }
.pos-customer-fields input:focus, .pos-customer-fields textarea:focus, .pos-account-customer input:focus { border-color:#111; box-shadow:0 0 0 2px #11111112; }
.pos-customer-note { grid-column:1/-1; }
.pos-customer-note textarea { resize:vertical; }
.pos-account-customer { max-width:680px; }
.pos-search-input, .pos-product-search { position:relative; display:flex; align-items:center; gap:9px; border:1px solid #dedede; border-radius:100px; padding:0 14px; min-height:42px; color:#777; }
.pos-search-input i, .pos-product-search i { flex:none; }
.pos-search-input input, .pos-product-search input { border:0; background:transparent; padding:9px 0; outline:none; width:100%; color:#111; box-shadow:none!important; }
.pos-helper { font-size:12px; color:#777; margin:9px 0 0; }
.pos-customer-results { margin-top:8px; max-height:175px; overflow:auto; border:1px solid #eee; }
.pos-customer-results button { display:flex; width:100%; align-items:center; gap:11px; border:0; border-bottom:1px solid #eee; background:#fff; padding:9px 12px; text-align:left; }
.pos-customer-results button:last-child { border-bottom:0; }
.pos-customer-results button.selected { background:#f6f6f6; }
.pos-customer-results button>span:nth-child(2) { display:grid; gap:3px; flex:1; }
.pos-customer-results strong { color:#111; font-size:12px; }
.pos-customer-results small { color:#777; font-size:11px; }
.pos-customer-avatar { display:grid; place-items:center; width:34px; height:34px; border-radius:50%; background:#f1f1f1; }
.pos-selected-customer { margin:8px 0 0; font-size:11px; color:#247444; }
.pos-member-info { margin-top:15px; padding:13px 15px; background:#f7f7f7; border:1px solid #e5e5e5; }
.pos-member-info>div { display:flex; justify-content:space-between; align-items:center; gap:12px; }
.pos-member-info strong { font-size:13px; }
.pos-member-info span { font-size:11px; font-weight:800; }
.pos-member-info p, .pos-member-info small { display:block; color:#666; font-size:11px; margin:5px 0 0; }
.pos-guest-note { padding-top:13px; }
.pos-catalog-header { margin-bottom:20px; }
.pos-catalog-actions { display:flex; gap:9px; align-items:center; }
.pos-filter-button, .pos-catalog-actions select, .pos-filter-chips select { min-height:39px; border:1px solid #dedede; border-radius:100px; padding:0 14px; background:#fff; font-size:11px; font-weight:700; color:#222; }
.pos-filter-button { display:flex; align-items:center; gap:7px; }
.pos-catalog-toolbar { display:flex; justify-content:space-between; align-items:center; gap:14px; padding:14px 0; margin-bottom:20px; border-top:1px solid #e9e9e9; border-bottom:1px solid #e9e9e9; }
.pos-filter-chips { display:flex; flex-wrap:wrap; align-items:center; gap:8px; }
.pos-filter-chips button { padding:8px 13px; }
.pos-filter-chips select { min-height:35px; }
.pos-product-search { width:min(330px,100%); flex:none; }
.pos-product-search input { font-size:12px; }
.pos-advanced-filters { display:grid; grid-template-columns:1fr 1fr 1fr auto; gap:20px; align-items:start; padding:17px; margin:-4px 0 20px; background:#f7f7f7; }
.pos-advanced-filters strong { display:block; color:#666; font-size:10px; letter-spacing:.12em; text-transform:uppercase; margin-bottom:10px; }
.pos-option-wrap { display:flex; flex-wrap:wrap; gap:7px; }
.pos-option-wrap button { border:1px solid #ddd; background:#fff; padding:7px 11px; border-radius:100px; color:#444; font-size:11px; }
.pos-option-wrap button.active { border-color:#111; background:#111; color:#fff; }
.pos-clear-filters { border:0; background:transparent; text-decoration:underline; color:#444; font-size:11px; font-weight:700; align-self:end; padding:7px; }
.pos-product-grid { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:26px 17px; }
.pos-product-card { min-width:0; display:flex; flex-direction:column; }
.pos-product-media { position:relative; display:block; width:100%; aspect-ratio:4/5; border:0; padding:0; overflow:hidden; background:#f1f1f1; cursor:pointer; }
.pos-product-media img { width:100%; height:100%; object-fit:cover; transition:transform .4s ease; }
.pos-product-card:hover .pos-product-media img { transform:scale(1.045); }
.pos-sale-tag { position:absolute; top:10px; left:10px; z-index:1; padding:5px 8px; background:#111; color:#fff; font-size:9px; font-weight:800; letter-spacing:.08em; }
.pos-product-card-body { display:flex; flex:1; flex-direction:column; gap:5px; padding-top:10px; }
.pos-brand-name { color:#777; font-size:9px; font-weight:800; letter-spacing:.15em; text-transform:uppercase; }
.pos-product-name { min-height:39px; border:0; padding:0; background:transparent; text-align:left; color:#141414; font-family:Georgia,serif; font-size:15px; line-height:1.35; }
.pos-product-meta { color:#888; font-size:10px; }
.pos-card-price { display:flex; flex-wrap:wrap; align-items:baseline; gap:8px; margin:4px 0 8px; }
.pos-card-price del, .pos-selected-price del { color:#888; font-size:11px; }
.pos-card-price strong, .pos-selected-price strong { color:#111; font-size:14px; font-weight:900; }
.pos-card-actions { display:flex; gap:6px; margin-top:auto; }
.pos-card-actions button { flex:1; min-height:34px; padding:7px 6px; font-size:10px; font-weight:800; }
.pos-buy-button { display:inline-flex; align-items:center; justify-content:center; gap:7px; border:1px solid #111; background:#111; color:#fff; padding:11px 14px; font-size:11px; font-weight:800; }
.pos-buy-button:hover { background:#333; }
.pos-detail-button { border:1px solid #ddd; background:#fff; color:#222; }
.pos-detail-button:hover { border-color:#111; }
.pos-empty-products { padding:75px 20px; text-align:center; border:1px solid #eee; }
.pos-empty-products>span { display:block; font-size:35px; color:#ccc; }
.pos-empty-products h3 { margin:8px 0 4px; font-family:Georgia,serif; font-size:23px; }
.pos-empty-products p { margin:0; color:#777; font-size:12px; }
.pos-empty-products button { margin-top:17px; background:#111; color:#fff; border:0; padding:10px 18px; font-size:11px; font-weight:800; }
.pos-pagination { display:flex; justify-content:center; gap:7px; margin-top:30px; }
.pos-pagination button { min-width:35px; height:35px; border:1px solid #ddd; background:#fff; color:#222; font-size:11px; }
.pos-pagination button.active { background:#111; border-color:#111; color:#fff; }
.pos-pagination button:disabled { opacity:.35; cursor:not-allowed; }
.pos-product-modal-backdrop, .pos-order-backdrop { position:fixed; inset:0; z-index:1050; display:flex; justify-content:center; align-items:center; padding:20px; background:rgba(0,0,0,.48); }
.pos-product-modal { position:relative; display:grid; grid-template-columns:minmax(260px,.9fr) minmax(320px,1.1fr); width:min(930px,96vw); max-height:90vh; overflow:auto; background:#fff; box-shadow:0 20px 70px #0003; }
.pos-modal-close, .pos-modal-close-inline { display:grid; place-items:center; width:36px; height:36px; border:1px solid #ddd; background:#fff; color:#222; }
.pos-modal-close { position:absolute; top:12px; right:12px; z-index:2; }
.pos-product-modal-image { min-height:430px; background:#f1f1f1; }
.pos-product-modal-image img { width:100%; height:100%; max-height:80vh; object-fit:cover; }
.pos-product-modal-content { padding:38px 34px 28px; }
.pos-product-modal-content h2 { margin:9px 0; font-family:Georgia,serif; font-weight:500; font-size:30px; line-height:1.2; }
.pos-detail-meta, .pos-product-description { color:#777; font-size:12px; line-height:1.7; }
.pos-product-description { margin:14px 0 20px; color:#444; }
.pos-modal-section { margin:17px 0; }
.pos-modal-section>strong { display:block; margin-bottom:9px; font-size:11px; color:#333; }
.pos-modal-options { display:flex; flex-wrap:wrap; gap:7px; }
.pos-modal-options button { display:flex; align-items:center; gap:7px; border:1px solid #ddd; background:#fff; padding:8px 11px; font-size:11px; }
.pos-modal-options button.active { border-color:#111; box-shadow:inset 0 0 0 1px #111; }
.pos-color-dot { width:13px; height:13px; border:1px solid #ddd; border-radius:50%; }
.pos-selected-price { display:flex; align-items:baseline; gap:9px; margin:22px 0 15px; }
.pos-selected-price strong { font-size:20px; }
.pos-selected-price small { color:#888; font-size:10px; }
.pos-modal-bottom { display:flex; align-items:end; gap:12px; }
.pos-modal-bottom label { display:grid; gap:6px; color:#555; font-size:10px; font-weight:800; }
.pos-modal-bottom input { width:78px; height:40px; border:1px solid #ddd; padding:7px; }
.pos-modal-bottom .pos-buy-button { min-height:40px; flex:1; }
.pos-detail-footnote { color:#888; font-size:10px; line-height:1.5; margin:13px 0 0; }
.pos-order-backdrop { justify-content:flex-end; align-items:stretch; padding:0; z-index:1060; }
.pos-order-drawer { display:flex; flex-direction:column; width:min(700px,96vw); height:100%; background:#fff; box-shadow:-15px 0 50px #0002; }
.pos-order-drawer-header { display:flex; justify-content:space-between; gap:16px; align-items:center; padding:23px 25px; border-bottom:1px solid #e8e8e8; }
.pos-order-drawer-header h2 { margin:0; font-family:Georgia,serif; font-size:28px; font-weight:500; }
.pos-order-drawer-header small { color:#777; font-size:11px; }
.pos-order-header-actions { display:flex; align-items:center; gap:7px; }
.pos-invoice-link, .pos-reset-order { min-height:36px; border:1px solid #ddd; background:#fff; padding:0 10px; font-size:10px; font-weight:700; }
.pos-invoice-link { display:flex; align-items:center; gap:6px; }
.pos-order-drawer-body { flex:1; overflow:auto; padding:20px 25px 28px; }
.pos-order-customer-summary { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:9px; margin-bottom:22px; }
.pos-order-customer-summary>div { display:grid; gap:5px; padding:11px; background:#f7f7f7; min-width:0; }
.pos-order-customer-summary small { color:#777; font-size:9px; text-transform:uppercase; }
.pos-order-customer-summary strong { color:#222; font-size:11px; overflow-wrap:anywhere; }
.pos-order-section-title { display:flex; justify-content:space-between; align-items:center; margin:19px 0 10px; color:#222; font-size:11px; font-weight:900; text-transform:uppercase; letter-spacing:.06em; }
.pos-order-section-title span { color:#888; font-size:10px; font-weight:600; text-transform:none; }
.pos-order-empty { padding:28px 15px; border:1px dashed #ddd; color:#777; text-align:center; font-size:12px; }
.pos-order-items { max-height:260px; overflow:auto; border-top:1px solid #eee; }
.pos-order-item { display:flex; align-items:center; gap:11px; padding:11px 0; border-bottom:1px solid #eee; }
.pos-order-item img { width:58px; height:58px; object-fit:cover; background:#f4f4f4; }
.pos-order-item-info { display:grid; flex:1; min-width:0; gap:4px; }
.pos-order-item-info strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:12px; }
.pos-order-item-info small { color:#777; font-size:10px; }
.pos-order-item-info b { font-size:11px; }
.pos-order-item input { width:56px; border:1px solid #ddd; padding:7px 4px; text-align:center; font-size:11px; }
.pos-remove-item { border:0; background:transparent; color:#a33; padding:6px; }
.pos-coupon-row { display:flex; gap:7px; }
.pos-coupon-row select { min-width:0; flex:1; border:1px solid #ddd; padding:9px; font-size:11px; }
.pos-coupon-row button { border:1px solid #111; background:#111; color:#fff; padding:0 12px; font-size:10px; font-weight:800; }
.pos-coupon-row button.secondary { border-color:#ddd; background:#fff; color:#333; }
.pos-order-totals { padding:15px 0; margin-top:16px; border-top:1px solid #e8e8e8; }
.pos-order-totals>div { display:flex; justify-content:space-between; gap:15px; margin:8px 0; font-size:12px; }
.pos-order-totals span { color:#777; }
.pos-order-totals strong { color:#222; }
.pos-order-totals .discount { color:#b42318; }
.pos-order-totals .grand-total { padding-top:13px; margin-top:13px; border-top:1px solid #ddd; align-items:baseline; }
.pos-order-totals .grand-total span { color:#111; font-size:14px; font-weight:900; }
.pos-order-totals .grand-total strong { font-size:22px; font-weight:900; }
.pos-payment-methods { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
.pos-payment-methods button { min-height:42px; border:1px solid #ddd; background:#fff; color:#333; font-size:11px; font-weight:800; }
.pos-payment-methods button.active { border-color:#111; background:#111; color:#fff; }
.pos-cash-box { margin-top:12px; padding:13px; background:#f7f7f7; border:1px solid #e8e8e8; }
.pos-cash-box label { display:block; margin-bottom:7px; font-size:10px; font-weight:800; }
.pos-cash-box input { width:100%; border:1px solid #ddd; padding:10px; font-size:12px; }
.pos-cash-box>div { display:flex; justify-content:space-between; margin-top:10px; font-size:11px; }
.pos-cash-box strong { color:#247444; }
.pos-cash-box strong.negative, .pos-cash-box small { color:#b42318; }
.pos-cash-box small { display:block; margin-top:7px; font-size:10px; }
.pos-order-drawer-footer { display:grid; grid-template-columns:.85fr 1.15fr; gap:9px; padding:16px 25px; border-top:1px solid #e8e8e8; background:#fff; }
.pos-continue-shopping { min-height:44px; border:1px solid #ddd; background:#fff; color:#333; font-size:11px; font-weight:800; }
.pos-checkout-button { min-height:44px; display:flex; justify-content:center; align-items:center; gap:8px; border:1px solid #111; background:#111; color:#fff; font-size:11px; font-weight:900; }
.pos-checkout-button:disabled { opacity:.4; cursor:not-allowed; }
.pos-qr-dialog { border-radius:16px!important; overflow:hidden; }
.pos-qr-dialog .pos-qr-image { width:240px; height:240px; object-fit:contain; border-radius:12px; border:1px solid #e5e7eb; background:#fff; }
@media (max-width:1050px) { .pos-workspace-grid { grid-template-columns:1fr; } .pos-customer-panel { position:static; } .pos-customer-panel .pos-panel-heading { align-items:center; flex-direction:row; } .pos-customer-panel .pos-order-open { width:auto; } .pos-customer-fields { grid-template-columns:repeat(2,minmax(0,1fr)); } .pos-customer-note { grid-column:1/-1; } .pos-account-customer { max-width:680px; } }
@media (max-width:1200px) { .pos-product-grid { grid-template-columns:repeat(4,minmax(0,1fr)); } .pos-catalog-toolbar { align-items:flex-start; flex-direction:column; } .pos-product-search { width:100%; } }
@media (max-width:850px) { .pos-product-grid { grid-template-columns:repeat(3,minmax(0,1fr)); } .pos-product-modal { grid-template-columns:1fr; width:min(600px,96vw); } .pos-product-modal-image { min-height:230px; max-height:320px; } .pos-product-modal-image img { max-height:320px; } .pos-product-modal-content { padding:25px 22px; } }
@media (max-width:600px) { .pos-panel-heading, .pos-catalog-header { align-items:flex-start; flex-direction:column; } .pos-order-open { width:100%; justify-content:center; } .pos-customer-fields { grid-template-columns:1fr; } .pos-customer-note { grid-column:auto; } .pos-product-grid { grid-template-columns:repeat(2,minmax(0,1fr)); gap:21px 12px; } .pos-advanced-filters { grid-template-columns:1fr; } .pos-catalog-actions { width:100%; } .pos-catalog-actions>* { flex:1; justify-content:center; } .pos-filter-chips select { max-width:100%; } .pos-product-modal-backdrop { padding:9px; } .pos-product-modal { max-height:94vh; width:100%; } .pos-product-modal-content h2 { font-size:24px; } .pos-order-drawer { width:100vw; } .pos-order-drawer-header, .pos-order-drawer-body { padding-left:16px; padding-right:16px; } .pos-order-drawer-footer { padding:12px 16px; grid-template-columns:1fr; } .pos-order-customer-summary { grid-template-columns:1fr 1fr; } .pos-invoice-link { display:none; } }
</style>
<style scoped>
/* Teleport moves the dialog to body, so modal primitives must not require an .admin-shell ancestor. */
.pos-qr-dialog { border-radius: 16px !important; overflow: hidden; }
.pos-qr-dialog .pos-qr-image { width: 240px; height: 240px; object-fit: contain; border-radius: 12px; border: 1px solid #e5e7eb; background: #fff; }
</style>
