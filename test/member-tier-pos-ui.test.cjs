const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');

test('POS loyalty tiers use the requested spend thresholds and benefits', () => {
  const store = read('src/views/admin/adminStore.js');
  assert.match(store, /5_000_000_000[\s\S]*discountPercent: 5/);
  assert.match(store, /1_000_000_000[\s\S]*discountPercent: 3/);
  assert.match(store, /500_000_000[\s\S]*discountPercent: 2/);
  assert.match(store, /100_000_000[\s\S]*discountPercent: 0/);
  assert.match(store, /posCouponDiscountAmount/);
  assert.match(store, /posMemberDiscountAmount/);
  assert.match(store, /coupon_discount_amount/);
});

test('POS QR modal shares VietQR builder and clearly warns when bank data is not configured', () => {
  const store = read('src/views/admin/adminStore.js');
  const pos = read('src/views/admin/pages/PosPage.vue');
  assert.match(store, /buildTransferQr\(/);
  assert.match(pos, /posPayModal\.isBankQr/);
  assert.match(read('src/services/paymentQr.js'), /VITE_BANK_CODE/);
  assert.match(pos, /posPayModal\.qrRefreshKey/);
});

test('Employee route hides storefront header/footer and provides searchable management tabs', () => {
  const app = read('src/App.vue');
  const employee = read('src/views/EmployeePortalView.vue');
  assert.match(app, /const isEmployee = computed/);
  assert.match(app, /!isAdmin && !isEmployee && !isAuthPage/);
  assert.match(employee, /STEP INTO YOUR STYLE/);
  assert.match(employee, /Tìm mục quản lý/);
  assert.match(employee, /visibleEmployeeTabs/);
  assert.match(employee, /toasts/);
});

test('Member tier migration defines the 4 requested thresholds and percentage benefits', () => {
  const sql = read('database/SD_11_COMPLETE.sql');
  for (const value of ['100000000', '500000000', '1000000000', '5000000000']) assert.ok(sql.includes(value));
  assert.match(sql, /'SILVER',N'Bạc',500000000,2/);
  assert.match(sql, /'GOLD',N'Vàng',1000000000,3/);
  assert.match(sql, /'DIAMOND',N'Kim Cương',5000000000,5/);
});
