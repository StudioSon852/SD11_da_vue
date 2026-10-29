package vn.shoegroup.sd11.admin;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.shoegroup.sd11.auth.JwtPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Employee shift planning, attendance and weekly payroll APIs for the FREEDOMS coursework app. */
@RestController
@RequestMapping("/api")
public class StaffManagementController {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime[] SHIFT_START = {
        LocalTime.of(6,40), LocalTime.of(8,45), LocalTime.of(10,50), LocalTime.of(13,0),
        LocalTime.of(15,5), LocalTime.of(17,10), LocalTime.of(19,15)
    };
    private static final LocalTime[] SHIFT_END = {
        LocalTime.of(8,40), LocalTime.of(10,45), LocalTime.of(12,50), LocalTime.of(15,0),
        LocalTime.of(17,5), LocalTime.of(19,10), LocalTime.of(21,15)
    };

    private final JdbcTemplate jdbc;
    public StaffManagementController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ---------------- Admin: staff directory, weekly schedule and salary ----------------
    @GetMapping("/admin/staff")
    public List<Map<String,Object>> staffDirectory() {
        requireAdmin();
        return jdbc.queryForList("""
            SELECT u.UserID employeeId, u.UserID id, u.FullName fullName, u.FullName name,
                   u.Email email, u.Phone phone, ISNULL(u.IsActive,1) active,
                   COALESCE(s.WeeklyBaseSalary,800000) weeklyBaseSalary,
                   COALESCE(s.ExpectedShiftsPerWeek,8) expectedShiftsPerWeek,
                   s.UpdatedAt salaryUpdatedAt
            FROM dbo.Users u
            LEFT JOIN dbo.Roles r ON r.RoleID=u.RoleID
            LEFT JOIN dbo.EmployeeSalarySettings s ON s.EmployeeUserID=u.UserID
            WHERE u.RoleID=3 OR LOWER(r.RoleName) LIKE '%employee%' OR LOWER(r.RoleName) LIKE '%staff%' OR LOWER(r.RoleName) LIKE '%nhan vien%' OR LOWER(r.RoleName) LIKE N'%nhân viên%'
            ORDER BY ISNULL(u.IsActive,1) DESC, u.FullName, u.UserID
            """);
    }

    @GetMapping("/admin/staff/schedule")
    public List<Map<String,Object>> adminSchedule(@RequestParam(required=false) String weekStart) {
        requireAdmin();
        LocalDate start = weekStart(weekStart);
        return scheduleRows(start, start.plusDays(6), null);
    }

