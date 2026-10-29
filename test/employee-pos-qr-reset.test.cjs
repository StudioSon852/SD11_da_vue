const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const root = path.resolve(__dirname, '..')
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8')

test('employee POS and orders reuse the exact Admin components and shared data store', () => {
  const employee = read('src/views/EmployeePortalView.vue')
  assert.match(employee, /import PosPage from '\.\/admin\/pages\/PosPage\.vue'/)
  assert.match(employee, /import PaymentsPage from '\.\/admin\/pages\/PaymentsPage\.vue'/)
  assert.match(employee, /fetchAllData\(true\); loadEmployeeData\(false\) \}, 5000/)
  assert.match(employee, /freedoms:show-payments/)
})

test('POS transfer opens shared VietQR and a 30-minute countdown', () => {
  const store = read('src/views/admin/adminStore.js')
  const pos = read('src/views/admin/pages/PosPage.vue')
  assert.match(store, /buildTransferQr\(\{ reference: paymentReference, amount: savedTotal, orderId: newId \}\)/)
  assert.match(store, /secondsRemaining: 1800/)
  assert.match(store, /setInterval\(\(\) => \{ void refreshPosPaymentStatus\(\); \}, 5000\)/)
  assert.match(store, /refreshTransferQrUrl\(posPayModal\.qr\)/)
  assert.match(pos, /formatPosCountdown\(posPayModal\.secondsRemaining\)/)
  assert.match(pos, /posPayModal\.paymentStatus/)
})

test('single database script creates SD_11 and explicitly scopes the order/payment reset', () => {
  const sql = read('database/SD_11_COMPLETE.sql')
  assert.match(sql, /DB_NAME\(\) <> N'SD_11'/)
  assert.match(sql, /PHẦN 8 — RESET LỊCH SỬ ĐƠN HÀNG VÀ THANH TOÁN/)
  assert.match(sql, /SỐ LƯỢNG TRƯỚC KHI RESET/)
  assert.match(sql, /AS \[RecordCount\]/)
  assert.match(sql, /OrderPaymentHistory/)
  assert.match(sql, /PaymentTransactions/)
  assert.match(sql, /DELETE FROM dbo\.Orders/)
  assert.doesNotMatch(sql, /AS RowCount/)
})

test('employee/admin management controls have rounded borders', () => {
  const employee = read('src/views/EmployeePortalView.vue')
  assert.match(employee, /border-radius: 10px/)
  assert.match(employee, /box-shadow: 0 0 0 3px/)
})

test('Admin and Employee clear cached order history when the API authoritatively returns an empty list', () => {
  const store = read('src/views/admin/adminStore.js')
  assert.match(store, /if \(freshOrders\.length === 0\)/)
  assert.match(store, /db\.orders = \[\]/)
  assert.match(store, /Không smart-merge mảng rỗng với cache cũ/)
})

test('customer order cache clears after an authoritative empty server order list', () => {
  const store = read('src/stores/orderStore.js')
  assert.match(store, /if \(list\.length === 0 && orderState\.orders\.length > 0\)/)
  assert.match(store, /orderState\.orders = \[\]/)
  assert.match(store, /saveOrders\(\)/)
})

test('destructive reset is restricted to SD_11, transactional, and preserves non-order history', () => {
  const sql = fs.readFileSync(path.join(root, 'database/SD_11_COMPLETE.sql'), 'utf8')
  assert.match(sql, /THỰC THI RESET LỊCH SỬ ĐƠN HÀNG VÀ THANH TOÁN/)
  assert.match(sql, /DB_NAME\(\) <> N'SD_11'/)
  assert.match(sql, /BEGIN TRANSACTION/)
  assert.match(sql, /COMMIT TRANSACTION/)
  assert.match(sql, /ROLLBACK TRANSACTION/)
  assert.match(sql, /DELETE FROM dbo\.Orders/)
  assert.match(sql, /UPDATE pt SET OrderID=NULL/)
  assert.match(sql, /UPDATE rv SET OrderDetailID=NULL/)
  assert.doesNotMatch(sql, /DELETE FROM dbo\.PointTransactions/i)
  assert.doesNotMatch(sql, /DELETE FROM dbo\.Reviews/i)
  assert.doesNotMatch(sql, /UPDATE dbo\.Coupons SET UsedCount=0/i)
  assert.doesNotMatch(sql, /RowCount/)
})
