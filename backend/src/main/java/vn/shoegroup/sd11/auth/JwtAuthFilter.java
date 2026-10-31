package vn.shoegroup.sd11.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Verifies JWT and re-checks live account status/role so a blocked account loses access immediately. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;

    public JwtAuthFilter(JwtService jwtService, JdbcTemplate jdbc) {
        this.jwtService = jwtService;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        final Claims claims;
        try {
            claims = jwtService.parse(header.substring(7).trim());
        } catch (Exception invalidToken) {
            // Spring Security will reject the request if the endpoint requires authentication.
            chain.doFilter(request, response);
            return;
        }

        long userId;
        try { userId = Long.parseLong(claims.getSubject()); }
        catch (Exception invalidSubject) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }

        try {
            List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT u.UserID, u.Email, u.FullName, ISNULL(u.IsActive,1) IsActive,
                       u.RoleID, COALESCE(r.RoleName,'Customer') RoleName
                FROM dbo.Users u LEFT JOIN dbo.Roles r ON r.RoleID=u.RoleID
                WHERE u.UserID=?
                """, userId);
            if (rows.isEmpty()) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Tài khoản không còn tồn tại. Vui lòng đăng nhập lại.");
                return;
            }
            Map<String,Object> row = rows.get(0);
            Object activeValue = row.get("IsActive");
            boolean active = !(Boolean.FALSE.equals(activeValue) || (activeValue instanceof Number n && n.intValue() == 0)
                    || "false".equalsIgnoreCase(String.valueOf(activeValue)) || "0".equals(String.valueOf(activeValue)));
            if (!active) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Tài khoản đã bị khóa. Mọi chức năng yêu cầu đăng nhập đã bị vô hiệu hóa.");
                return;
            }

            String email = String.valueOf(row.get("Email"));
            String fullName = String.valueOf(row.get("FullName"));
            int roleId = row.get("RoleID") instanceof Number n ? n.intValue() : 2;
            String role = normalizeRole(String.valueOf(row.get("RoleName")), roleId);
            JwtPrincipal principal = new JwtPrincipal(userId, email, role, roleId, fullName);
            String authority = "ROLE_" + role.toUpperCase(Locale.ROOT).replace(' ', '_');
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(authority))));
        } catch (DataAccessException databaseError) {
            System.err.println("[AUTH-ACCOUNT-STATUS] Không kiểm tra được trạng thái tài khoản: " + databaseError.getMessage());
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Không thể xác minh trạng thái tài khoản do database chưa kết nối. Kiểm tra SQL Server rồi thử lại.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String normalizeRole(String raw, int roleId) {
        String name = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (name.contains("admin")) return "Admin";
        if (name.contains("manager") || name.contains("quan ly") || name.contains("quản lý")) return "Manager";
        if (name.contains("employee") || name.contains("staff") || name.contains("nhan vien") || name.contains("nhân viên")) return "Employee";
        if (roleId == 1) return "Admin";
        if (roleId == 3) return "Employee";
        if (roleId == 4) return "Manager";
        return "Customer";
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        response.getWriter().write("{\"success\":false,\"message\":\"" + escaped + "\"}");
    }
}