    @PutMapping("/admin/staff/schedule")
    @Transactional
    public Map<String,Object> setShift(@RequestBody Map<String,Object> body) {
        JwtPrincipal actor = requireAdmin();
        LocalDate date = parseDate(body.get("workDate"), "Ngày làm việc không hợp lệ.");
        int shiftNo = intValue(body.get("shiftNo"), 0);
        if (shiftNo < 1 || shiftNo > 7) throw bad("Ca làm phải từ ca 1 đến ca 7.");
        Long employeeId = optionalLong(body.get("employeeUserId"));

        List<Map<String,Object>> existing = jdbc.queryForList("""
            SELECT a.AssignmentID assignmentId, a.EmployeeUserID employeeId,
                   CASE WHEN at.AttendanceID IS NULL THEN 0 ELSE 1 END hasAttendance
            FROM dbo.EmployeeShiftAssignments a
            LEFT JOIN dbo.EmployeeAttendance at ON at.AssignmentID=a.AssignmentID
            WHERE a.WorkDate=? AND a.ShiftNo=?
            """, Date.valueOf(date), shiftNo);
        if (employeeId == null) {
            if (!existing.isEmpty()) {
                if (num(existing.get(0).get("hasAttendance")) == 1)
                    throw bad("Ca đã được chấm công, không thể gỡ hoặc đổi nhân viên.");
                jdbc.update("DELETE FROM dbo.EmployeeShiftAssignments WHERE AssignmentID=?", num(existing.get(0).get("assignmentId")));
            }
            return Map.of("success", true, "workDate", date.toString(), "shiftNo", shiftNo, "employeeUserId", "");
        }

        Integer validEmployee = jdbc.queryForObject("""
            SELECT COUNT(1) FROM dbo.Users u LEFT JOIN dbo.Roles r ON r.RoleID=u.RoleID
            WHERE u.UserID=? AND ISNULL(u.IsActive,1)=1
              AND (u.RoleID=3 OR LOWER(r.RoleName) LIKE '%employee%' OR LOWER(r.RoleName) LIKE '%staff%' OR LOWER(r.RoleName) LIKE '%nhan vien%' OR LOWER(r.RoleName) LIKE N'%nhân viên%')
            """, Integer.class, employeeId);
        if (validEmployee == null || validEmployee == 0) throw bad("Chỉ có thể xếp ca cho tài khoản nhân viên đang hoạt động.");

        if (existing.isEmpty()) {
            jdbc.update("INSERT INTO dbo.EmployeeShiftAssignments(EmployeeUserID,WorkDate,ShiftNo,AssignedBy,CreatedAt) VALUES(?,?,?,?,SYSDATETIME())",
                employeeId, Date.valueOf(date), shiftNo, actor.id());
        } else {
            Map<String,Object> row = existing.get(0);
            if (num(row.get("hasAttendance")) == 1 && num(row.get("employeeId")) != employeeId)
                throw bad("Ca đã được chấm công, không thể đổi người phụ trách.");
            jdbc.update("UPDATE dbo.EmployeeShiftAssignments SET EmployeeUserID=?,AssignedBy=? WHERE AssignmentID=?",
                employeeId, actor.id(), num(row.get("assignmentId")));
        }
        return Map.of("success", true, "workDate", date.toString(), "shiftNo", shiftNo, "employeeUserId", employeeId);
    }

