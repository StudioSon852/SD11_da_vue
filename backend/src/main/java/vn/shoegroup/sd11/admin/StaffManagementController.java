package vn.shoegroup.sd11.admin;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.shoegroup.sd11.auth.JwtPrincipal;
import java.util.Locale;
import java.util.Map;

/** Hồ sơ tự phục vụ của nhân viên; phần ca làm và lương đã được loại khỏi hệ thống. */
@RestController
@RequestMapping("/api")
public class StaffManagementController {
    private final JdbcTemplate jdbc;
    public StaffManagementController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/employee/profile")
    public Map<String,Object> employeeProfile() {
        JwtPrincipal user = requireEmployee();
        return jdbc.queryForMap("SELECT UserID id,FullName fullName,Email email,Phone phone,Address address,ISNULL(IsActive,1) active FROM dbo.Users WHERE UserID=?", user.id());
    }

    @PutMapping("/employee/profile")
    public Map<String,Object> updateEmployeeProfile(@RequestBody Map<String,Object> body) {
        JwtPrincipal user = requireEmployee();
        String name = text(body.get("fullName")).trim();
        String phone = text(body.get("phone")).trim();
        String address = text(body.get("address")).trim();
        if (name.length() < 2 || name.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ tên phải có từ 2 đến 100 ký tự.");
        if (!phone.isBlank() && !phone.matches("0[0-9]{9}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại phải có 10 chữ số và bắt đầu bằng 0.");
        jdbc.update("UPDATE dbo.Users SET FullName=?,Phone=?,Address=?,UpdatedAt=GETDATE() WHERE UserID=?", name, phone.isBlank()?null:phone, address.isBlank()?null:address, user.id());
        return employeeProfile();
    }

    private static JwtPrincipal requireEmployee() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtPrincipal user)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        String role = user.role() == null ? "" : user.role().toLowerCase(Locale.ROOT);
        if (user.roleId() != 3 && !role.contains("employee") && !role.contains("staff") && !role.contains("nhan vien") && !role.contains("nhân viên"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chức năng này chỉ dành cho tài khoản nhân viên.");
        return user;
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
