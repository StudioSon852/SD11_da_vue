/* QR thanh toán: mã tham chiếu được tạo duy nhất cho từng giao dịch. */
export function createPaymentReference(prefix = 'FDS') {
  const random = globalThis.crypto?.randomUUID?.().replaceAll('-', '').slice(0, 12)
    || Math.random().toString(36).slice(2, 14).toUpperCase()
  return `${prefix}-${Date.now().toString(36).toUpperCase()}-${random}`.slice(0, 80)
}

export function buildTransferQr({ reference = '', amount = 0, orderId = '' } = {}) {
  const memo = String(reference || `FDS-${orderId || Date.now()}`).replace(/[^\p{L}\p{N}-]/gu, '').slice(0, 35)
  const bankCode = String(import.meta.env.VITE_BANK_CODE || '').trim()
  const account = String(import.meta.env.VITE_BANK_ACCOUNT || '').trim()
  const accountName = String(import.meta.env.VITE_BANK_ACCOUNT_NAME || '').trim()
  const value = Math.max(0, Math.round(Number(amount) || 0))
  if (bankCode && account) {
    const url = new URL(`https://img.vietqr.io/image/${encodeURIComponent(bankCode)}-${encodeURIComponent(account)}-compact2.png`)
    url.searchParams.set('amount', String(value))
    url.searchParams.set('addInfo', memo)
    if (accountName) url.searchParams.set('accountName', accountName)
    return { url: url.toString(), isBankQr: true, reference: memo }
  }
  const payload = `FREEDOMS|THAM_CHIEU=${memo}|DON=${orderId || 'POS'}|VND=${value}`
  return {
    url: `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${encodeURIComponent(payload)}`,
    isBankQr: false,
    reference: memo,
  }
}


/** Force a fresh image request every 30 minutes without changing the payment reference. */
export function refreshTransferQrUrl(value) {
  const source = String(value || '').trim()
  if (!source) return source
  try {
    const url = new URL(source)
    url.searchParams.set('_refresh', `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`)
    return url.toString()
  } catch {
    return source
  }
}
