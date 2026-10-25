import { computed, reactive, watch } from "vue";
import { currentUser } from "./authStore";
import { api } from "../services/apiClient";

// ============================================================
// STORAGE
// ============================================================

const STORAGE_KEY = "shoegroup_carts_v4";
const GUEST_CART_KEY = "__guest__";

// ============================================================
// LẤY ID TÀI KHOẢN
// ============================================================

const getUserId = (user) => {
  if (!user) return GUEST_CART_KEY;

  const id =
    user.id_account ??
    user.account_id ??
    user.id_user ??
    user.user_id ??
    user.id ??
    user.ID ??
    user.email ??
    user.username;

  if (id === undefined || id === null || String(id).trim() === "") {
    return GUEST_CART_KEY;
  }

  return `user_${String(id).trim()}`;
};

const sanitizeCartItem = (item) => {
  if (!item || typeof item !== "object") return null;
  const productId = Number(item.id_product ?? item.product_id ?? item.product?.id_product);
  const quantity = Number(item.quantity);
  const unitPrice = Number(item.unitPrice ?? item.price ?? item.product?.price);
  if (!Number.isSafeInteger(productId) || productId <= 0) return null;
  if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > 1000000) return null;
  if (!Number.isFinite(unitPrice) || unitPrice < 0 || unitPrice > 1e12) return null;
  const variantRaw = item.variant_id;
  const variantId = variantRaw === null || variantRaw === undefined || String(variantRaw).trim() === ""
    ? null
    : Number(variantRaw);
  if (variantId !== null && (!Number.isSafeInteger(variantId) || variantId <= 0)) return null;
  const detailId = String(item.id_product_detail ?? `${productId}_${variantId ?? "default"}`).trim();
  if (!detailId || detailId.length > 300) return null;
  // Strip legacy inventory flags from carts created by the former ShoeGroup build.
  const cleanItem = { ...item };
  delete cleanItem.stockQuantity;
  delete cleanItem.stockReserved;
  delete cleanItem.isOutOfStock;
  delete cleanItem.hasInsufficientStock;
  delete cleanItem.stockAvailability;
  delete cleanItem.stockCheckedAt;
  return { ...cleanItem, id_product: productId, variant_id: variantId, id_product_detail: detailId, quantity, unitPrice };
};

// ============================================================
// LOAD TẤT CẢ GIỎ
// ============================================================

const loadAllCarts = () => {
  if (typeof localStorage === "undefined") {
    return {};
  }

  try {
    const raw = localStorage.getItem(STORAGE_KEY);

    if (!raw) {
      return {};
    }

    const data = JSON.parse(raw);

    if (
      !data ||
      typeof data !== "object" ||
      Array.isArray(data)
    ) {
      return {};
    }

    return data;
  } catch (error) {
    console.error("Không thể đọc giỏ hàng:", error);
    return {};
  }
};

// ============================================================
// SAVE TẤT CẢ GIỎ
// ============================================================

const saveAllCarts = (carts) => {
  if (typeof localStorage === "undefined") {
    return;
  }

  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify(carts),
    );
  } catch (error) {
    console.error("Không thể lưu giỏ hàng:", error);
  }
};

// ============================================================
// LOAD GIỎ CỦA TÀI KHOẢN
// ============================================================

const loadCartForUser = (user) => {
  const carts = loadAllCarts();
  const userKey = getUserId(user);

  if (!Array.isArray(carts[userKey])) {
    return [];
  }

  return carts[userKey].map(sanitizeCartItem).filter(Boolean);
};

// ============================================================
// SAVE GIỎ CỦA TÀI KHOẢN
// ============================================================

const saveCartForUser = (userKey, items) => {
  const carts = loadAllCarts();

  carts[userKey] = JSON.parse(
    JSON.stringify(items || []),
  );

  saveAllCarts(carts);
};

// ============================================================
// USER HIỆN TẠI
// ============================================================

let activeUserKey = getUserId(currentUser.value);

// ============================================================
// CART STATE
// ============================================================

export const cartState = reactive({
  items: loadCartForUser(currentUser.value),
  isMiniCartOpen: false,
  isDrawerOpen: false,
});


