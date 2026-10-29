import { api } from './apiClient'
import { shippingMethods as fallbackShippingMethods } from '../data/mockData'

export const STORE_ORIGIN_DEFAULT = {
  address: '249 Xã Đàn, Đống Đa, Hà Nội',
  latitude: 21.0150,
  longitude: 105.8290,
}
const STORE_ORIGIN_TEXT = `${STORE_ORIGIN_DEFAULT.address}, Việt Nam`
const FALLBACK_METHODS = [...fallbackShippingMethods]
const geocodeCache = new Map()
const geocodeInFlight = new Map()
let originCache = null
let lastNominatimRequestAt = 0

const toNumber = (value, fallback = 0) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

export const calculateShippingFee = (distanceKm) => {
  const km = Math.max(0, toNumber(distanceKm, 0))
  if (km <= 0.5) return 0
  if (km <= 2) return 15700
  if (km <= 3) return 19700
  return 19700 + Math.ceil(km - 3) * 5400
}

export const normalizeShippingMethod = (raw = {}) => {
  const code = String(raw.code ?? raw.MethodCode ?? raw.method_code ?? 'STANDARD').trim().toUpperCase()
  return {
    id: raw.id ?? raw.ShippingMethodID ?? raw.shipping_method_id ?? null,
    code,
    name: raw.name ?? raw.MethodName ?? raw.method_name ?? 'Giao hàng tiêu chuẩn',
    basePrice: Math.max(0, toNumber(raw.basePrice ?? raw.BasePrice, 15700)),
    pricePerKm: Math.max(0, toNumber(raw.pricePerKm ?? raw.PricePerKm, 5400)),
    eta: raw.eta ?? raw.EstimatedTimeText ?? raw.estimated_time_text ?? '2 - 3 ngày',
    originCity: raw.originCity ?? raw.OriginCity ?? STORE_ORIGIN_TEXT,
    desc: raw.desc ?? `Tính phí theo tọa độ GPS từ ${STORE_ORIGIN_DEFAULT.address}. Miễn phí trong 500 m.`,
  }
}

const fallback = () => {
  const byCode = new Map()
  FALLBACK_METHODS.map(normalizeShippingMethod).forEach((method) => byCode.set(method.code, method))
  const methods = [...byCode.values()]
  if (!methods.length) return [{ code: 'STANDARD', name: 'Giao hàng tiêu chuẩn', basePrice: 15700, pricePerKm: 5400, eta: '2-5 ngày', originCity: STORE_ORIGIN_TEXT }]
  return methods.map((m) => ({ ...m, basePrice: 15700, pricePerKm: 5400, originCity: STORE_ORIGIN_TEXT }))
}

async function fetchJson(url, timeoutMs = 10000) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(url, { signal: controller.signal, headers: { Accept: 'application/json', 'Accept-Language': 'vi,en;q=0.8' } })
    if (!response.ok) throw new Error(`Dịch vụ định vị trả về HTTP ${response.status}`)
    return await response.json()
  } finally {
    clearTimeout(timer)
  }
}

async function fetchNominatim(url) {
  // Public Nominatim asks applications to stay at or below one request/second.
  const waitMs = Math.max(0, 1000 - (Date.now() - lastNominatimRequestAt))
  if (waitMs > 0) await new Promise((resolve) => setTimeout(resolve, waitMs))
  lastNominatimRequestAt = Date.now()
  return fetchJson(url)
}

const cleanAddressPart = (value) => String(value || '')
  .replace(/\s+/g, ' ')
  .replace(/^(?:,\s*)+|(?:\s*,)+$/g, '')
  .trim()

const normalizeAddressKey = (value) => cleanAddressPart(value)
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
  .toLocaleLowerCase('vi-VN')
  .replace(/[^a-z0-9]+/g, ' ')
  .trim()

function stripCountrySuffix(value) {
  return cleanAddressPart(value)
    .replace(/(?:,\s*)?(?:việt\s*nam|viet\s*nam|vietnam|vn)\s*$/i, '')
    .replace(/\s*,\s*/g, ', ')
    .trim()
}

function uniqueParts(parts) {
  const result = []
  const seen = new Set()
  for (const raw of parts) {
    const part = stripCountrySuffix(raw)
    const key = normalizeAddressKey(part)
    if (!key || seen.has(key)) continue
    // Do not append an administrative part when its words already appear in a
    // longer part. This avoids duplicate ward/city names in legacy FullAddress.
    if (result.some((existing) => {
      const existingKey = normalizeAddressKey(existing)
      return existingKey.includes(key) || key.includes(existingKey)
    })) continue
    result.push(part)
    seen.add(key)
  }
  return result
}

