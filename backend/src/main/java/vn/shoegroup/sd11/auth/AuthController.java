package vn.shoegroup.sd11.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class AuthController {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RESET_RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordService passwords;
    private final JwtService jwt;
    @Value("${app.auth.expose-reset-token:false}")
    private boolean exposeResetToken;

    public AuthController(JdbcTemplate jdbc, PasswordService passwords, JwtService jwt) {
        this.jdbc = jdbc; this.passwords = passwords; this.jwt = jwt;
    }

    @PostMapping({"/auth/login", "/login"})
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String email = text(body.get("email")).toLowerCase(Locale.ROOT);
        String password = text(body.get("password"));
        if (email.isBlank() || !EMAIL.matcher(email).matches() || password.isEmpty() || password.length() > 256)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập email và mật khẩu hợp lệ.");
        var rows = jdbc.queryForList("""
            SELECT TOP 1 u.UserID id_user, u.Email email, u.PasswordHash password_hash,
                   u.FullName full_name, u.Phone phone, u.Address address, u.AvatarURL avatar_url,
                   u.RoleID role_id, r.RoleName role_name, u.IsActive active
            FROM dbo.Users u LEFT JOIN dbo.Roles r ON r.RoleID=u.RoleID
            WHERE LOWER(u.Email)=? ORDER BY u.UserID
            """, email);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác.");
        Map<String, Object> row = rows.get(0);
        if (!truthy(row.get("active")) || !passwords.matches(password, text(row.get("password_hash"))))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác.");
        long userId = number(row.get("id_user"));
        int roleId = (int) number(row.get("role_id"));
        String role = roleText(text(row.get("role_name")), roleId);
        String fullName = text(row.get("full_name"));
        // Upgrade older plaintext demo credentials to the current scrypt format after successful authentication.
        String stored = text(row.get("password_hash"));
        if (!stored.startsWith("scrypt$") && !stored.startsWith("$2")) {
            jdbc.update("UPDATE dbo.Users SET PasswordHash=?, LastPasswordChangedAt=COALESCE(LastPasswordChangedAt, GETDATE()) WHERE UserID=?", passwords.hash(password), userId);
        }
        String token = jwt.issue(userId, email, role, roleId, fullName);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userId); user.put("id_user", userId); user.put("email", email);
        user.put("full_name", fullName); user.put("phone", row.get("phone")); user.put("address", row.get("address"));
        user.put("avatar_url", row.get("avatar_url")); user.put("role_id", roleId); user.put("role", role); user.put("token", token);
        return Map.of("success", true, "message", "Đăng nhập thành công.", "user", user, "token", token);
    }

    @PostMapping({"/auth/register", "/register"})
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String fullName = text(first(body, "full_name", "fullName")).trim();
        String email = text(body.get("email")).trim().toLowerCase(Locale.ROOT);
        String password = text(body.get("password"));
        String phone = text(body.get("phone")).trim();
        String address = text(body.get("address")).trim();
        String birth = text(body.get("birth_date"));
        if (fullName.length() < 2 || fullName.length() > 100 || !EMAIL.matcher(email).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng kiểm tra họ tên và email.");
        if (password.length() < 8 || password.length() > 128)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu phải có từ 8 đến 128 ký tự.");
        if (!phone.isBlank() && !phone.matches("0[0-9]{9}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.");
        java.sql.Date birthDate = null;
        if (!birth.isBlank()) {
            try { birthDate = java.sql.Date.valueOf(LocalDate.parse(birth)); }
            catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày sinh không hợp lệ."); }
            if (birthDate.after(java.sql.Date.valueOf(LocalDate.now()))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày sinh không thể ở tương lai.");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Users WHERE LOWER(Email)=?", Integer.class, email) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email này đã được sử dụng.");
        try {
            KeyHolder kh = new GeneratedKeyHolder();
            final java.sql.Date finalBirthDate = birthDate;
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO dbo.Users (RoleID, Email, PasswordHash, FullName, Phone, Address, IsActive, CreatedAt, EmailVerified, PointBalance, BirthDate)
                    VALUES (2, ?, ?, ?, ?, ?, 1, GETDATE(), 0, 0, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, email); ps.setString(2, passwords.hash(password)); ps.setNString(3, fullName);
                if (phone.isBlank()) ps.setNull(4, Types.VARCHAR); else ps.setString(4, phone);
                if (address.isBlank()) ps.setNull(5, Types.NVARCHAR); else ps.setNString(5, address);
                if (finalBirthDate == null) ps.setNull(6, Types.DATE); else ps.setDate(6, finalBirthDate);
                return ps;
            }, kh);
            Number generated = kh.getKey();
            long userId = generated == null ? jdbc.queryForObject("SELECT UserID FROM dbo.Users WHERE Email=?", Long.class, email) : generated.longValue();
            String role = "Customer";
            String token = jwt.issue(userId, email, role, 2, fullName);
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", userId); user.put("id_user", userId); user.put("email", email); user.put("full_name", fullName);
            user.put("phone", phone); user.put("address", address); user.put("role_id", 2); user.put("role", role); user.put("token", token);
            return Map.of("success", true, "message", "Đăng ký thành công. Vui lòng đăng nhập.", "user", user, "token", token);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email này đã được sử dụng.");
        }
    }

    /** Generates a time-limited password-reset request. Real email delivery can be configured separately. */
    @PostMapping("/auth/forgot-password")
    public Map<String, Object> forgotPassword(@RequestBody Map<String, Object> body) {
        String email = text(body.get("email")).trim().toLowerCase(Locale.ROOT);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Nếu email tồn tại, yêu cầu đặt lại mật khẩu đã được ghi nhận.");
        if (!EMAIL.matcher(email).matches()) return response;

        List<Long> ids = jdbc.queryForList("SELECT TOP 1 UserID FROM dbo.Users WHERE LOWER(Email)=? AND ISNULL(IsActive,1)=1 ORDER BY UserID", Long.class, email);
        if (ids.isEmpty()) return response;

        byte[] bytes = new byte[32];
        RESET_RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        String tokenHash = sha256(token);
        jdbc.update("UPDATE dbo.Users SET PasswordResetToken=?,PasswordResetTokenExpiry=DATEADD(MINUTE,30,GETDATE()),UpdatedAt=GETDATE() WHERE UserID=?", tokenHash, ids.get(0));
        // Never expose a reset token in a deployed environment. Enable only for local testing.
        if (exposeResetToken) response.put("resetToken", token);
        return response;
    }

    @PostMapping("/auth/reset-password")
    public Map<String, Object> resetPassword(@RequestBody Map<String, Object> body) {
        String token = text(body.get("token")).trim();
        String password = text(first(body, "newPassword", "new_password"));
        if (token.length() < 32 || token.length() > 128)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Liên kết đặt lại mật khẩu không hợp lệ.");
        if (password.length() < 8 || password.length() > 128)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải có từ 8 đến 128 ký tự.");

        String tokenHash = sha256(token);
        List<Long> ids = jdbc.queryForList("SELECT TOP 1 UserID FROM dbo.Users WHERE PasswordResetToken=? AND PasswordResetTokenExpiry>GETDATE() AND ISNULL(IsActive,1)=1 ORDER BY UserID", Long.class, tokenHash);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Liên kết đặt lại mật khẩu đã hết hạn hoặc không hợp lệ.");
        int updated = jdbc.update("UPDATE dbo.Users SET PasswordHash=?,PasswordResetToken=NULL,PasswordResetTokenExpiry=NULL,LastPasswordChangedAt=GETDATE(),UpdatedAt=GETDATE() WHERE UserID=? AND PasswordResetToken=? AND PasswordResetTokenExpiry>GETDATE()", passwords.hash(password), ids.get(0), tokenHash);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Liên kết đặt lại mật khẩu đã hết hạn hoặc không hợp lệ.");
        return Map.of("success", true, "message", "Mật khẩu đã được đặt lại thành công.");
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("Không thể tạo token bảo mật.", ex); }
    }

    private static Object first(Map<String, Object> map, String a, String b) { return map.containsKey(a) ? map.get(a) : map.get(b); }
    static String text(Object v) { return v == null ? "" : String.valueOf(v); }
    static long number(Object v) { try { return Long.parseLong(String.valueOf(v)); } catch (Exception ex) { return 0; } }
    static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0d;
        String s = String.valueOf(v).trim();
        return !s.isEmpty() && !s.equalsIgnoreCase("false") && !s.equals("0") && !s.equalsIgnoreCase("off");
    }
    static String roleText(String dbRole, int roleId) {
        if (!dbRole.isBlank()) {
            String clean = dbRole.trim();
            if (clean.equalsIgnoreCase("nhan vien") || clean.equalsIgnoreCase("nhân viên")) return "Employee";
            if (clean.equalsIgnoreCase("quan ly") || clean.equalsIgnoreCase("quản lý")) return "Manager";
            return clean;
        }
        return roleId == 1 ? "Admin" : roleId == 4 ? "Manager" : roleId == 3 ? "Employee" : "Customer";
    }
}
