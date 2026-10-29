const clean = (value) => String(value ?? "").trim()

const zaloPhone = clean(import.meta.env.VITE_ZALO_PHONE) || "0868636420"
const configuredZaloUrl = clean(import.meta.env.VITE_ZALO_URL)

export const STORE_CONTACT = Object.freeze({
  zaloPhone,
  // Set VITE_ZALO_URL only if the shop uses a custom Zalo link; otherwise it
  // follows VITE_ZALO_PHONE so changing the phone in .env.local is enough.
  zaloUrl: configuredZaloUrl || `https://zalo.me/${zaloPhone}`,
  phone: clean(import.meta.env.VITE_STORE_PHONE),
  email: clean(import.meta.env.VITE_STORE_EMAIL),
  address: clean(import.meta.env.VITE_STORE_ADDRESS) || "249 Xã Đàn, Đống Đa, Hà Nội",
})
