/*
 * HTTP interceptor tương thích các lệnh fetch cũ và thêm JWT vào API dự án.
 * Mặc định API_BASE_URL là /api, đi qua Vite proxy tới Spring Boot :8080.
 * Nếu cấu hình VITE_API_BASE_URL là URL tuyệt đối, request sẽ đi trực tiếp tới API đó.
 */
import { API_BASE_URL, getToken, clearToken } from "./apiClient";

const LEGACY_BASES = [
  "http://localhost:5000/api",
  "http://127.0.0.1:5000/api",
];

let installed = false;
const configuredBase = String(API_BASE_URL || "/api").replace(/\/+$/, "");
const configuredBaseIsAbsolute = /^https?:\/\//i.test(configuredBase);

function isAtOrBelow(value, base) {
  return value === base || value.startsWith(`${base}/`) || value.startsWith(`${base}?`);
}

function rewriteUrl(value, origin) {
  let url = value;

  // Redirect legacy API URLs to the current configured API base.
  for (const legacy of LEGACY_BASES) {
    if (isAtOrBelow(url, legacy)) {
      url = `${configuredBase}${url.slice(legacy.length)}`;
      break;
    }
  }

  // When an absolute API base is configured, turn legacy /api paths into that base.
  if (
    configuredBaseIsAbsolute &&
    (url === "/api" || url.startsWith("/api/") || url.startsWith("/api?"))
  ) {
    url = `${configuredBase}${url.slice(4)}`;
  }

  // URL objects / Request objects can expose an absolute same-origin URL even when
  // the actual API base is the relative Vite proxy path /api.
  const proxyBase = new URL(configuredBase, origin).href.replace(/\/+$/, "");
  const isConfiguredApi = isAtOrBelow(url, configuredBase) || isAtOrBelow(url, proxyBase);
  const isLegacyApi = LEGACY_BASES.some((legacy) => isAtOrBelow(url, legacy));
  return { url, isProjectApi: isConfiguredApi || isLegacyApi };
}

function isPublicAuthEndpoint(value, origin) {
  try {
    const pathname = new URL(value, origin).pathname;
    return /\/(?:auth\/)?(?:login|register|forgot-password|reset-password)\/?$/i.test(pathname);
  } catch {
    return false;
  }
}

export function installHttpInterceptor({ onUnauthorized } = {}) {
  if (installed || typeof window === "undefined" || typeof window.fetch !== "function") return;
  installed = true;

  const originalFetch = window.fetch.bind(window);

  window.fetch = async (input, init = {}) => {
    const isRequest = typeof Request !== "undefined" && input instanceof Request;
    const originalUrl = typeof input === "string"
      ? input
      : isRequest
        ? input.url
        : String(input);

    const rewritten = rewriteUrl(originalUrl, window.location.origin);
    if (!rewritten.isProjectApi) return originalFetch(input, init);

    // Fetch accepts relative paths, but new Request() requires an absolute URL.
    const targetUrl = /^https?:\/\//i.test(rewritten.url)
      ? rewritten.url
      : new URL(rewritten.url, window.location.origin).href;

    // Preserve headers from both Request and RequestInit; explicit init headers win.
    const headers = new Headers(isRequest ? input.headers : undefined);
    if (init.headers) {
      new Headers(init.headers).forEach((value, key) => headers.set(key, value));
    }

    const publicAuthRequest = isPublicAuthEndpoint(targetUrl, window.location.origin);
    if (publicAuthRequest) {
      headers.delete("Authorization");
    } else {
      const token = getToken();
      if (token && !headers.has("Authorization")) {
        headers.set("Authorization", `Bearer ${token}`);
      }
    }

    // Clone the Request source so its body remains readable, and always pass an
    // absolute URL to the Request constructor (fixes invalid relative URL errors).
    const target = isRequest ? new Request(targetUrl, input) : targetUrl;
    const response = await originalFetch(target, { ...init, headers });

    // A bad username/password is an expected public-auth 401, not an expired session.
    // Do not clear the current user or redirect away from the login/reset screens.
    if (response.status === 401 && !isPublicAuthEndpoint(targetUrl, window.location.origin)) {
      clearToken();
      if (typeof onUnauthorized === "function") onUnauthorized();
    }

    // Never retry mutations automatically: the server may already have committed them.
    return response;
  };
}

export default installHttpInterceptor;
