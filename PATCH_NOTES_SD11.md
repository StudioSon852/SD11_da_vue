# Bản sửa lỗi SD_11 (25/10/2026)

## Lỗi đã sửa

1. `src/views/LoginView.vue`: đọc trực tiếp JSON từ `api.post()` thay vì bóc `data` theo kiểu Axios. Trước đó biến `data` luôn có thể là `undefined`, khiến đăng nhập báo thiếu `user` hoặc JWT dù API trả về đúng.
2. `src/views/RegisterView.vue`: sửa cùng lỗi đọc phản hồi khi đăng ký.
3. `src/views/HomeView.vue`: sửa cùng lỗi đọc danh sách sản phẩm từ `api.get('/products')`.
4. `src/services/httpInterceptor.js`: chuẩn hóa URL API cũ và API `/api` hiện tại; bảo toàn method, body và header khi đầu vào là `Request`; chuyển URL về dạng tuyệt đối trước khi tạo `Request`; không đăng xuất người dùng chỉ vì endpoint đăng nhập/đăng ký/quên mật khẩu trả về 401.
5. `src/services/apiClient.js`: không tự xóa phiên đăng nhập khi endpoint xác thực công khai trả về 401 (ví dụ mật khẩu đăng nhập sai).
6. `src/views/admin/pages/ProductsPage.vue`: đóng thẻ `div` còn thiếu trong vòng lặp màu/biến thể. Lỗi này có thể làm Vue/Vite báo thiếu thẻ đóng và khiến toàn ứng dụng không biên dịch được.

## Chạy dự án

1. Sao lưu thư mục dự án hiện tại.
2. Giải nén ZIP vào một thư mục mới hoặc ghi đè các file nguồn đã sửa.
3. Giữ/copy file cấu hình thật `backend/.env` của bạn; ZIP không chứa mật khẩu SQL Server.
4. Trong thư mục gốc dự án chạy `npm install`.
5. Chạy cả hai dịch vụ bằng `npm run dev`, hoặc mở hai Terminal và chạy `npm run dev:api` cùng `npm run dev:web`.
6. Kiểm tra `http://localhost:8080/api/health` trước khi thử các chức năng cần database.

## Phạm vi xác minh

- Đã kiểm tra cú pháp các file JavaScript bằng `node --check`.
- Đã chạy smoke test cô lập cho interceptor: URL API cũ, header JWT, body POST, Request cùng nguồn và xử lý 401 của endpoint công khai/bảo vệ.
- Chưa xác minh được toàn bộ `npm test` / `npm run build` trong môi trường đóng gói vì cài đặt npm bị timeout và thiếu gói `esbuild`; môi trường đóng gói cũng không có Maven. Hãy chạy build/test sau khi cài đủ dependency trên máy phát triển.


## Bổ sung xử lý lỗi 401 và khôi phục đăng nhập Admin

7. `src/services/apiClient.js` và `src/services/httpInterceptor.js`: loại bỏ header Authorization cũ khỏi endpoint đăng nhập/đăng ký/quên/đặt lại mật khẩu; đọc thêm `detail`/`title` trong response lỗi Spring Boot, hiển thị thông báo hướng dẫn rõ hơn cho 401/403. URL `/api/...` tại `localhost:5173` vẫn được chuyển qua Vite proxy tới Spring Boot `localhost:8080`.
8. `database/RESET_ADMIN_LOGIN_DEV_F5.sql`: script khôi phục **local development** cho `admin@shoegroup.com`, bảo đảm role Admin/Manager theo ID mà API sử dụng, không xóa dữ liệu khác. Sau khi chạy bằng F5, dùng mật khẩu tạm `Admin@12345`; đổi mật khẩu ngay sau khi đăng nhập. Chỉ chạy khi cần khôi phục tài khoản local, sau khi đã kiểm tra/sao lưu database.
9. HTTP 401 ở endpoint đăng nhập là phản hồi xác thực từ backend, không phải bằng chứng lỗi URL proxy. Khi tài khoản/mật khẩu sai, 401 vẫn là phản hồi đúng; bản vá giúp lỗi hiện rõ hơn và kèm script khôi phục cho tài khoản demo local.


## Kiểm tra bổ sung ngày 26/10/2026

- Xác minh lại cấu hình route: `/api/auth/login` và `/api/auth/register` được `permitAll`; request `/api/...` từ cổng 5173 đi qua Vite proxy tới cổng 8080. `401` ở login xảy ra trong controller khi email không tìm thấy, mật khẩu không khớp hoặc tài khoản không active.
- Tạo script `database/RESET_ADMIN_LOGIN_DEV_F5.sql` để khôi phục tài khoản demo local; hash scrypt trong script đã được đối chiếu với `Node crypto.scrypt` theo đúng `N=16384, r=8, p=1, dkLen=64`.
- `node --check` đã chạy qua `apiClient.js`, `httpInterceptor.js`, `authStore.js` và `adminStore.js`. `npm test` chưa chạy được vì gói `esbuild` không có trong môi trường đóng gói; thử `npm install` bị timeout. Chưa có kết nối tới SQL Server thật/Maven trong môi trường đóng gói nên các thao tác CRUD vẫn cần xác nhận trực tiếp trên máy của bạn sau khi chạy schema fix và đăng nhập thành công.

10. Admin account CRUD: bổ sung `POST /api/accounts` (chỉ Admin được tạo tài khoản, kiểm tra email/role/mật khẩu và hash mật khẩu bằng BCrypt); cập nhật `PUT /api/accounts/{id}` để Admin đổi mật khẩu khi nhập giá trị mới, còn để trống thì giữ mật khẩu cũ. Bật chỉnh sửa role trong form và đồng bộ các role 1=Admin, 2=Customer, 3=Employee, 4=Manager.