// ============================================================
// THEO DÕI ĐỔI TÀI KHOẢN
// ============================================================
//
// Ví dụ:
//
// Tài khoản A:
// cart = [giày A]
//
// Logout
//
// Tài khoản B:
// cart = [giày B]
//
// Hai tài khoản hoàn toàn riêng nhau.
// ============================================================

watch(
  currentUser,
  (newUser) => {
    const newUserKey = getUserId(newUser);

    if (newUserKey === activeUserKey) {
      return;
    }

    // Lưu giỏ tài khoản cũ
    saveCartForUser(
      activeUserKey,
      cartState.items,
    );

    // Chuyển sang tài khoản mới
    activeUserKey = newUserKey;

    const newCart = loadCartForUser(newUser);

    cartState.items.splice(
      0,
      cartState.items.length,
      ...newCart,
    );

    cartState.isMiniCartOpen = false;
  },
);

// ============================================================
// TỰ ĐỘNG LƯU GIỎ
// ============================================================

watch(
  () => cartState.items,
  (items) => {
    saveCartForUser(
      activeUserKey,
      items,
    );
  },
  {
    deep: true,
  },
);

// ============================================================
// FORMAT TIỀN
// ============================================================

export const formatCurrency = (value) => {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(Number(value || 0));
};

// ============================================================
// CART ITEMS
// ============================================================

export const cartItems = computed(() => {
  const items = cartState.items.map((item) => ({
    item,
    subtotal:
      Number(item.unitPrice || 0) *
      Number(item.quantity || 0),
  }));
  return items.map(({ item, subtotal }) => ({ ...item, subtotal }));
});

// ============================================================
// CART COUNT
// ============================================================

export const cartCount = computed(() => {
  return cartState.items.reduce(
    (total, item) =>
      total + Number(item.quantity || 0),
    0,
  );
});

// ============================================================
// SUBTOTAL
// ============================================================

export const cartSubtotal = computed(() => {
  return cartItems.value.reduce(
    (total, item) =>
      total + Number(item.subtotal || 0),
    0,
  );
});

// ============================================================
// SHIPPING
// ============================================================

export const cartShippingFee = computed(() => {
  return cartCount.value > 0
    ? 30000
    : 0;
});

// ============================================================
// TOTAL
// ============================================================

export const cartTotal = computed(() => {
  return (
    cartSubtotal.value +
    cartShippingFee.value
  );
});

// ============================================================
// ADD TO CART
// ============================================================

