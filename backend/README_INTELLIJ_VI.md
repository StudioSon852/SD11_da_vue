# Hướng dẫn mở Spring Boot backend bằng IntelliJ IDEA

## Mục đích

Backend là project Maven độc lập tại thư mục này. Frontend Vue vẫn ở thư mục cha `..`; không di chuyển hoặc đổi tên bất kỳ file Vue nào.

## Cấu trúc backend

```text
backend/
├─ pom.xml
├─ .env.example
└─ src/
   └─ main/
      ├─ java/vn/shoegroup/sd11/
      │  ├─ Sd11ApiApplication.java
      │  ├─ account/
      │  ├─ admin/
      │  ├─ auth/
      │  ├─ catalog/
      │  ├─ common/
      │  ├─ config/
      │  ├─ order/
      │  ├─ product/
      │  └─ wallet/
      └─ resources/application.properties
```

## Cấu hình lần đầu

1. Cài JDK 17 và IntelliJ IDEA.
2. Chọn **File → Open** rồi mở `backend/pom.xml` (hoặc thư mục `backend`).
3. Chọn JDK 17 tại **File → Project Structure → Project SDK**.
4. Mở Maven tool window và chọn **Reload All Maven Projects**.
5. Sao chép `.env.example` thành `.env` ngay trong `backend/`; khai báo thông tin SQL Server thật và JWT secret.
6. Sau khi Maven đồng bộ, chọn cấu hình dùng chung **SD11 API** nếu IntelliJ đã nạp file `backend/.run/SD11_API.run.xml`; cấu hình này đặt sẵn main class và working directory. Nếu chưa thấy, mở class `vn.shoegroup.sd11.Sd11ApiApplication` và chạy hàm `main`.
7. Nếu tạo cấu hình chạy thủ công hoặc file `.env` không được nạp, vào **Run → Edit Configurations** và đặt Working directory thành thư mục cha của `backend` (thư mục có `package.json`).
8. Kiểm tra `http://localhost:8080/api/health`.

## Kết nối Vue

Frontend gọi API tương đối qua `/api`. Cấu hình `vite.config.js` ở thư mục cha đã chuyển `/api` đến `http://localhost:8080`. Vì vậy chỉ cần chạy frontend riêng tại thư mục cha:

```powershell
cd ..
npm install
npm run dev:web
```

Website chạy ở `http://localhost:5173`; backend chạy trong IntelliJ ở cổng `8080`. Giữ cả hai tiến trình chạy khi sử dụng website.

## Không làm

- Không chuyển `src/`, `public/`, `package.json`, `vite.config.js` vào `backend/`.
- Không tạo một project Vue mới trong IntelliJ.
- Không commit `backend/.env`, mật khẩu SQL Server hoặc JWT secret.
- Không chạy lại SQL setup mỗi lần khởi động ứng dụng.
