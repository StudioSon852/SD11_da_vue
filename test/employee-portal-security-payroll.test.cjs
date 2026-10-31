const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');

test('Admin can add staff using roles from the database rather than a hard-coded role list', () => {
  const accountsPage = read('src/views/admin/pages/AccountsPage.vue');
  const adminStore = read('src/views/admin/adminStore.js');
  const api = read('backend/src/main/java/vn/shoegroup/sd11/admin/AdminDataController.java');
  assert.match(accountsPage, /openForm\('accounts', null, \{ role_id: employeeRoleId \}\)/);
  assert.match(adminStore, /db\.roles\.find\(\(r\) => \/customer\|khach hang\/\.test\(normalizeRoleLabel\(r\.name\)\)/);
  assert.match(api, /@GetMapping\("\/roles"\)/);
  assert.match(api, /selectedRoleName.*contains\("admin"\)/s);
  assert.match(api, /LIKE N'%nhân viên%'/);
  assert.match(api, /LIKE N'%quản lý%'/);
});

test('locked users are blocked by live database status and the last protected Admin is protected', () => {
  const filter = read('backend/src/main/java/vn/shoegroup/sd11/auth/JwtAuthFilter.java');
  const accountApi = read('backend/src/main/java/vn/shoegroup/sd11/account/AccountController.java');
  const page = read('src/views/admin/pages/AccountsPage.vue');
  assert.match(filter, /SELECT u\.UserID, u\.Email, u\.FullName, ISNULL\(u\.IsActive,1\) IsActive/);
  assert.match(filter, /Tài khoản đã bị khóa/);
  assert.match(accountApi, /Không thể khóa hoặc hạ quyền Admin cuối cùng đang hoạt động/);
  assert.match(page, /protectedAdminId/);
});

test('employee portal retains core workspaces and removes shift/payroll tabs and endpoints', () => {
  const portal = read('src/views/EmployeePortalView.vue');
  const routes = read('src/router/index.js');
  const adminRoutes = read('src/views/admin/adminRoutes.js');
  const adminLayout = read('src/views/admin/AdminLayout.vue');
  const controller = read('backend/src/main/java/vn/shoegroup/sd11/admin/StaffManagementController.java');
  assert.match(routes, /path: "\/employee"/);
  for (const label of ['Tổng quan', 'Bán hàng tại quầy', 'Đơn hàng & thanh toán', 'Khách hàng', 'Hồ sơ'])
    assert.ok(portal.includes(label), `missing employee tab: ${label}`);
  for (const removed of ['Ca làm', 'Mức lương của tôi', 'EmployeeAttendance', 'EmployeeShiftAssignments', 'EmployeeSalarySettings']) {
    assert.ok(!portal.includes(removed), `employee portal still contains ${removed}`);
    assert.ok(!adminLayout.includes(removed), `admin navigation still contains ${removed}`);
  }
  assert.doesNotMatch(adminRoutes, /path:\s*['"]staff['"]/);
  assert.match(controller, /@GetMapping\("\/employee\/profile"\)/);
  assert.match(controller, /@PutMapping\("\/employee\/profile"\)/);
  assert.doesNotMatch(controller, /@GetMapping\("\/employee\/(?:shifts|payroll|attendance)/);
});

test('staff shift and salary tables are dropped without deleting staff accounts', () => {
  const migration = read('database/SD_11_DATABASE_SETUP.sql');
  const controller = read('backend/src/main/java/vn/shoegroup/sd11/admin/StaffManagementController.java');
  for (const table of ['EmployeeSalarySettings', 'EmployeeShiftAssignments', 'EmployeeAttendance']) {
    assert.match(migration, new RegExp('DROP TABLE dbo\\.' + table));
    assert.doesNotMatch(migration, new RegExp('CREATE TABLE dbo\\.' + table));
  }
  assert.match(migration, /Không đụng đến Users\/Roles\/tài khoản nhân viên/);
  assert.doesNotMatch(controller, /weeklySalary|attendanceWindowStatus|LocalTime\.of/);
});

test('Overview hides breakdown by payment method in both admin and employee portal', () => {
  const dashboard = read('src/views/admin/pages/DashboardPage.vue');
  const portal = read('src/views/EmployeePortalView.vue');
  assert.doesNotMatch(dashboard, /Doanh thu theo thanh toán/);
  assert.match(portal, /DashboardPage/);
  assert.match(portal, /Không hiển thị phân bổ doanh thu theo phương thức thanh toán/);
});
