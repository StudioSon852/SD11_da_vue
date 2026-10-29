import { computed, reactive } from "vue";
import { api, setToken, clearToken, setUnauthorizedHandler } from "../services/apiClient";

// Tách phiên đăng nhập theo TAB. sessionStorage được cô lập giữa các tab cùng origin,
// vì vậy đăng nhập Nhân viên ở một tab không ghi đè JWT của Admin ở tab khác.
const STORAGE_KEY = "shoegroup_current_user";
const LEGACY_USER_KEY = "user";
const LEGACY_TOKEN_KEY = "token";

function tabStorage() {
  try { return typeof window !== "undefined" ? window.sessionStorage : null; }
  catch { return null; }
}

function loadCurrentUser() {
  const storage = tabStorage();
  if (!storage) return null;
  try {
    const user = JSON.parse(storage.getItem(STORAGE_KEY) || storage.getItem(LEGACY_USER_KEY) || "null");
    const token = storage.getItem("shoegroup_token") || storage.getItem(LEGACY_TOKEN_KEY) || user?.token || null;
    if (!user) {
      // Avoid resurrecting a stale legacy token on this tab when no user exists.
      storage.removeItem(STORAGE_KEY);
      storage.removeItem(LEGACY_USER_KEY);
      storage.removeItem(LEGACY_TOKEN_KEY);
      clearToken();
      return null;
    }
    if (token && !user.token) user.token = token;
    if (user.token) setToken(user.token);
    return user;
  } catch {
    storage.removeItem(STORAGE_KEY);
    storage.removeItem(LEGACY_USER_KEY);
    storage.removeItem(LEGACY_TOKEN_KEY);
    clearToken();
    return null;
  }
}

function saveCurrentUser(user) {
  const storage = tabStorage();
  if (!storage) return;
  if (!user) {
    [STORAGE_KEY, LEGACY_USER_KEY, LEGACY_TOKEN_KEY].forEach((key) => storage.removeItem(key));
    clearToken();
    return;
  }
  if (user.token) setToken(user.token);
  storage.setItem(STORAGE_KEY, JSON.stringify(user));
  // Compatibility keys remain tab-scoped; never write auth credentials to localStorage.
  storage.setItem(LEGACY_USER_KEY, JSON.stringify(user));
  if (user.token) storage.setItem(LEGACY_TOKEN_KEY, user.token);
}

export const authState = reactive({ currentUser: loadCurrentUser() });
export const currentUser = computed(() => authState.currentUser);
export const isAuthenticated = computed(() => !!authState.currentUser);
export const getCurrentUser = () => authState.currentUser;

export const setSessionUser = (user) => {
  authState.currentUser = user ? { ...user } : null;
  saveCurrentUser(authState.currentUser);
  return authState.currentUser;
};

export const login = async ({ email, password }) => {
  try {
    const data = await api.post("/auth/login", { email, password });
    if (data?.success && data?.user && data?.token) {
      const user = { ...data.user, token: data.token };
      authState.currentUser = user;
      saveCurrentUser(user);
      return { ok: true, message: "Đăng nhập thành công.", user };
    }
    return { ok: false, message: data?.message || "Đăng nhập thất bại." };
  } catch (error) {
    return { ok: false, message: error?.message || "Không thể kết nối đến máy chủ." };
  }
};

export const register = async ({ fullName, email, password }) => {
  try {
    const data = await api.post("/auth/register", { full_name: fullName, email, password });
    // Endpoint đăng ký của API tạo tài khoản, nhưng giao diện vẫn yêu cầu đăng nhập
    // lại nếu phản hồi không phát hành token/user hợp lệ.
    if (data?.success && data?.user && data?.token) {
      const user = { ...data.user, token: data.token };
      authState.currentUser = user;
      saveCurrentUser(user);
      return { ok: true, message: "Đăng ký thành công.", user };
    }
    return { ok: !!data?.success, message: data?.message || "Đăng ký thành công. Vui lòng đăng nhập.", user: data?.user || null };
  } catch (error) {
    return { ok: false, message: error?.message || "Không thể kết nối đến máy chủ." };
  }
};

export const logout = () => {
  authState.currentUser = null;
  saveCurrentUser(null);
};

// When the backend reports an expired or blocked account, clear this tab's identity.
setUnauthorizedHandler(() => {
  authState.currentUser = null;
  saveCurrentUser(null);
  if (typeof window !== "undefined" && window.location.pathname !== "/login") {
    window.location.assign("/login?reason=account-disabled");
  }
});

export const updateProfile = async (data) => {
  try {
    const userId = data?.id ?? data?.id_user ?? data?.UserID;
    if (!userId) return { ok: false, message: "Không xác định được tài khoản." };
    const response = await api.put(`/accounts/${userId}`, {
      name: data.full_name,
      phone: data.phone,
      ...(data.avatar_url !== undefined ? { avatar_url: data.avatar_url } : {}),
      ...(data.address !== undefined ? { address: data.address } : {}),
    });
    authState.currentUser = {
      ...authState.currentUser,
      ...data,
      ...(response?.user || {}),
      id: userId,
    };
    saveCurrentUser(authState.currentUser);
    return { ok: true, user: authState.currentUser };
  } catch (error) {
    return { ok: false, message: error?.message || "Lỗi kết nối máy chủ." };
  }
};

export const requestPasswordReset = async (email) => {
  try {
    const data = await api.post("/auth/forgot-password", { email });
    return { ok: !!data?.success, message: data?.message, resetToken: data?.resetToken || null };
  } catch (error) {
    return { ok: false, message: error?.message || "Không thể kết nối đến máy chủ." };
  }
};

export const resetPassword = async ({ token, newPassword }) => {
  try {
    const data = await api.post("/auth/reset-password", { token, newPassword });
    return {
      ok: !!data?.success,
      message: data?.message || (data?.success ? "Đặt lại mật khẩu thành công." : "Token không hợp lệ hoặc đã hết hạn."),
    };
  } catch (error) {
    return { ok: false, message: error?.message || "Không thể kết nối đến máy chủ." };
  }
};