/** Candidate list ordered from the most specific user-entered address to safe
 * administrative-area fallbacks. The direct street line is intentionally first
 * so stale/duplicated area fields cannot corrupt a valid street address. */
export function buildGeocodeQueries(addressParts = {}) {
  if (typeof addressParts === 'string') {
    const direct = stripCountrySuffix(addressParts)
    return direct ? [direct] : []
  }
  const line = cleanAddressPart(addressParts.address || addressParts.line || addressParts.addressLine)
  const full = stripCountrySuffix(addressParts.fullAddress)
  const ward = cleanAddressPart(addressParts.ward)
  const district = cleanAddressPart(addressParts.district)
  const province = cleanAddressPart(addressParts.province)

  // If the street field already includes several comma-separated address parts
  // (e.g. house, neighbourhood, ward and city), do not append the currently
  // selected ward/province. Legacy records can contain different admin names
  // in `FullAddress`; combining them creates contradictory queries and poor hits.
  const lineIsDetailed = line.split(',').filter(Boolean).length >= 3
    || /(phường|phuong|xã|xa|quận|quan|huyện|huyen|khu phố|khu pho|thành phố|thanh pho|thị trấn|thi tran)/i.test(line)
  const candidates = [line]
  if (!lineIsDetailed) {
    candidates.push(
      full,
      uniqueParts([line, ward, district, province]).join(', '),
      uniqueParts([line, ward, province]).join(', '),
      uniqueParts([line, province]).join(', '),
      uniqueParts([ward, district, province]).join(', '),
      uniqueParts([ward, province]).join(', '),
    )
  }

  const out = []
  const seen = new Set()
  for (const item of candidates) {
    const candidate = stripCountrySuffix(item)
    const key = normalizeAddressKey(candidate)
    if (!key || seen.has(key)) continue
    seen.add(key)
    out.push(candidate)
  }
  return out.slice(0, 6)
}

function withVietnamCountry(query) {
  const cleaned = stripCountrySuffix(query)
  return cleaned ? `${cleaned}, Việt Nam` : 'Việt Nam'
}

function validGeoPoint(latitude, longitude) {
  return Number.isFinite(latitude) && Number.isFinite(longitude)
    && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180
    && (latitude !== 0 || longitude !== 0)
}

function isVietnamResult(countryCode) {
  return !countryCode || String(countryCode).toLowerCase() === 'vn'
}

async function geocodeAddress(addressParts) {
  const queries = buildGeocodeQueries(addressParts)
  const address = queries[0] || ''
  const key = normalizeAddressKey(queries.join(' | '))
  if (!address) throw new Error('Vui lòng nhập địa chỉ nhận hàng để xác định tọa độ GPS.')
  if (geocodeCache.has(key)) return geocodeCache.get(key)
  if (geocodeInFlight.has(key)) return geocodeInFlight.get(key)

  const pending = (async () => {
    // Try Photon first. Do not set lang=vi in the query string: the public Photon
    // instance may reject language codes it does not support with HTTP 400.
    // Let Accept-Language guide display language instead.
    for (const query of queries) {
      try {
        const params = new URLSearchParams({ q: withVietnamCountry(query), limit: '1' })
        const rows = await fetchJson(`https://photon.komoot.io/api/?${params.toString()}`)
        const feature = rows?.features?.[0]
        const coords = feature?.geometry?.coordinates
        const longitude = Number(coords?.[0])
        const latitude = Number(coords?.[1])
        if (validGeoPoint(latitude, longitude) && isVietnamResult(feature?.properties?.countrycode)) {
          const result = {
            latitude,
            longitude,
            resolvedAddress: feature?.properties?.name || feature?.properties?.street || query,
            provider: 'Photon',
          }
          geocodeCache.set(key, result)
          return result
        }
      } catch (_) {
        // Try the next candidate/provider. DNS and network failures are expected
        // to fall through rather than stopping the checkout immediately.
      }
    }

    // Nominatim is a secondary provider. Only send the best street-level query
    // and throttle it to one request per second as required by its public policy.
    try {
      const query = queries[0]
      const params = new URLSearchParams({
        format: 'jsonv2', limit: '1', countrycodes: 'vn', 'accept-language': 'vi',
        q: withVietnamCountry(query),
      })
      const rows = await fetchNominatim(`https://nominatim.openstreetmap.org/search?${params.toString()}`)
      const row = Array.isArray(rows) ? rows[0] : null
      const latitude = Number(row?.lat)
      const longitude = Number(row?.lon)
      if (validGeoPoint(latitude, longitude) && isVietnamResult(row?.address?.country_code)) {
        const result = {
          latitude,
          longitude,
          resolvedAddress: row.display_name || query,
          provider: 'Nominatim',
        }
        geocodeCache.set(key, result)
        return result
      }
    } catch (_) {
      // Continue to the actionable final error below.
    }

    throw new Error('Không thể tra cứu GPS từ dịch vụ bản đồ (có thể do mạng/DNS). Hãy kiểm tra kết nối Internet, kiểm tra địa chỉ có bị ghép sai phường/xã hoặc tỉnh/thành rồi thử lại.')
  })()
  geocodeInFlight.set(key, pending)
  try { return await pending } finally { geocodeInFlight.delete(key) }
}

