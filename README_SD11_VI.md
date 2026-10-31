# SD_11 / FREEDOMS — Website bán giày

## Công nghệ
- Frontend: Vue 3 + Vite.
- Backend: Spring Boot + Java 17 + Maven.
- Database: Microsoft SQL Server 2017+.

## 1. Cài database (chỉ còn một file SQL)

1. Mở SQL Server Management Studio (SSMS), kết nối đúng SQL Server instance mà backend sẽ sử dụng.
2. Sao lưu database hiện tại nếu có dữ liệu cần giữ.
3. Mở duy nhất `database/SD_11_DATABASE_SETUP.sql`, nhấn **F5 chạy toàn bộ file**. Không cần bật SQLCMD Mode và không cần chạy thêm file Update riêng.
4. **CẢNH BÁO:** file này là bộ cài mới, tự xóa rồi tạo lại database `SD_11`. Mọi dữ liệu đang có trong database `SD_11` sẽ bị thay thế. Cuối script, phần Update đã được gộp vào cùng file: xóa tài khoản khách/nhân viên demo và dữ liệu cá nhân/lịch sử demo, chỉ giữ Admin và đặt lại mật khẩu.
5. Thông tin Admin sau khi chạy: tên đăng nhập alias `son` hoặc email `admin@company.com`; mật khẩu `adminTest!123`; tên hiển thị `Sơn (Admin)`.
6. Xem kết quả kiểm tra cuối file, đặc biệt số tài khoản kỳ vọng 1 Admin và số đơn hàng kỳ vọng 0.

Không chạy file này trên database production. Script yêu cầu SQL Server 2017+ và tài khoản có quyền tạo/xóa database. Schema nền, các migration POS/thanh toán/phí ship, trạng thái đơn, thành viên, giảm giá từng SKU, snapshot ưu đãi và cập nhật tài khoản Admin đều đã nằm trong file duy nhất.

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

Yêu cầu: Node.js LTS, JDK 17; Maven 3.9+ khi chạy backend bằng Terminal (IntelliJ có thể dùng Maven tích hợp sẵn).

Giải nén ZIP, mở PowerShell tại đúng thư mục có `package.json` (thư mục `SD11`), rồi sao chép `backend/.env.example` thành `backend/.env` và điền thông tin SQL Server. Sau khi cài database bằng SSMS, chạy:

```powershell
npm install
npm run dev
```

Trên Windows có thể chạy `START_SD11.bat` sau khi đã tạo `backend/.env`. Script sẽ mở hai terminal riêng cho API và web. Hoặc mở hai cửa sổ terminal riêng:

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


## 6. Mở backend bằng IntelliJ IDEA, giữ nguyên frontend Vue

**Không di chuyển `src/`, `public/`, `package.json`, `vite.config.js` hoặc `index.html`.** Frontend Vue hiện tại tiếp tục nằm ở thư mục gốc của `SD11`; backend Spring Boot đã được tách độc lập trong `backend/` theo chuẩn Maven. Không cần tạo thêm thư mục `frontend/` vì việc chuyển các file Vue có thể làm hỏng alias `@`, script npm và đường dẫn public.

Cấu trúc làm việc:

```text
SD11/                          <- thư mục dự án gốc
├─ src/                         <- Vue 3 (giữ nguyên)
├─ public/                      <- tài nguyên tĩnh Vue (giữ nguyên)
├─ package.json                 <- lệnh npm của frontend
├─ vite.config.js               <- proxy /api -> localhost:8080
├─ index.html
├─ database/
│  └─ SD_11_DATABASE_SETUP.sql  <- file SQL duy nhất
├─ backend/                     <- mở riêng bằng IntelliJ IDEA
│  ├─ pom.xml                   <- Maven/Spring Boot project
│  ├─ .env.example
│  ├─ .run/SD11_API.run.xml     <- cấu hình Run dùng chung cho IntelliJ
│  ├─ README_INTELLIJ_VI.md
│  └─ src/
│     └─ main/
│        ├─ java/vn/shoegroup/sd11/
│        └─ resources/application.properties
└─ README_SD11_VI.md
```

