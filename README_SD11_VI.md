# SD_11 — Website bán giày

## Công nghệ
- Frontend: Vue 3 + Vite + TypeScript-compatible Vue SFC.
- Backend: Spring Boot 3.5.6 REST API, JDK 17, Maven 3.9.16.
- Database: SQL Server 2017+; quản lý bằng SSMS.
- Local URLs: website `http://localhost:5173`, API health `http://localhost:8080/api/health`.

## 1. Database — làm đúng trên cùng một SQL Server instance

1. Mở SSMS và kết nối đến instance bạn sử dụng (ví dụ `localhost`, `(localdb)\MSSQLLocalDB`, hoặc `localhost\SQLEXPRESS`).
2. Để cài mới sạch hoàn toàn, mở `database/SD_11_FULL_20261025.sql` và nhấn **F5**. Script này xóa database `SD_11` hiện có trước khi tạo lại; hãy sao lưu nếu có dữ liệu cần giữ.
3. Nếu database `SD_11` đã tồn tại, chỉ mở `database/SD_11_SCHEMA_FIX_F5.sql` và nhấn **F5**. Script vá không xóa database.
4. Mở `database/DIAGNOSTIC_SQL_SERVER_F5.sql` để kiểm tra server, database và bảng bắt buộc. Đảm bảo SSMS đang kết nối cùng instance với backend.

## 2. Cấu hình backend/.env

Sao chép `backend/.env.example` thành `backend/.env`. Nhập đúng thông tin từ kết nối SSMS — mật khẩu không thể tự đoán và không nên đưa vào ZIP.

### SQL Server mặc định dùng TCP port

```properties
PORT=8080
DB_SERVER=localhost
DB_PORT=1433
DB_NAME=SD_11
DB_USER=sa
DB_PASSWORD=MAT_KHAU_SQL_SERVER_CUA_BAN
DB_ENCRYPT=false
DB_TRUST_CERT=true
JWT_SECRET=thay_bang_chuoi_ngau_nhien_dai_toi_thieu_32_ky_tu
```

Tài khoản `sa` chỉ dùng được nếu SQL Server cho phép SQL Authentication (Mixed Mode), tài khoản đang bật, và mật khẩu chính xác. Nếu bạn dùng tài khoản SQL khác, thay `DB_USER` và `DB_PASSWORD`.

### SQL Server Express / named instance

Nếu trong SSMS bạn kết nối bằng `TENMAY\SQLEXPRESS`, không tự điền `localhost:1433` trừ khi bạn đã cấu hình instance đó nghe đúng cổng 1433. Thay vào đó, trong `backend/.env` dùng JDBC URL dạng này (đổi `localhost` hoặc `SQLEXPRESS` theo máy của bạn):

```properties
DB_JDBC_URL=jdbc:sqlserver://localhost;instanceName=SQLEXPRESS;databaseName=SD_11;encrypt=false;trustServerCertificate=true;loginTimeout=8
DB_USER=sa
DB_PASSWORD=MAT_KHAU_SQL_SERVER_CUA_BAN
```

Khi dùng `DB_JDBC_URL`, nó ưu tiên hơn `DB_SERVER`/`DB_PORT`. Named instance có thể cần dịch vụ SQL Server Browser hoạt động để phân giải cổng; lựa chọn ổn định hơn cho môi trường dev là bật TCP/IP và đặt port tĩnh.

## 3. Cài đặt Java, Maven và Node

Cài JDK 17, Maven 3.9.16 và Node.js LTS. ZIP SD_11_final đặt `package.json` ngay ở thư mục gốc của gói. Sau khi giải nén, mở PowerShell tại đúng thư mục đang chứa `package.json` (kiểm tra nhanh bằng `dir package.json`); không chạy lệnh ở thư mục cha chỉ chứa một thư mục con.

```powershell
java -version
mvn -version
node -v
npm -v
npm install
```

Nếu `mvn` không được nhận diện, Maven chưa được thêm vào `PATH`; lỗi này xảy ra trước khi backend chạy và không phải lỗi SQL.

## 4. Chạy dự án

Mở hai PowerShell để xem lỗi rõ ràng theo từng tiến trình.

Cửa sổ 1 — frontend:

```powershell
npm run dev:web
```

Cửa sổ 2 — backend:

```powershell
npm run dev:api
```

Giữ hai cửa sổ mở. Frontend dùng đường dẫn `/api` qua Vite proxy tới Spring Boot ở cổng 8080, nên trang bán hàng mặc định không cần CORS trực tiếp giữa cổng 5173 và 8080. Có thể chạy cả hai bằng `npm run dev`; khi backend lỗi, frontend không tự bị tắt.

Sau khi frontend báo Vite ready, mở `http://localhost:5173`. Sau khi backend chạy, mở `http://localhost:8080/api/health`. Endpoint sức khỏe báo một trong các trạng thái:
- `UP`: SQL Server đã kết nối, các bảng lõi tồn tại.
- `DATABASE_DOWN`: sai host/port/instance, đăng nhập SQL hoặc database chưa mở được; nội dung `message` sẽ gợi ý nguyên nhân.
- `SCHEMA_INCOMPLETE`: kết nối được SQL Server nhưng đang thiếu bảng/view; chạy script vá schema trên đúng database.

