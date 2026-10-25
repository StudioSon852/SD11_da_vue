/* ============================================================
 * apiClient.js - Lop goi API dung chung cho toan bo frontend
 * ------------------------------------------------------------
 * - Mac dinh goi /api qua Vite proxy den Spring Boot :8080; khong hard-code host.
 * - Tu dong gan token JWT vao header Authorization.
 * - Token duoc luu trong sessionStorage de moi tab co phien rieng.
 * - Tu dong dang xuat tab hien tai khi token het han (401).
 *
 * Cach dung (code moi nen dung cai nay):
 *   import { api } from "@/services/apiClient";
 *   const products = await api.get("/products?page=1&limit=12");
 *   await api.post("/orders", payload);
 *
 * Code cu goi localhost:5000 se duoc httpInterceptor doi thanh /api de di qua proxy.
 * ============================================================ */

export const API_BASE_URL = (
  import.meta.env?.VITE_API_BASE_URL || "/api"
).replace(/\/+$/, "");

const TOKEN_KEY = "shoegroup_token";
const USER_KEY = "shoegroup_current_user";

function tabStorage() {
  try { return typeof window !== "undefined" ? window.sessionStorage : null; }
  catch { return null; }
}

export const getToken = () => {
  const storage = tabStorage();
  if (!storage) return null;
  try {
    const direct = storage.getItem(TOKEN_KEY);
    if (direct) return direct;
    const user = JSON.parse(storage.getItem(USER_KEY) || storage.getItem("user") || "null");
    return user?.token || storage.getItem("token") || null;
  } catch {
    return null;
  }
};

export const setToken = (token) => {
  const storage = tabStorage();
  if (!storage) return;
  try {
    if (token) storage.setItem(TOKEN_KEY, token);
    else storage.removeItem(TOKEN_KEY);
  } catch { /* ignore unavailable tab storage */ }
};

export const clearToken = () => setToken(null);

let onUnauthorized = null;
export const setUnauthorizedHandler = (fn) => {
  onUnauthorized = fn;
};

function isPublicAuthRequest(url) {
  try {
    const pathname = new URL(url, typeof window !== "undefined" ? window.location.origin : "http://localhost").pathname;
    return /\/(?:auth\/)?(?:login|register|forgot-password|reset-password)\/?$/i.test(pathname);
  } catch {
    return false;
  }
}

async function request(method, path, body, options = {}) {
  const url = /^https?:\/\//i.test(path)
    ? path
    : `${API_BASE_URL}${path.startsWith("/") ? "" : "/"}${path}`;

  const headers = { ...(options.headers || {}) };
  if (body !== undefined && !(body instanceof FormData))
    headers["Content-Type"] = "application/json";

  // Public authentication calls must not carry an old/expired JWT. Remove any
  // caller-supplied Authorization header too, then only attach tokens to protected APIs.
  const publicAuthRequest = isPublicAuthRequest(url);
  if (publicAuthRequest) {
    for (const key of Object.keys(headers)) {
      if (key.toLowerCase() === "authorization") delete headers[key];
    }
  } else {
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(url, {
    method,
    headers,
    body:
      body === undefined
        ? undefined
        : body instanceof FormData
          ? body
          : JSON.stringify(body),
    signal: options.signal,
  });

  if (res.status === 401 && !isPublicAuthRequest(url)) {
    clearToken();
    if (typeof onUnauthorized === "function") onUnauthorized();
  }

  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }

  if (!res.ok) {
    const serverMessage = data && (data.message || data.detail || data.error || data.title);
    const responseType = res.headers.get("content-type") || "";
    const proxyFailure = /ECONNREFUSED|socket hang up|http proxy error|proxy error/i.test(String(data ?? text));
    const unavailableHtml = res.status >= 500 && !responseType.toLowerCase().includes("application/json");
    const defaultMessage = res.status === 401 && isPublicAuthRequest(url)
      ? "Email hoặc mật khẩu không chính xác. Nếu là tài khoản quản trị, hãy kiểm tra tài khoản trong database SD_11 và chạy database/RESET_ADMIN_LOGIN_DEV_F5.sql khi cần khôi phục đăng nhập local."
      : res.status === 403
        ? "Tài khoản hiện tại không có quyền thực hiện thao tác này. Hãy đăng nhập bằng tài khoản Admin/Manager/Employee phù hợp."
        : `Lỗi ${res.status}: API không xử lý được yêu cầu`;
    const message = serverMessage || (res.status === 502 || res.status === 504 || proxyFailure || unavailableHtml
      ? "Không kết nối được Spring Boot API tại localhost:8080. Hãy chạy npm run dev:api rồi mở http://localhost:8080/api/health để kiểm tra cấu hình SQL Server."
      : defaultMessage);
    const err = new Error(message);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

export const api = {
  get: (path, options) => request("GET", path, undefined, options),
  post: (path, body, options) => request("POST", path, body ?? {}, options),
  put: (path, body, options) => request("PUT", path, body ?? {}, options),
  patch: (path, body, options) => request("PATCH", path, body ?? {}, options),
  delete: (path, options) => request("DELETE", path, undefined, options),
};

/* ---------- Cac API moi (nhanh hon, co phan trang) ---------- */
export const productsApi = {
  list: (params = {}) => {
    const q = new URLSearchParams(
      Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ""),
    ).toString();
    return api.get(`/v2/products${q ? `?${q}` : ""}`);
  },
  featured: (limit = 8) => api.get(`/v2/products/featured?limit=${limit}`),
};

export const ordersApi = {
  list: (params = {}) => {
    const q = new URLSearchParams(
      Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ""),
    ).toString();
    return api.get(`/v2/orders${q ? `?${q}` : ""}`);
  },
};

export const dashboardApi = {
  summary: () => api.get("/v2/dashboard/summary"),
};

export default api;
