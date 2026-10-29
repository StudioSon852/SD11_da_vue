const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const controller = fs.readFileSync(path.join(root, 'backend/src/main/java/vn/shoegroup/sd11/order/OrderController.java'), 'utf8');
const schema = fs.readFileSync(path.join(root, 'database/SD_11_COMPLETE.sql'), 'utf8');

test('PaymentTransactions uses SUCCESS enum supported by SQL Server constraint', () => {
  assert.ok(schema.includes('CK_PT_Status'), 'The baseline database schema should define CK_PT_Status');
  assert.ok(schema.includes("N''''SUCCESS''''"), 'CK_PT_Status allowed values should include SUCCESS');
  assert.match(controller, /Status=N'SUCCESS'/, 'Payment confirmation must persist SUCCESS');
  assert.doesNotMatch(controller, /Status\s*(?:=|<>)\s*N'SUCCEEDED'/i,
    'SUCCEEDED is not an allowed persisted database status');
});
