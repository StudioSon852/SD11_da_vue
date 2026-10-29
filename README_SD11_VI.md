# SD_11 / FREEDOMS — Website bán giày

## Công nghệ
- Frontend: Vue 3 + Vite.
- Backend: Spring Boot + Java 17 + Maven.
- Database: Microsoft SQL Server 2017+.

## 1. Cài database (một file SQL duy nhất)

1. Mở SQL Server Management Studio (SSMS) và kết nối đúng SQL Server instance mà backend sẽ dùng.
2. Sao lưu database nếu đang có dữ liệu cần giữ.
3. Mở `database/SD_11_COMPLETE.sql` và nhấn **F5 chạy toàn bộ file** (không cần bật SQLCMD Mode).
4. **Cảnh báo:** đây là script cài mới, sẽ xóa và tạo lại database `SD_11`. Ở bước cuối, script xóa lịch sử đơn hàng/thanh toán và các bản ghi phụ thuộc thuộc phạm vi reset để lịch sử về rỗng; không chạy trên production.
5. Xem kết quả ở cuối script: số bảng, khóa ngoại, index, số dòng mỗi bảng, `ShippingPricingRules`, `StoreSettings`, mã tham chiếu đơn trùng và lịch sử thanh toán.

Script tổng hợp bao gồm schema nền, nâng cấp POS/đơn hàng/thanh toán/phí ship, trạng thái đơn, hạng thành viên, ca làm/chấm công/lương và chuẩn hóa workflow. Không cần chạy từng file SQL riêng nữa.

## 2. Cấu hình kết nối backend

Sao chép `backend/.env.example` thành `backend/.env` rồi điền đúng thông tin SQL Server. Không đưa `.env`, mật khẩu hoặc JWT secret thật vào ZIP/git.

Ví dụ kết nối TCP mặc định:

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

Nếu dùng SQL Server Express/named instance, cấu hình `DB_JDBC_URL` trong `backend/.env` theo instance thực tế. Đảm bảo SSMS và backend dùng cùng instance/database.

## 3. Cài và chạy

Yêu cầu: Node.js LTS, JDK 17, Maven 3.9+.

Mở PowerShell tại thư mục chứa `package.json`, sau đó chạy:

```powershell
npm install
npm run dev
```

Hoặc mở hai cửa sổ terminal riêng:

```powershell
npm run dev:web
npm run dev:api
```

Frontend: `http://localhost:5173`  
API health: `http://localhost:8080/api/health`

## 4. Kiểm thử

```powershell
npm test
npm run build
```

Nếu API báo lỗi database, kiểm tra terminal backend và xác nhận các giá trị host/instance, tên database, user và password đúng với SSMS.

## 5. VietQR thật

Sao chép file `.env.example` ở thư mục gốc thành `.env.local`, điền `VITE_BANK_CODE`, `VITE_BANK_ACCOUNT`, `VITE_BANK_ACCOUNT_NAME`, rồi khởi động lại Vite. Không commit `.env.local`. Nếu các giá trị ngân hàng chưa cấu hình, QR sẽ chỉ là mã tham chiếu, chưa phải QR chuyển khoản ngân hàng thật.