export const addToCart = async (payload) => {
  const {
    product,
    quantity = 1,
    size = {},
    color = {},
    variantId = null,
  } = payload || {};

  // ----------------------------------------------------------
  // KIỂM TRA PRODUCT
  // ----------------------------------------------------------

  if (!product) {
    return {
      ok: false,
      message: "Sản phẩm không hợp lệ.",
    };
  }

  // ----------------------------------------------------------
  // PRODUCT ID
  // ----------------------------------------------------------

  const productId =
    product.id_product ??
    product.id ??
    product.ProductID;
  const normalizedProductId = Number(productId);
  if (!Number.isSafeInteger(normalizedProductId) || normalizedProductId <= 0) {
    return { ok: false, message: "Mã sản phẩm không hợp lệ." };
  }

  // ----------------------------------------------------------
  // PRODUCT NAME
  // ----------------------------------------------------------

  const productName =
    product.product_name ??
    product.name ??
    product.ProductName ??
    "Sản phẩm";

  // ----------------------------------------------------------
  // PRICE
  // ----------------------------------------------------------

  const basePrice = Number(
    product.price ??
    product.BasePrice ??
    0,
  );

  const salePrice = Number(
    product.sale_price ??
    product.SalePrice ??
    0,
  );
  if (!Number.isFinite(basePrice) || basePrice < 0 || !Number.isFinite(salePrice) || salePrice < 0 || (salePrice > 0 && salePrice > basePrice)) {
    return { ok: false, message: "Giá sản phẩm không hợp lệ." };
  }

  const productPrice =
    salePrice > 0
      ? salePrice
      : basePrice;

  // ----------------------------------------------------------
  // IMAGE
  // ----------------------------------------------------------

  const productImage =
    color.image ??
    color.image_url ??
    product.image_url ??
    product.ImageURL ??
    product.image ??
    "";

  // ----------------------------------------------------------
  // SIZE
  // ----------------------------------------------------------

  const sizeName =
    size.size_name ??
    size.SizeName ??
    size.name ??
    size.size ??
    "42";

  // ----------------------------------------------------------
  // COLOR
  // ----------------------------------------------------------

  const colorName =
    color.color_label ??
    color.color_name ??
    color.ColorName ??
    color.name ??
    "Tiêu chuẩn";

  const colorHex =
    color.color_hex ??
    color.hex ??
    "";

  // ----------------------------------------------------------
  // VARIANT ID
  // ----------------------------------------------------------

  const normalizedVariantId =
    variantId ??
    payload.variant_id ??
    product.variant_id ??
    product.id_variant ??
    null;
  const numericVariantId = normalizedVariantId === null || normalizedVariantId === undefined || String(normalizedVariantId).trim() === ""
    ? null
    : Number(normalizedVariantId);
  if (numericVariantId !== null && (!Number.isSafeInteger(numericVariantId) || numericVariantId <= 0)) {
    return { ok: false, message: "Mã biến thể không hợp lệ." };
  }

  // ----------------------------------------------------------
  // ATTRIBUTES
  // ----------------------------------------------------------

  const attributes = {
    material_name:
      product.material_name ??
      product.MaterialName ??
      "",

    sole_name:
      product.sole_name ??
      product.SoleName ??
      "",

    cushioning_name:
      product.cushioning_name ??
      product.CushioningName ??
      "",

    brand_name:
      product.brand_name ??
      product.BrandName ??
      "",

    category_name:
      product.category_name ??
      product.category ??
      "",

    sport:
      product.sport ??
      product.Sport ??
      "",

    collection_name:
      product.collection_name ??
      product.CollectionName ??
      "",
  };

  // ==========================================================
  // QUANTITY
  // ==========================================================

  const requestedQuantity =
    Number(quantity);

  if (
    !Number.isFinite(
      requestedQuantity,
    ) ||
    !Number.isInteger(requestedQuantity) ||
    requestedQuantity < 1
  ) {
    return {
      ok: false,
      message: "Số lượng không hợp lệ.",
    };
  }

  // ==========================================================
  // ID CHI TIẾT GIỎ
  // ==========================================================
  //
  // Có variant ID:
  //
  // 10_variant_101
  // 10_variant_102
  //
  // => 2 biến thể khác nhau.
  //
  // Không có variant ID:
  //
  // product + size + color
  //
  // ==========================================================

  const detailId =
    numericVariantId !== null
      ? `${normalizedProductId}_variant_${numericVariantId}`
      : `${normalizedProductId}_${String(sizeName)}_${String(colorName)}`;

  // ==========================================================
  // TÌM ITEM ĐÃ CÓ
  // ==========================================================

  const existingItem =
    cartState.items.find(
      (item) =>
        item.id_product_detail ===
        detailId,
    );

  // ==========================================================
  // ITEM ĐÃ CÓ
  // ==========================================================

  if (existingItem) {
    const currentQuantity =
      Number(existingItem.quantity || 0);

    const newQuantity =
      currentQuantity +
      requestedQuantity;

    if (newQuantity > 99) {
      return { ok: false, message: "Mỗi biến thể tối đa 99 sản phẩm trong một giỏ hàng." };
    }
    existingItem.quantity =
      newQuantity;

    existingItem.variant_id = numericVariantId;
    delete existingItem.stockQuantity;
    delete existingItem.stockReserved;
    delete existingItem.isOutOfStock;
    delete existingItem.hasInsufficientStock;
    delete existingItem.stockAvailability;
    delete existingItem.stockCheckedAt;

    existingItem.unitPrice =
      productPrice;

    existingItem.product = {
      ...existingItem.product,

      product_name:
        productName,

      price:
        productPrice,

      image_url:
        productImage,

      ...attributes,
    };

    return {
      ok: true,
      message:
        "Đã cập nhật số lượng.",
    };
  }

  // ==========================================================
  // ITEM MỚI
  // ==========================================================

  if (requestedQuantity > 99) {
    return { ok: false, message: "Mỗi biến thể tối đa 99 sản phẩm trong một giỏ hàng." };
  }

  // ==========================================================
  // THÊM CART ITEM
  // ==========================================================

  cartState.items.unshift({
    // ID duy nhất của dòng giỏ
    id_product_detail:
      detailId,

    // Product
    id_product:
      normalizedProductId,

    // Variant thật
    variant_id:
      numericVariantId,

    // Product snapshot
    product: {
      id_product:
        normalizedProductId,

      product_name:
        productName,

      price:
        productPrice,

      image_url:
        productImage,

      ...attributes,
    },

    // Size
    size: {
      size_name:
        String(sizeName),
    },

    // Color
    color: {
      color_label:
        colorName,

      color_name:
        colorName,

      color_hex:
        colorHex,
    },

    // Attributes
    attributes,

    // Quantity trong cart
    quantity:
      requestedQuantity,

    // Giá tại thời điểm thêm
    unitPrice:
      productPrice,

  });

  return {
    ok: true,
    message:
      "Đã thêm vào giỏ hàng.",
  };
};

