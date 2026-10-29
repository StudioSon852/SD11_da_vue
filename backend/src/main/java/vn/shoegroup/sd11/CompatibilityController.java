package vn.shoegroup.sd11;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.shoegroup.sd11.auth.JwtPrincipal;
import vn.shoegroup.sd11.order.OrderController;
import vn.shoegroup.sd11.product.ProductController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compatibility routes used by the original ShoeGroup UI. */
@RestController
@RequestMapping("/api")
public class CompatibilityController {
    private final JdbcTemplate jdbc;
    private final ProductController products;
    private final OrderController orders;

    public CompatibilityController(JdbcTemplate jdbc, ProductController products, OrderController orders) {
        this.jdbc = jdbc;
        this.products = products;
        this.orders = orders;
    }

    @GetMapping("/v2/products")
    public List<Map<String, Object>> listProducts(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer limit) {
        int pageNo = page == null ? 1 : Math.max(1, page);
        int pageSize = limit == null ? 12 : Math.max(1, Math.min(100, limit));
        List<Map<String, Object>> rows = products.list(q, category, Math.min(500, pageNo * pageSize));
        int start = Math.min(rows.size(), (pageNo - 1) * pageSize);
        int end = Math.min(rows.size(), start + pageSize);
        return rows.subList(start, end);
    }

    @GetMapping("/v2/products/featured")
    public List<Map<String, Object>> featuredProducts(@RequestParam(required = false) Integer limit) {
        int take = limit == null ? 8 : Math.max(1, Math.min(100, limit));
        return products.list(null, null, 500).stream()
            .filter(row -> truthy(row.get("is_featured")))
            .limit(take)
            .toList();
    }

    @GetMapping("/v2/orders")
    public List<Map<String, Object>> listOrders() {
        return orders.list();
    }

    @GetMapping("/v2/dashboard/summary")
    public Map<String, Object> dashboardSummary() {
        JwtPrincipal user = currentUser();
        if (user.roleId() != 1 && user.roleId() != 3 && user.roleId() != 4)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem báo cáo quản trị.");
        long productsCount = count("SELECT COUNT_BIG(*) FROM dbo.Products WHERE ISNULL(IsActive,1)=1");
        long ordersCount = count("SELECT COUNT_BIG(*) FROM dbo.Orders");
        long customersCount = count("SELECT COUNT_BIG(*) FROM dbo.Users WHERE RoleID=2");
        BigDecimal revenue = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN ISNULL(IsCountedAsRevenue,0)=1 THEN TotalAmount ELSE 0 END),0) FROM dbo.Orders", BigDecimal.class);
        if (revenue == null) revenue = BigDecimal.ZERO;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("totalProducts", productsCount);
        result.put("totalOrders", ordersCount);
        result.put("totalCustomers", customersCount);
        result.put("totalRevenue", revenue);
        result.put("products", productsCount);
        result.put("orders", ordersCount);
        result.put("customers", customersCount);
        result.put("revenue", revenue);
        return result;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private static boolean truthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        String s = String.valueOf(value).trim();
        return !s.isEmpty() && !s.equals("0") && !s.equalsIgnoreCase("false");
    }

    private static JwtPrincipal currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof JwtPrincipal user))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        return user;
    }
}
