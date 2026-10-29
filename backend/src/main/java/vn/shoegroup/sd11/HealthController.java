package vn.shoegroup.sd11;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.shoegroup.sd11.common.DatabaseDiagnostics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    @Value("${app.db.configured-name:SD_11}")
    private String configuredDatabase;

    public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping("/api/log-error")
    public Map<String,Object> logError(@RequestBody Map<String,Object> body) {
        Object message = body.get("message");
        System.err.println("[FRONTEND-ERROR] " + String.valueOf(message));
        return Map.of("success", true);
    }

    /** Always answers when the API process is alive, even if SQL Server is unavailable. */
    @GetMapping({"/", "/api/health"})
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "SD_11 Spring Boot REST API");
        result.put("configuredDatabase", configuredDatabase);
        try {
            String connectedDatabase = jdbc.queryForObject("SELECT DB_NAME()", String.class);
            result.put("database", connectedDatabase);
            if (configuredDatabase != null && !configuredDatabase.isBlank() && !configuredDatabase.equalsIgnoreCase(connectedDatabase)) {
                result.put("success", false);
                result.put("status", "WRONG_DATABASE");
                result.put("message", "Backend đã kết nối SQL Server nhưng đang vào database " + connectedDatabase + ", trong khi DB_NAME được cấu hình là " + configuredDatabase + ". Hãy sửa backend/.env để DB_NAME đúng database đã chạy script SD_11.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
            }
            result.put("success", true);
            result.put("status", "UP");

            List<String> required = List.of("Users", "Roles", "Products", "ProductVariants", "Orders", "OrderDetails", "OrderStatusHistory", "OrderPaymentHistory", "PaymentTransactions", "UserAddresses", "ProductImages", "ShippingMethods", "PaymentMethods", "Categories", "Brands", "vw_ProductSoldCount");
            List<String> existing = jdbc.queryForList("SELECT name FROM sys.objects WHERE type IN ('U','V')", String.class);
            List<String> missing = new ArrayList<>();
            for (String table : required) if (existing.stream().noneMatch(table::equalsIgnoreCase)) missing.add(table);

            // Detect the most common incomplete/mismatched legacy schemas before the first page/API request fails.
            List<Map<String, Object>> requiredColumns = List.of(
                Map.of("tableName", "Users", "columnName", "UserID"),
                Map.of("tableName", "Users", "columnName", "Email"),
                Map.of("tableName", "Users", "columnName", "PasswordHash"),
                Map.of("tableName", "Users", "columnName", "FullName"),
                Map.of("tableName", "Users", "columnName", "RoleID"),
                Map.of("tableName", "Users", "columnName", "IsActive"),
                Map.of("tableName", "Roles", "columnName", "RoleID"),
                Map.of("tableName", "Roles", "columnName", "RoleName"),
                Map.of("tableName", "Products", "columnName", "ProductID"),
                Map.of("tableName", "Products", "columnName", "ProductName"),
                Map.of("tableName", "Products", "columnName", "BasePrice"),
                Map.of("tableName", "Products", "columnName", "ImageURL"),
                Map.of("tableName", "ProductVariants", "columnName", "ProductVariantID"),
                Map.of("tableName", "ProductVariants", "columnName", "ProductID"),
                Map.of("tableName", "ProductVariants", "columnName", "Size"),
                Map.of("tableName", "ProductVariants", "columnName", "ColorName"),
                Map.of("tableName", "Orders", "columnName", "OrderID"),
                Map.of("tableName", "Orders", "columnName", "TotalAmount"),
                Map.of("tableName", "Orders", "columnName", "OrderDate"),
                Map.of("tableName", "Orders", "columnName", "Status"),
                Map.of("tableName", "Orders", "columnName", "PaymentMethod"),
                Map.of("tableName", "Orders", "columnName", "PaymentStatus"),
                Map.of("tableName", "Orders", "columnName", "ShippingAddress"),
                Map.of("tableName", "Orders", "columnName", "CustomerName"),
                Map.of("tableName", "Orders", "columnName", "CustomerPhone"),
                Map.of("tableName", "Orders", "columnName", "AddressChangedAt"),
                Map.of("tableName", "Orders", "columnName", "SalesChannel"),
                Map.of("tableName", "Orders", "columnName", "PaymentReference"),
                Map.of("tableName", "Orders", "columnName", "CashReceived"),
                Map.of("tableName", "Orders", "columnName", "CashChange"),
                Map.of("tableName", "Orders", "columnName", "CompletedAt"),
                Map.of("tableName", "Orders", "columnName", "CancelledAt"),
                Map.of("tableName", "Orders", "columnName", "CancellationReason"),
                Map.of("tableName", "OrderStatusHistory", "columnName", "HistoryID"),
                Map.of("tableName", "OrderStatusHistory", "columnName", "OrderID"),
                Map.of("tableName", "OrderStatusHistory", "columnName", "NewStatus"),
                Map.of("tableName", "OrderStatusHistory", "columnName", "ChangedAt"),
                Map.of("tableName", "OrderPaymentHistory", "columnName", "OrderID"),
                Map.of("tableName", "OrderPaymentHistory", "columnName", "PaymentHistoryID"),
                Map.of("tableName", "OrderPaymentHistory", "columnName", "NewPaymentStatus"),
                Map.of("tableName", "OrderPaymentHistory", "columnName", "ChangedAt"),
                Map.of("tableName", "PaymentTransactions", "columnName", "TransactionID"),
                Map.of("tableName", "PaymentTransactions", "columnName", "OrderID"),
                Map.of("tableName", "PaymentTransactions", "columnName", "Provider"),
                Map.of("tableName", "PaymentTransactions", "columnName", "ProviderTxnRef"),
                Map.of("tableName", "PaymentTransactions", "columnName", "Amount"),
                Map.of("tableName", "PaymentTransactions", "columnName", "Status"),
                Map.of("tableName", "PaymentTransactions", "columnName", "CreatedAt"),
                Map.of("tableName", "PaymentTransactions", "columnName", "CompletedAt"),
                Map.of("tableName", "OrderDetails", "columnName", "OrderDetailID"),
                Map.of("tableName", "OrderDetails", "columnName", "OrderID"),
                Map.of("tableName", "OrderDetails", "columnName", "ProductID"),
                Map.of("tableName", "OrderDetails", "columnName", "Quantity"),
                Map.of("tableName", "OrderDetails", "columnName", "ProductNameSnapshot"),
                Map.of("tableName", "OrderDetails", "columnName", "SKUSnapshot"),
                Map.of("tableName", "OrderDetails", "columnName", "ColorHex"),
                Map.of("tableName", "OrderDetails", "columnName", "ImageURLSnapshot"),
                Map.of("tableName", "UserAddresses", "columnName", "AddressID"),
                Map.of("tableName", "UserAddresses", "columnName", "UserID"),
                Map.of("tableName", "UserAddresses", "columnName", "FullAddress"),
                Map.of("tableName", "ProductImages", "columnName", "ProductID"),
                Map.of("tableName", "ProductImages", "columnName", "ImageURL"),
                Map.of("tableName", "ShippingMethods", "columnName", "MethodCode"),
                Map.of("tableName", "PaymentMethods", "columnName", "MethodCode")
            );
            List<String> missingColumns = new ArrayList<>();
            for (Map<String, Object> requiredColumn : requiredColumns) {
                String table = String.valueOf(requiredColumn.get("tableName"));
                String column = String.valueOf(requiredColumn.get("columnName"));
                Integer present = jdbc.queryForObject("SELECT CASE WHEN COL_LENGTH(?, ?) IS NULL THEN 0 ELSE 1 END", Integer.class, "dbo." + table, column);
                if (present == null || present == 0) missingColumns.add(table + "." + column);
            }
            if (!missing.isEmpty() || !missingColumns.isEmpty()) {
                result.put("success", false);
                result.put("status", "SCHEMA_INCOMPLETE");
                result.put("missingObjects", missing);
                result.put("missingColumns", missingColumns);
                List<String> missingDetails = new ArrayList<>(missing);
                missingDetails.addAll(missingColumns);
                result.put("message", "SQL Server đã kết nối, nhưng database " + connectedDatabase + " chưa khớp cấu trúc API. Thiếu: " + String.join(", ", missingDetails) + ". File database/SD_11_COMPLETE.sql là bộ cài mới có tính phá hủy (tạo lại SD_11 và reset lịch sử đơn/thanh toán); chỉ chạy sau khi sao lưu và chỉ khi bạn chấp nhận cài mới DEV/TEST. Nếu cần giữ dữ liệu hiện có, không chạy file này.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
            }
            result.put("message", "Kết nối SQL Server thành công và các bảng lõi của SD_11 đã sẵn sàng.");
            return ResponseEntity.ok(result);
        } catch (DataAccessException ex) {
            DatabaseDiagnostics.log("GET /api/health", ex);
            result.put("success", false);
            result.put("status", "DATABASE_DOWN");
            result.put("message", DatabaseDiagnostics.userMessage(ex));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
    }
}