### A. Cấu hình IntelliJ IDEA

1. Cài JDK 17 (khuyến nghị đúng JDK 17 mà dự án đang dùng) và IntelliJ IDEA.
2. Mở IntelliJ → **File → Open** → chọn file `SD11/backend/pom.xml` hoặc chọn thư mục `SD11/backend` → chọn **Open as Project**. Chấp nhận **Load Maven Project** nếu IntelliJ hỏi.
3. Vào **File → Project Structure → Project**: đặt **SDK = JDK 17**, **Language level = 17**.
4. Mở tab Maven (biểu tượng m) → **Reload All Maven Projects**. Chờ IntelliJ tải dependencies trong `pom.xml`.
5. Tạo file cấu hình kết nối SQL: copy `backend/.env.example` thành `backend/.env`, rồi sửa `DB_SERVER`, `DB_PORT` hoặc `DB_JDBC_URL`, `DB_NAME=SD_11`, `DB_USER`, `DB_PASSWORD` và `JWT_SECRET`. Không gửi file `.env` cho người khác và không đưa vào ZIP công khai.
6. Sau khi Maven đồng bộ, chọn cấu hình dùng chung **SD11 API** (đã chuẩn bị tại `backend/.run/SD11_API.run.xml`). Nếu IntelliJ chưa nạp cấu hình này, mở `src/main/java/vn/shoegroup/sd11/Sd11ApiApplication.java`, nhấn tam giác xanh cạnh hàm `main` → **Run 'Sd11ApiApplication'**.
7. Nếu không đọc được `backend/.env`, mở **Run → Edit Configurations** → cấu hình Application `Sd11ApiApplication` → đặt **Working directory** thành thư mục gốc `SD11` (thư mục chứa `package.json`), không phải `backend`. Cấu hình `spring.config.import` hiện tại sẽ tìm thấy `backend/.env` theo đường dẫn này.
8. Xác nhận backend chạy tại `http://localhost:8080/api/health`.

> Nếu IntelliJ IDEA Community không hiện các wizard Spring, vẫn có thể chạy bình thường bằng Application configuration trên class `Sd11ApiApplication`; dự án không phụ thuộc Spring wizard.

### B. Chạy frontend Vue hiện tại

Giữ thư mục gốc `SD11` làm working directory. Mở một terminal riêng (PowerShell hoặc VS Code Terminal):

```powershell
cd đường_dẫn_tới\SD11
npm install
npm run dev:web
```

Mở `http://localhost:5173`. Có thể chạy nhanh bằng `START_FRONTEND_FOR_INTELLIJ.bat`. `vite.config.js` đã proxy toàn bộ đường dẫn `/api` sang `http://localhost:8080`, vì vậy không cần đổi URL API trong Vue, không cần sửa `src/services/apiClient.js` và không phải bật CORS thủ công cho luồng proxy mặc định.

### C. Cách chạy mỗi lần làm việc

- IntelliJ IDEA: chạy `Sd11ApiApplication` để bật backend.
- PowerShell ở thư mục gốc `SD11`: chạy `npm run dev:web` để bật Vue.
- Chỉ chạy SQL một lần sau khi cài database; không chạy lại mỗi lần mở website.
- Dừng backend bằng nút Stop trong IntelliJ và dừng frontend bằng `Ctrl + C` trong terminal.

**Lưu ý về tính độc lập:** IntelliJ chỉ quản lý Maven/Java trong `backend`; npm/Vite vẫn quản lý frontend từ `package.json` ở thư mục gốc. Hai tiến trình trao đổi qua HTTP `/api` ở cổng 8080, nên tách môi trường phát triển không làm thay đổi cấu trúc hoặc mã nguồn Vue hiện tại.
