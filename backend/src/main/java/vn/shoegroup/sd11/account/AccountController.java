package vn.shoegroup.sd11.account;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.shoegroup.sd11.auth.JwtPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AccountController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AccountController(JdbcTemplate jdbc, PasswordEncoder passwords) { this.jdbc = jdbc; this.passwords = passwords; }

    @GetMapping("/addresses")
    public List<Map<String, Object>> addresses() {
        JwtPrincipal user = currentUser();
        return jdbc.queryForList("""
            SELECT AddressID id, UserID userId, RecipientName recipient, RecipientName fullName, Phone phone,
                   Province province, Province provinceName, District district, Ward ward, Ward communeName,
                   AddressLine line, AddressLine addressLine, FullAddress fullAddress,
                   ISNULL(IsVerified,0) isVerified, ISNULL(IsDefault,0) isDefault
            FROM dbo.UserAddresses WHERE UserID=? ORDER BY ISNULL(IsDefault,0) DESC, AddressID DESC
            """, user.id());
    }

    @PostMapping("/addresses")
    public Map<String, Object> createAddress(@RequestBody Map<String, Object> body) {
        JwtPrincipal user = currentUser();
        String recipient = text(body, "recipient", "fullName", user.fullName());
        String phone = text(body, "phone", "phone", "");
        String province = text(body, "province", "provinceName", "");
        String district = text(body, "district", "district", "");
        String ward = text(body, "ward", "communeName", "");
        String line = text(body, "line", "addressLine", "");
        String fullAddress = text(body, "fullAddress", "address", String.join(", ", List.of(line, ward, district, province).stream().filter(s -> !s.isBlank()).toList()));
        if (line.isBlank() && fullAddress.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập địa chỉ nhận hàng.");
        Boolean isDefault = body.get("isDefault") instanceof Boolean b ? b : Boolean.FALSE;
        if (isDefault) jdbc.update("UPDATE dbo.UserAddresses SET IsDefault=0 WHERE UserID=?", user.id());
        Long id = jdbc.queryForObject("""
            INSERT INTO dbo.UserAddresses (UserID, RecipientName, Phone, Province, District, Ward, AddressLine, FullAddress, IsVerified, IsDefault, CreatedAt)
            OUTPUT INSERTED.AddressID VALUES (?,?,?,?,?,?,?,?,0,?,GETDATE())
            """, Long.class, user.id(), recipient, phone.isBlank()?null:phone, province, district, ward, line, fullAddress, isDefault);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("id", id);
        result.put("userId", user.id());
        result.put("recipient", recipient);
        result.put("fullName", recipient);
        result.put("phone", phone);
        result.put("province", province);
        result.put("provinceName", province);
        result.put("district", district);
        result.put("ward", ward);
        result.put("communeName", ward);
        result.put("line", line);
        result.put("addressLine", line);
        result.put("fullAddress", fullAddress);
        result.put("isDefault", isDefault);
        return result;
    }

    @PutMapping("/addresses/{id}")
    public Map<String, Object> updateAddress(@PathVariable long id, @RequestBody Map<String, Object> body) {
        JwtPrincipal user = currentUser();
        if (Boolean.TRUE.equals(body.get("isDefault"))) {
            jdbc.update("UPDATE dbo.UserAddresses SET IsDefault=CASE WHEN AddressID=? THEN 1 ELSE 0 END WHERE UserID=?", id, user.id());
        } else {
            int updated = jdbc.update("""
                UPDATE dbo.UserAddresses SET RecipientName=COALESCE(?,RecipientName), Phone=COALESCE(?,Phone),
                  Province=COALESCE(?,Province), District=COALESCE(?,District), Ward=COALESCE(?,Ward),
                  AddressLine=COALESCE(?,AddressLine), FullAddress=COALESCE(?,FullAddress)
                WHERE AddressID=? AND UserID=?
                """, nullable(body,"recipient","fullName"), nullable(body,"phone","phone"), nullable(body,"province","provinceName"), nullable(body,"district","district"), nullable(body,"ward","communeName"), nullable(body,"line","addressLine"), nullable(body,"fullAddress","address"), id, user.id());
            if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ.");
        }
        return jdbc.queryForMap("SELECT AddressID id, UserID userId, RecipientName recipient, RecipientName fullName, Phone phone, Province province, Province provinceName, District district, Ward ward, Ward communeName, AddressLine line, AddressLine addressLine, FullAddress fullAddress, ISNULL(IsDefault,0) isDefault FROM dbo.UserAddresses WHERE AddressID=? AND UserID=?", id, user.id());
    }

    @DeleteMapping("/addresses/{id}")
    public Map<String, Object> deleteAddress(@PathVariable long id) {
        int n = jdbc.update("DELETE FROM dbo.UserAddresses WHERE AddressID=? AND UserID=?", id, currentUser().id());
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ.");
        return Map.of("success", true, "message", "Đã xóa địa chỉ.");
    }

    @PutMapping("/accounts/{id}")
    public Map<String, Object> updateProfile(@PathVariable long id, @RequestBody Map<String, Object> body) {
        JwtPrincipal user = currentUser();
        boolean admin = isAdmin(user);
        if (user.id() != id && !admin) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền cập nhật tài khoản này.");
        if (!admin && (body.containsKey("role_id") || body.containsKey("RoleID") || body.containsKey("active") || body.containsKey("IsActive")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Admin mới được phân quyền hoặc khóa/mở khóa tài khoản.");
        if (admin) {
            Map<String,Object> target = jdbc.queryForMap("SELECT u.UserID,u.RoleID,ISNULL(u.IsActive,1) IsActive,COALESCE(r.RoleName,'Customer') RoleName FROM dbo.Users u LEFT JOIN dbo.Roles r ON r.RoleID=u.RoleID WHERE u.UserID=?", id);
            boolean currentIsAdmin = isAdminRoleName(String.valueOf(target.get("RoleName")));
            boolean currentlyActive = !Boolean.FALSE.equals(target.get("IsActive")) && !"0".equals(String.valueOf(target.get("IsActive")));
            Object desiredRoleRaw = body.containsKey("role_id") ? body.get("role_id") : body.get("RoleID");
            Integer desiredRole = null;
            boolean desiredIsAdmin = false;
            if (desiredRoleRaw != null && !String.valueOf(desiredRoleRaw).isBlank()) {
                try { desiredRole = Integer.valueOf(String.valueOf(desiredRoleRaw)); }
                catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò không hợp lệ."); }
                List<String> targetRoles = jdbc.queryForList("SELECT RoleName FROM dbo.Roles WHERE RoleID=?", String.class, desiredRole);
                if (targetRoles.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò không tồn tại. Hãy tải lại danh sách phân quyền.");
                desiredIsAdmin = isAdminRoleName(targetRoles.get(0));
            }
            Boolean desiredActive = nullableBool(body, "active", "IsActive");
            if (desiredRole != null && !currentIsAdmin && desiredIsAdmin)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể cấp thêm quyền Admin từ màn quản lý tài khoản. Hãy giữ tài khoản quản trị hệ thống hiện có.");
            Long protectedAdminId = jdbc.queryForObject("SELECT MIN(u.UserID) FROM dbo.Users u JOIN dbo.Roles r ON r.RoleID=u.RoleID WHERE LOWER(r.RoleName) LIKE '%admin%'", Long.class);
            boolean adminWillBeRemoved = currentIsAdmin && currentlyActive && ((desiredRole != null && !desiredIsAdmin) || Boolean.FALSE.equals(desiredActive));
            if (adminWillBeRemoved && protectedAdminId != null && protectedAdminId == id)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đây là tài khoản Admin bảo vệ của hệ thống; không thể khóa hoặc hạ quyền tài khoản này.");
            if (adminWillBeRemoved) {
                Integer activeAdmins = jdbc.queryForObject("SELECT COUNT(1) FROM dbo.Users u JOIN dbo.Roles r ON r.RoleID=u.RoleID WHERE LOWER(r.RoleName) LIKE '%admin%' AND ISNULL(u.IsActive,1)=1", Integer.class);
                if (activeAdmins != null && activeAdmins <= 1)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể khóa hoặc hạ quyền Admin cuối cùng đang hoạt động. Hãy giữ ít nhất một tài khoản Admin để quản trị hệ thống.");
            }
        }
        String newPassword = body.get("password") == null ? "" : String.valueOf(body.get("password"));
        if (!newPassword.isEmpty() && !admin)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ quản trị viên mới được đổi mật khẩu tài khoản khác.");
        if (!newPassword.isEmpty() && (newPassword.length() < 8 || newPassword.length() > 128))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu phải có từ 8 đến 128 ký tự.");
        boolean changePassword = admin && !newPassword.isEmpty();
        String encodedPassword = changePassword ? passwords.encode(newPassword) : null;
        int n = jdbc.update("""
            UPDATE dbo.Users SET FullName=COALESCE(?,FullName), Phone=COALESCE(?,Phone), Address=COALESCE(?,Address), AvatarURL=COALESCE(?,AvatarURL),
              RoleID=CASE WHEN ?=1 THEN COALESCE(?,RoleID) ELSE RoleID END,
              IsActive=CASE WHEN ?=1 THEN COALESCE(?,IsActive) ELSE IsActive END,
              PasswordHash=CASE WHEN ?=1 THEN ? ELSE PasswordHash END,
              LastPasswordChangedAt=CASE WHEN ?=1 THEN GETDATE() ELSE LastPasswordChangedAt END,
              UpdatedAt=GETDATE() WHERE UserID=?
            """, nullable(body,"name","full_name"), nullable(body,"phone","phone"), nullable(body,"address","address"), nullable(body,"avatar_url","AvatarURL"),
            admin?1:0, nullable(body,"role_id","RoleID"), admin?1:0, nullableBool(body,"active","IsActive"),
            changePassword?1:0, encodedPassword, changePassword?1:0, id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản.");
        return jdbc.queryForMap("SELECT UserID id, UserID id_user, Email email, FullName full_name, Phone phone, Address address, AvatarURL avatar_url, RoleID role_id FROM dbo.Users WHERE UserID=?", id);
    }

    private static boolean isAdmin(JwtPrincipal user) {
        return user.roleId() == 1 || (user.role() != null && user.role().toLowerCase(java.util.Locale.ROOT).contains("admin"));
    }
    private static boolean isAdminRoleName(String role) {
        return role != null && role.toLowerCase(java.util.Locale.ROOT).contains("admin");
    }

    private static JwtPrincipal currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof JwtPrincipal u)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        return u;
    }
    private static String text(Map<String,Object> b, String a, String alt, String fallback) { Object v=b.containsKey(a)?b.get(a):b.get(alt); return v==null?fallback:String.valueOf(v).trim(); }

    private static Boolean nullableBool(Map<String,Object> body, String a, String alt) {
        Object value = body.containsKey(a) ? body.get(a) : body.get(alt);
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        String s = String.valueOf(value).trim();
        return !(s.isEmpty() || s.equals("0") || s.equalsIgnoreCase("false") || s.equalsIgnoreCase("off"));
    }

    private static Object nullable(Map<String,Object> b, String a, String alt) { Object v=b.containsKey(a)?b.get(a):b.get(alt); String s=v==null?"":String.valueOf(v).trim(); return s.isBlank()?null:s; }
}