export function haversineKm(a, b) {
  const rad = (n) => n * Math.PI / 180
  const dLat = rad(Number(b.latitude) - Number(a.latitude))
  const dLon = rad(Number(b.longitude) - Number(a.longitude))
  const lat1 = rad(Number(a.latitude))
  const lat2 = rad(Number(b.latitude))
  const x = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2
  return 6371 * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(Math.max(0, 1 - x)))
}

async function getStoreOrigin() {
  if (originCache) return originCache
  try {
    const remote = await api.get('/shipping/origin')
    const latitude = Number(remote?.latitude ?? remote?.STORE_ORIGIN_LATITUDE)
    const longitude = Number(remote?.longitude ?? remote?.STORE_ORIGIN_LONGITUDE)
    if (Number.isFinite(latitude) && Number.isFinite(longitude) && latitude !== 0 && longitude !== 0) {
      originCache = {
        address: remote.address || remote.originAddress || STORE_ORIGIN_DEFAULT.address,
        latitude,
        longitude,
      }
      return originCache
    }
  } catch (_) { /* the specified fixed coordinates remain a safe fallback for local demos */ }
  originCache = { ...STORE_ORIGIN_DEFAULT }
  return originCache
}

export const shippingApi = {
  async methods() {
    try {
      const rows = await api.get('/shippingmethods')
      const methods = Array.isArray(rows) ? rows.map(normalizeShippingMethod).filter((method) => method.code) : []
      return methods.length ? methods.map((m) => ({ ...m, originCity: STORE_ORIGIN_TEXT })) : fallback()
    } catch (_) {
      return fallback()
    }
  },

  async quote({ methodCode = 'STANDARD', province = '', district = '', ward = '', address = '' } = {}) {
    const destination = await geocodeAddress({ province, district, ward, address })
    const origin = await getStoreOrigin()
    const distanceKm = haversineKm(origin, destination)
    const localFee = calculateShippingFee(distanceKm)
    let result = null
    try {
      // The API recomputes distance and fee from saved StoreSettings coordinates;
      // the browser-supplied distance is not trusted for the final order amount.
      result = await api.post('/shipping/quote', {
        methodCode,
        recipientLatitude: destination.latitude,
        recipientLongitude: destination.longitude,
      })
    } catch (_) {
      // Keep checkout usable when the quote endpoint temporarily fails; order creation
      // recalculates the same amount server-side from these GPS coordinates.
    }
    return {
      ...(result || {}),
      methodCode: String(result?.methodCode ?? methodCode).trim().toUpperCase(),
      fee: Math.max(0, toNumber(result?.fee ?? result?.shippingFee, localFee)),
      distanceKm: Math.max(0, toNumber(result?.distanceKm, distanceKm)),
      basePrice: Math.max(0, toNumber(result?.basePrice, 15700)),
      pricePerKm: Math.max(0, toNumber(result?.pricePerKm, 5400)),
      recipientLatitude: destination.latitude,
      recipientLongitude: destination.longitude,
      estimatedDistance: false,
      originAddress: result?.originAddress || origin.address || STORE_ORIGIN_TEXT,
      distanceMethod: 'Haversine (tọa độ GPS)',
    }
  },
}

export { fallback as fallbackShippingMethods }