    @PutMapping("/admin/staff/{employeeId}/salary")
    @Transactional
    public Map<String,Object> updateSalary(@PathVariable long employeeId, @RequestBody Map<String,Object> body) {
        JwtPrincipal actor = requireAdmin();
        BigDecimal salary = decimal(body.get("weeklyBaseSalary"));
        int expected = intValue(body.get("expectedShiftsPerWeek"), 8);
        if (salary == null || salary.compareTo(BigDecimal.ZERO) < 0 || salary.compareTo(new BigDecimal("1000000000")) > 0)
            throw bad("Lương tuần phải nằm trong khoảng 0 đến 1.000.000.000 đồng.");
        if (expected < 1 || expected > 49) throw bad("Số ca chuẩn mỗi tuần phải từ 1 đến 49.");
        Integer employeeExists = jdbc.queryForObject("""
            SELECT COUNT(1) FROM dbo.Users u LEFT JOIN dbo.Roles r ON r.RoleID=u.RoleID
            WHERE u.UserID=? AND (u.RoleID=3 OR LOWER(r.RoleName) LIKE '%employee%' OR LOWER(r.RoleName) LIKE '%staff%' OR LOWER(r.RoleName) LIKE '%nhan vien%' OR LOWER(r.RoleName) LIKE N'%nhân viên%')
            """, Integer.class, employeeId);
        if (employeeExists == null || employeeExists == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản nhân viên.");
        Integer exists = jdbc.queryForObject("SELECT COUNT(1) FROM dbo.EmployeeSalarySettings WHERE EmployeeUserID=?", Integer.class, employeeId);
        if (exists != null && exists > 0) {
            jdbc.update("UPDATE dbo.EmployeeSalarySettings SET WeeklyBaseSalary=?,ExpectedShiftsPerWeek=?,UpdatedBy=?,UpdatedAt=SYSDATETIME() WHERE EmployeeUserID=?",
                salary, expected, actor.id(), employeeId);
        } else {
            jdbc.update("INSERT INTO dbo.EmployeeSalarySettings(EmployeeUserID,WeeklyBaseSalary,ExpectedShiftsPerWeek,UpdatedBy,UpdatedAt) VALUES(?,?,?,?,SYSDATETIME())",
                employeeId, salary, expected, actor.id());
        }
        return Map.of("success", true, "employeeUserId", employeeId, "weeklyBaseSalary", salary, "expectedShiftsPerWeek", expected);
    }

    @GetMapping("/admin/staff/payroll")
    public List<Map<String,Object>> adminPayroll(@RequestParam(required=false) String weekStart) {
        requireAdmin();
        LocalDate start = weekStart(weekStart), end = start.plusDays(6);
        List<Map<String,Object>> staff = staffDirectory();
        List<Map<String,Object>> result = new ArrayList<>();
        for (Map<String,Object> person : staff) {
            long id = num(person.get("employeeId"));
            result.add(payrollSummary(id, text(person.get("fullName")), start, end, person.get("weeklyBaseSalary"), person.get("expectedShiftsPerWeek")));
        }
        return result;
    }

    // ---------------- Employee self service ----------------
    @GetMapping("/employee/profile")
    public Map<String,Object> employeeProfile() {
        JwtPrincipal user = requireEmployee();
        return jdbc.queryForMap("""
            SELECT UserID id,FullName fullName,Email email,Phone phone,Address address,ISNULL(IsActive,1) active
            FROM dbo.Users WHERE UserID=?
            """, user.id());
    }

    @PutMapping("/employee/profile")
    public Map<String,Object> updateEmployeeProfile(@RequestBody Map<String,Object> body) {
        JwtPrincipal user = requireEmployee();
        String name = string(body.get("fullName")).trim();
        String phone = string(body.get("phone")).trim();
        String address = string(body.get("address")).trim();
        if (name.length() < 2 || name.length() > 100) throw bad("Họ tên phải có từ 2 đến 100 ký tự.");
        if (!phone.isBlank() && !phone.matches("0[0-9]{9}")) throw bad("Số điện thoại phải có 10 chữ số và bắt đầu bằng 0.");
        jdbc.update("UPDATE dbo.Users SET FullName=?,Phone=?,Address=?,UpdatedAt=GETDATE() WHERE UserID=?", name,
            phone.isBlank()?null:phone, address.isBlank()?null:address, user.id());
        return employeeProfile();
    }

    @GetMapping("/employee/shifts")
    public List<Map<String,Object>> ownShifts(@RequestParam(required=false) String weekStart) {
        JwtPrincipal user = requireEmployee();
        LocalDate start = weekStart(weekStart);
        return scheduleRows(start, start.plusDays(6), user.id());
    }

    @PostMapping("/employee/shifts/{assignmentId}/attendance")
    @Transactional
    public Map<String,Object> checkIn(@PathVariable long assignmentId) {
        JwtPrincipal user = requireEmployee();
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT AssignmentID assignmentId,EmployeeUserID employeeId,WorkDate workDate,ShiftNo shiftNo
            FROM dbo.EmployeeShiftAssignments WHERE AssignmentID=? AND EmployeeUserID=?
            """, assignmentId, user.id());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ca làm không tồn tại hoặc không thuộc tài khoản của bạn.");
        Map<String,Object> shift = rows.get(0);
        LocalDate date = asLocalDate(shift.get("workDate"));
        int shiftNo = (int) num(shift.get("shiftNo"));
        if (date == null || shiftNo < 1 || shiftNo > 7) throw bad("Dữ liệu ca làm không hợp lệ.");
        LocalDateTime now = LocalDateTime.now(VN);
        if (!now.toLocalDate().equals(date)) throw bad("Chỉ được chấm công đúng ngày được xếp ca.");
        if (now.toLocalTime().isBefore(SHIFT_START[shiftNo-1]) || !now.toLocalTime().isBefore(SHIFT_END[shiftNo-1]))
            throw bad("Đã ngoài thời gian chấm công của ca này. Hãy chấm công trong khung giờ của ca.");
        Integer checked = jdbc.queryForObject("SELECT COUNT(1) FROM dbo.EmployeeAttendance WHERE AssignmentID=?", Integer.class, assignmentId);
        if (checked != null && checked > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ca này đã được chấm công, không thể chấm lần thứ hai.");
        jdbc.update("INSERT INTO dbo.EmployeeAttendance(AssignmentID,EmployeeUserID,WorkDate,ShiftNo,IsPresent,CheckedInAt) VALUES(?,?,?,?,1,SYSDATETIME())",
            assignmentId, user.id(), Date.valueOf(date), shiftNo);
        return Map.of("success", true, "message", "Đã ghi nhận có mặt.", "assignmentId", assignmentId, "checkedInAt", now.toString());
    }

    @GetMapping("/employee/payroll")
    public Map<String,Object> ownPayroll(@RequestParam(required=false) String weekStart) {
        JwtPrincipal user = requireEmployee();
        LocalDate start = weekStart(weekStart);
        return payrollSummary(user.id(), user.fullName(), start, start.plusDays(6), null, null);
    }

    // ---------------- Private helpers ----------------
    private List<Map<String,Object>> scheduleRows(LocalDate start, LocalDate end, Long employeeId) {
        String sql = """
            SELECT a.AssignmentID assignmentId,a.WorkDate workDate,a.ShiftNo shiftNo,
                   a.EmployeeUserID employeeId,u.FullName employeeName,
                   CAST(CASE WHEN at.IsPresent=1 THEN 1 ELSE 0 END AS bit) isPresent,
                   at.CheckedInAt checkedInAt
            FROM dbo.EmployeeShiftAssignments a
            JOIN dbo.Users u ON u.UserID=a.EmployeeUserID
            LEFT JOIN dbo.EmployeeAttendance at ON at.AssignmentID=a.AssignmentID
            WHERE a.WorkDate BETWEEN ? AND ?
            """ + (employeeId == null ? "" : " AND a.EmployeeUserID=? ") + " ORDER BY a.WorkDate,a.ShiftNo";
        if (employeeId == null) return jdbc.queryForList(sql, Date.valueOf(start), Date.valueOf(end));
        return jdbc.queryForList(sql, Date.valueOf(start), Date.valueOf(end), employeeId);
    }

    private Map<String,Object> payrollSummary(long employeeId, String name, LocalDate start, LocalDate end, Object suppliedSalary, Object suppliedExpected) {
        BigDecimal weeklySalary = decimal(suppliedSalary);
        Integer expected = suppliedExpected == null ? null : intValue(suppliedExpected, 8);
        if (weeklySalary == null) {
            List<Map<String,Object>> settings = jdbc.queryForList("SELECT WeeklyBaseSalary,ExpectedShiftsPerWeek FROM dbo.EmployeeSalarySettings WHERE EmployeeUserID=?", employeeId);
            if (!settings.isEmpty()) {
                weeklySalary = decimal(settings.get(0).get("WeeklyBaseSalary"));
                expected = intValue(settings.get(0).get("ExpectedShiftsPerWeek"), 8);
            }
        }
        if (weeklySalary == null) weeklySalary = new BigDecimal("800000");
        if (expected == null || expected < 1) expected = 8;
        List<Map<String,Object>> shifts = jdbc.queryForList("""
            SELECT a.WorkDate workDate,a.ShiftNo shiftNo,CAST(CASE WHEN at.IsPresent=1 THEN 1 ELSE 0 END AS bit) isPresent,
                   at.CheckedInAt checkedInAt
            FROM dbo.EmployeeShiftAssignments a LEFT JOIN dbo.EmployeeAttendance at ON at.AssignmentID=a.AssignmentID
            WHERE a.EmployeeUserID=? AND a.WorkDate BETWEEN ? AND ? ORDER BY a.WorkDate,a.ShiftNo
            """, employeeId, Date.valueOf(start), Date.valueOf(end));
        int assigned=0, attended=0, missed=0, pending=0;
        LocalDateTime now = LocalDateTime.now(VN);
        for (Map<String,Object> shift : shifts) {
            assigned++;
            boolean present = Boolean.TRUE.equals(shift.get("isPresent")) || "true".equalsIgnoreCase(String.valueOf(shift.get("isPresent"))) || num(shift.get("isPresent")) == 1;
            if (present) { attended++; continue; }
            LocalDate date = asLocalDate(shift.get("workDate"));
            int n = (int)num(shift.get("shiftNo"));
            if (date != null && n >= 1 && n <= 7 && !now.isBefore(LocalDateTime.of(date, SHIFT_END[n-1]))) missed++;
            else pending++;
        }
        BigDecimal perShift = weeklySalary.divide(BigDecimal.valueOf(expected), 0, RoundingMode.DOWN);
        BigDecimal deduction = perShift.multiply(BigDecimal.valueOf(Math.min(missed, expected)));
        BigDecimal payable = weeklySalary.subtract(deduction).max(BigDecimal.ZERO);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("employeeUserId", employeeId); out.put("employeeName", name); out.put("weekStart", start.toString()); out.put("weekEnd", end.toString());
        out.put("weeklyBaseSalary", weeklySalary); out.put("expectedShiftsPerWeek", expected); out.put("perShiftDeduction", perShift);
        out.put("assignedShifts", assigned); out.put("attendedShifts", attended); out.put("missedShifts", missed); out.put("pendingShifts", pending);
        out.put("deduction", deduction); out.put("projectedPay", payable);
        out.put("isFinalized", LocalDate.now(VN).isAfter(end));
        return out;
    }

    private static JwtPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtPrincipal user)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        return user;
    }
    private static JwtPrincipal requireAdmin() {
        JwtPrincipal user = currentUser();
        String role = user.role() == null ? "" : user.role().toLowerCase(Locale.ROOT);
        if (user.roleId() != 1 && !role.contains("admin")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Admin được quản lý lịch ca và mức lương nhân viên.");
        return user;
    }
    private static JwtPrincipal requireEmployee() {
        JwtPrincipal user = currentUser();
        String role = user.role() == null ? "" : user.role().toLowerCase(Locale.ROOT);
        if (user.roleId() != 3 && !role.contains("employee") && !role.contains("staff") && !role.contains("nhan vien") && !role.contains("nhân viên"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chức năng này chỉ dành cho tài khoản nhân viên.");
        return user;
    }
    private static LocalDate weekStart(String value) {
        LocalDate date;
        try { date = value == null || value.isBlank() ? LocalDate.now(VN) : LocalDate.parse(value); }
        catch (DateTimeParseException ex) { throw bad("Ngày bắt đầu tuần không hợp lệ (yyyy-MM-dd)."); }
        return date.with(DayOfWeek.MONDAY);
    }
    private static LocalDate parseDate(Object value, String message) {
        try { return LocalDate.parse(string(value)); } catch (Exception ex) { throw bad(message); }
    }
    private static LocalDate asLocalDate(Object value) {
        if (value instanceof Date date) return date.toLocalDate();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (value instanceof LocalDate d) return d;
        try { return value == null ? null : LocalDate.parse(String.valueOf(value).substring(0,10)); } catch (Exception ex) { return null; }
    }
    private static BigDecimal decimal(Object value) { try { return value == null || String.valueOf(value).isBlank() ? null : new BigDecimal(String.valueOf(value)); } catch (Exception ex) { return null; } }
    private static long num(Object value) { try { return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value)); } catch (Exception ex) { return 0; } }
    private static int intValue(Object value, int fallback) { try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ex) { return fallback; } }
    private static Long optionalLong(Object value) { if (value == null || String.valueOf(value).isBlank() || "null".equalsIgnoreCase(String.valueOf(value))) return null; try { return Long.valueOf(String.valueOf(value)); } catch (Exception ex) { throw bad("Mã nhân viên không hợp lệ."); } }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