// ============================================================
// INCREASE
// ============================================================

export const increaseQuantity = async (
  detailId,
) => {
  const item =
    cartState.items.find(
      (i) =>
        i.id_product_detail ===
        detailId,
    );

  if (!item) {
    return {
      ok: false,
      message:
        "Không tìm thấy sản phẩm trong giỏ.",
    };
  }

  const quantity = Number(item.quantity || 0);
  if (quantity >= 99) return { ok: false, message: "Số lượng tối đa là 99 sản phẩm cho mỗi biến thể." };

  item.quantity = quantity + 1;

  return {
    ok: true,
    message:
      "Đã tăng số lượng.",
  };
};

// ============================================================
// DECREASE
// ============================================================

export const decreaseQuantity = async (
  detailId,
) => {
  const item =
    cartState.items.find(
      (i) =>
        i.id_product_detail ===
        detailId,
    );

  if (!item) {
    return {
      ok: false,
      message:
        "Không tìm thấy sản phẩm trong giỏ.",
    };
  }

  const quantity =
    Number(
      item.quantity || 0,
    );

  if (quantity <= 1) {
    return {
      ok: false,
      message:
        "Số lượng tối thiểu là 1.",
    };
  }

  item.quantity = quantity - 1;

  return {
    ok: true,
    message:
      "Đã giảm số lượng.",
  };
};

// ============================================================
// REMOVE
// ============================================================

export const removeFromCart = async (
  detailId,
) => {
  const index =
    cartState.items.findIndex(
      (item) =>
        item.id_product_detail ===
        detailId,
    );

  if (index !== -1) {
    cartState.items.splice(
      index,
      1,
    );
  }
  return { ok: true };
};

// ============================================================
// CLEAR CART
// ============================================================

export const clearCart = async () => {
  cartState.items.splice(
    0,
    cartState.items.length,
  );
  return { ok: true };
};

// ============================================================
// MINI CART
// ============================================================

export const showMiniCart = () => {
  cartState.isMiniCartOpen =
    true;
};

export const hideMiniCart = () => {
  cartState.isMiniCartOpen =
    false;
};

export const toggleMiniCart = () => {
  cartState.isMiniCartOpen =
    !cartState.isMiniCartOpen;
};

// ============================================================
// DRAWER CART (slide-over, không làm mất trang nền)
// ============================================================

export const showDrawer = () => {
  cartState.isDrawerOpen = true;
  cartState.isMiniCartOpen = false;
  if (typeof document !== 'undefined') document.body.style.overflow = 'hidden';
};

export const hideDrawer = () => {
  cartState.isDrawerOpen = false;
  if (typeof document !== 'undefined') document.body.style.overflow = '';
};

export const toggleDrawer = () => {
  if (cartState.isDrawerOpen) hideDrawer();
  else showDrawer();
};
