package vn.shoegroup.sd11.common;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Converts SQL Server exceptions to local-development messages without exposing credentials. */
public final class DatabaseDiagnostics {
    private DatabaseDiagnostics() {}

    public static String userMessage(Throwable error) {
        String text = chainText(error).toLowerCase(Locale.ROOT);
        if (text.contains("login failed for user") || text.contains("18456")) {
            return "SQL Server đã phản hồi nhưng từ chối đăng nhập. Hãy kiểm tra DB_USER/DB_PASSWORD, bật SQL Server and Windows Authentication (Mixed Mode), rồi khởi động lại dịch vụ SQL Server.";
        }
        if (text.contains("cannot open database") || text.contains("error 4060") || text.contains("requested by the login")) {
            return "Đăng nhập SQL Server được nhưng không mở được database. Hãy kiểm tra DB_NAME=SD_11, chắc chắn đã chạy script database trên đúng instance và tài khoản có quyền CONNECT/đọc/ghi database SD_11.";
        }
        if (text.contains("connection refused") || text.contains("connect timed out") || text.contains("connection timed out")
                || text.contains("network-related or instance-specific") || text.contains("tcp provider")
                || text.contains("no such host") || text.contains("unknown host") || text.contains("could not connect")) {
            return "Không tìm thấy SQL Server ở địa chỉ đã cấu hình. Kiểm tra dịch vụ SQL Server đang chạy, TCP/IP và DB_SERVER/DB_PORT. Với SQLEXPRESS, đặt DB_JDBC_URL dùng instanceName=SQLEXPRESS; nếu dùng cổng tĩnh thì bật TCP/IP và xác nhận cổng trong SQL Server Configuration Manager.";
        }
        if (text.contains("invalid object name") || text.contains("invalid column name") || text.contains("could not find stored procedure")
                || text.contains("object name") && text.contains("does not exist")) {
            return "Đã vào được database nhưng cấu trúc bảng/view chưa khớp với API. File database/SD_11_COMPLETE.sql là bộ cài mới có tính phá hủy (tạo lại SD_11 và reset lịch sử đơn/thanh toán); chỉ chạy sau khi sao lưu và chỉ khi bạn chấp nhận cài mới DEV/TEST. Nếu cần giữ dữ liệu đang có, không chạy file này; xem dòng [DB-ERROR] để biết cột/bảng còn thiếu.";
        }
        if (text.contains("permission was denied") || text.contains("the select permission was denied") || text.contains("the insert permission was denied") || text.contains("the update permission was denied")) {
            return "Tài khoản SQL đăng nhập được nhưng chưa đủ quyền trên database SD_11. Hãy cấp quyền kết nối và quyền SELECT/INSERT/UPDATE/DELETE trên các bảng ứng dụng cho DB_USER.";
        }
        if (text.contains("password") && (text.contains("empty") || text.contains("null") || text.contains("not set"))) {
            return "DB_PASSWORD chưa được cấu hình. Hãy sao chép backend/.env.example thành backend/.env và nhập mật khẩu SQL Server thực tế.";
        }
        String detail = rootCause(error).getMessage();
        String safeDetail = safe(detail == null || detail.isBlank() ? error.getClass().getSimpleName() : detail);
        return "Không truy vấn được database SD_11. Chi tiết SQL Server: " + safeDetail + ". Kiểm tra bảng/cột theo log [DB-ERROR], cấu hình DB_JDBC_URL (nếu có), DB_SERVER, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD và quyền SQL.";
    }

    public static void log(String context, Throwable error) {
        Throwable root = rootCause(error);
        System.err.println("[DB-ERROR] " + context + ": " + root.getClass().getSimpleName() + ": " + safe(root.getMessage()));
        Throwable t = error;
        int count = 0;
        while (t != null && count++ < 8) {
            if (t instanceof SQLException sql) {
                System.err.println("[DB-ERROR] SQLState=" + sql.getSQLState() + ", errorCode=" + sql.getErrorCode());
                break;
            }
            t = t.getCause();
        }
        System.err.println("[DB-GUIDE] " + userMessage(error));
    }

    private static String chainText(Throwable error) {
        List<String> parts = new ArrayList<>();
        Throwable t = error;
        int count = 0;
        while (t != null && count++ < 12) {
            if (t.getMessage() != null) parts.add(t.getMessage());
            if (t instanceof SQLException sql && sql.getNextException() != null) parts.add(sql.getNextException().getMessage());
            t = t.getCause();
        }
        return String.join(" | ", parts);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable t = error;
        int count = 0;
        while (t != null && t.getCause() != null && t.getCause() != t && count++ < 20) t = t.getCause();
        return t == null ? error : t;
    }

    private static String safe(String message) {
        if (message == null || message.isBlank()) return "no detail";
        return message.replaceAll("(?i)(password|pwd)\\s*=\\s*[^;\\s]+", "$1=***");
    }
}