Có thể chạy `powershell -ExecutionPolicy Bypass -File .\scripts\Check-SD11.ps1` từ thư mục dự án để kiểm tra đường dẫn, `.env`, các cổng và endpoint health. Terminal backend sẽ in chi tiết `[DB-ERROR]` mà không in mật khẩu.

## 5. Đăng nhập/đăng ký và nghiệp vụ

- Trang `/login` và `/signup` có nền gradient xanh nước biển, form responsive và dùng API `/api/auth/login`, `/api/auth/register`.
- Đăng ký chỉ tạo tài khoản Customer, mật khẩu được hash; đăng nhập trả JWT, role admin/manager/employee điều hướng theo quyền.
- Quản lý sản phẩm: Products + ProductVariants cho phép quản lý tên, giá, hình ảnh, size/màu/SKU; không dùng số lượng tồn kho để chặn mua.
- Giỏ hàng, đơn hàng, phương thức thanh toán, voucher, đổi trả, địa chỉ, ví và các API quản trị tiếp tục dùng SQL Server.
- Nhãn `Đã bán N` dùng view `dbo.vw_ProductSoldCount`, tính từ dòng chi tiết đơn có PaymentStatus xác nhận đã thanh toán; không dùng số tĩnh mẫu trong giao diện.
- Cột tồn kho cũ có thể còn trong dữ liệu ShoeGroup để tương thích, nhưng không được dùng trong logic bán hàng. Script vá bỏ `StockAlerts` và `sp_ProcessOrderAtomic` khỏi module đang chạy. Bộ test frontend cũng đã được cập nhật theo chính sách không kiểm tra tồn kho; không còn test nào yêu cầu số lượng mua phải bị giới hạn theo `StockQuantity`.

## 6. Bản gọn và tài nguyên

Bản ZIP gọn đã bỏ các video hero lớn để giảm dung lượng tải xuống; trang chủ dùng các slide hình ảnh còn lại. Các file code, backend, SQL và tài nguyên ảnh cần thiết được giữ lại. Nếu cần video hero, hãy chép lại các file MP4 từ bản đầy đủ vào thư mục `img/` và khôi phục phần `videos` trong `src/data/homeMedia.js`.

## 7. Lưu ý quan trọng

Không thể xác định mật khẩu, tên instance hoặc cổng SQL Server từ mã nguồn. Nếu SSMS kết nối được nhưng API không kết nối được, so sánh đúng 4 giá trị `DB_SERVER`/`DB_JDBC_URL`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` và bảo đảm hai bên vào cùng instance. Không chia sẻ file `backend/.env` vì có thể chứa mật khẩu.

## Thêm sản phẩm mới từ trang Admin

1. Đăng nhập bằng tài khoản có vai trò Admin (RoleID = 1).
2. Mở `/admin` → **Quản lý sản phẩm** → **Thêm sản phẩm**.
3. Nhập tên, giá, danh mục; chọn ít nhất một màu và ít nhất một size, sau đó thêm ảnh (URL hoặc tải ảnh) nếu cần.
4. Nhấn **Lưu sản phẩm**. Nếu thành công, danh sách sẽ tự tải lại và tổng sản phẩm tăng thêm 1.

Database cài mẫu có 6 sản phẩm ban đầu; đây không phải giới hạn. API `POST /api/products` tạo bản ghi mới trong SQL Server. Bảng `ProductVariants` của schema ShoeGroup gốc có cột `StockQuantity NOT NULL`; SD_11 không dùng cột này để kiểm soát mua hàng, nhưng API ghi giá trị trung tính `0` khi thêm một biến thể để tương thích với schema cũ. Không có chức năng nhập/trừ/hoàn tồn kho được kích hoạt bởi giá trị này.

Nếu trình biên dịch Vue báo `Element is missing end tag` tại `ProductsPage.vue` gần phần `v-else`, thay file bằng bản `src/views/admin/pages/ProductsPage.vue` trong gói cập nhật. Đảm bảo không có dấu `\` đứng trước các thẻ HTML/Vue, ví dụ phải là `<!-- ... -->` và `<div ...>`, không phải `\<!-- ... -->` hay `\<div ...>`.


## Khôi phục đăng nhập Admin trên máy phát triển

Nếu `POST /api/auth/login` trả `401` cho tài khoản quản trị mặc định, hãy xác nhận API đã kết nối được database `SD_11`, sao lưu database, mở `database/RESET_ADMIN_LOGIN_DEV_F5.sql` trong SSMS và chạy F5 trên đúng instance/database. Script chỉ khôi phục tài khoản `admin@shoegroup.com` (không xóa dữ liệu khác). Đăng nhập bằng mật khẩu tạm `Admin@12345`, sau đó đổi mật khẩu ngay. Không sử dụng mật khẩu tạm này ở môi trường production.

Địa chỉ `http://localhost:5173/api/...` là request đi qua Vite proxy; proxy chuyển tiếp tới Spring Boot `http://localhost:8080`. Do đó `401` từ `/api/auth/login` thường là thông tin đăng nhập không khớp hoặc tài khoản bị vô hiệu hóa, không phải lỗi cổng. Sau khi đăng nhập Admin thành công, API CRUD trong trang quản trị dùng JWT lấy từ phiên đăng nhập. Nếu thao tác nào trả `403`, cần kiểm tra role ID/quyền; nếu trả `500`, xem thông báo JSON và terminal backend để xác định cột/bảng SQL cụ thể.
