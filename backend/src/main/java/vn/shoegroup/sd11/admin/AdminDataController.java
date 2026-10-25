package vn.shoegroup.sd11.admin;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.shoegroup.sd11.auth.JwtPrincipal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Admin/CRM endpoints retained from the ShoeGroup UI. Stock adjustments are intentionally absent. */
@RestController
@RequestMapping("/api")
public class AdminDataController {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AdminDataController(JdbcTemplate jdbc, PasswordEncoder passwords) { this.jdbc = jdbc; this.passwords = passwords; }

    @GetMapping("/accounts")
    public List<Map<String,Object>> accounts() { requireStaff(); return jdbc.queryForList("""
        SELECT u.UserID id,u.UserID id_user,u.Email email,u.Email username,u.FullName name,u.FullName full_name,
               u.Phone phone,u.Address address,u.RoleID role_id,ISNULL(u.IsActive,1) active,
               r.RoleName role, u.CreatedAt created_at
        FROM dbo.Users u LEFT JOIN dbo.Roles r ON r.RoleID=u.RoleID ORDER BY u.UserID DESC
        """); }

    @PostMapping("/accounts")
    @Transactional
    public Map<String,Object> createAccount(@RequestBody Map<String,Object> b) {
        requireAdmin();
        String email = str(b, "username", "email").toLowerCase(Locale.ROOT);
        String name = str(b, "name", "full_name");
        String password = b.get("password") == null ? "" : String.valueOf(b.get("password"));
        int roleId = integer(b, "role_id", "RoleID", 2);
        boolean active = truthy(first(b, "active", "IsActive"));
        if (email.isBlank() || !EMAIL.matcher(email).matches() || email.length() > 100)
            throw bad("Email tài khoản không hợp lệ.");
        if (name.length() < 2 || name.length() > 100)
            throw bad("Họ tên phải có từ 2 đến 100 ký tự.");
        if (password.length() < 8 || password.length() > 128)
            throw bad("Mật khẩu tài khoản phải có từ 8 đến 128 ký tự.");
        if (roleId < 1 || roleId > 4 || jdbc.queryForObject("SELECT COUNT(1) FROM dbo.Roles WHERE RoleID=?", Integer.class, roleId) == 0)
            throw bad("Vai trò được chọn không tồn tại trong dbo.Roles.");
        if (jdbc.queryForObject("SELECT COUNT(1) FROM dbo.Users WHERE LOWER(LTRIM(RTRIM(Email)))=?", Integer.class, email) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email này đã được sử dụng.");
        String encoded = passwords.encode(password);
        Long id = jdbc.queryForObject("""
            INSERT INTO dbo.Users (RoleID,Email,PasswordHash,FullName,IsActive,CreatedAt,UpdatedAt,EmailVerified,PointBalance)
            OUTPUT INSERTED.UserID VALUES (?,?,?,?,?,GETDATE(),GETDATE(),0,0)
            """, Long.class, roleId, email, encoded, name, active);
        return Map.of("success", true, "id", id, "UserID", id, "email", email, "username", email,
            "name", name, "full_name", name, "role_id", roleId, "active", active);
    }

    @GetMapping("/customers")
    public List<Map<String,Object>> customers() { requireStaff(); return jdbc.queryForList("""
        SELECT u.UserID id,u.UserID customer_id,u.FullName name,u.FullName full_name,u.Email email,
               u.Phone phone,u.Address address,u.CreatedAt created_at, ISNULL(u.Source,'Web') source,
               COUNT(o.OrderID) order_count,COALESCE(SUM(CASE WHEN ISNULL(o.IsCountedAsRevenue,0)=1 THEN o.TotalAmount ELSE 0 END),0) spent
        FROM dbo.Users u LEFT JOIN dbo.Orders o ON o.UserID=u.UserID
        WHERE u.RoleID=2 OR u.Source=N'POS'
        GROUP BY u.UserID,u.FullName,u.Email,u.Phone,u.Address,u.CreatedAt,u.Source
        ORDER BY u.UserID DESC
        """); }

    @GetMapping("/customers/{id}/orders")
    public List<Map<String,Object>> customerOrders(@PathVariable long id) {
        requireStaff();
        return jdbc.queryForList("SELECT OrderID id,OrderID order_id,TotalAmount total,OrderDate order_date,Status status,PaymentStatus payment_status FROM dbo.Orders WHERE UserID=? ORDER BY OrderDate DESC", id);
    }

    @PostMapping("/customers")
    @Transactional
    public Map<String,Object> createWalkInCustomer(@RequestBody Map<String,Object> b) {
        requireStaff();
        String name = str(b,"name","full_name"); if (name.isBlank()) name = "Khách lẻ";
        String phone = str(b,"phone","phone");
        if (!phone.isBlank()) {
            List<Map<String,Object>> found = jdbc.queryForList("SELECT TOP 1 UserID id,FullName name,Phone phone FROM dbo.Users WHERE Phone=? AND (RoleID=2 OR Source=N'POS')", phone);
            if (!found.isEmpty()) return Map.of("success",true,"existed",true,"id",found.get(0).get("id"),"name",found.get(0).get("name"),"phone",phone);
        }
        String email = "walkin-" + UUID.randomUUID().toString().replace("-","").substring(0,20) + "@walkin.local";
        String secret = passwords.encode(UUID.randomUUID().toString());
        Long id = jdbc.queryForObject("""
          INSERT INTO dbo.Users (RoleID,Email,PasswordHash,FullName,Phone,Address,IsActive,CreatedAt,UpdatedAt,Source,EmailVerified,PointBalance)
          OUTPUT INSERTED.UserID VALUES (2,?,?,?,?,?,1,GETDATE(),GETDATE(),N'POS',1,0)
          """, Long.class, email, secret, name, phone.isBlank()?null:phone, null);
        return Map.of("success",true,"id",id,"name",name,"full_name",name,"phone",phone,"source","POS");
    }

    @GetMapping("/discounts")
    public List<Map<String,Object>> discounts() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        boolean staff = principal instanceof JwtPrincipal user && (user.roleId() == 1 || user.roleId() == 3 || user.roleId() == 4);
        String sql = staff ? """
            SELECT CouponID id,CouponID CouponID,CouponCode code,CouponCode coupon_code,CouponName name,
                   DiscountType discount_type,DiscountValue value,DiscountPercent AS [percent],MinOrderAmount min_order,
                   MaxDiscountAmount max_discount,UsageLimit quantity,ISNULL(UsedCount,0) used,StartDate start_date,
                   ExpiryDate expiry,ISNULL(IsActive,0) active,Description description,IsFlashSale is_flash_sale
            FROM dbo.Coupons ORDER BY CouponID DESC
            """ : """
            SELECT CouponID id,CouponID CouponID,CouponCode code,CouponCode coupon_code,CouponName name,
                   DiscountType discount_type,DiscountValue value,DiscountPercent AS [percent],MinOrderAmount min_order,
                   MaxDiscountAmount max_discount,UsageLimit quantity,ISNULL(UsedCount,0) used,StartDate start_date,
                   ExpiryDate expiry,ISNULL(IsActive,0) active,Description description,IsFlashSale is_flash_sale
            FROM dbo.Coupons
            WHERE ISNULL(IsActive,0)=1 AND (StartDate IS NULL OR StartDate<=GETDATE()) AND ExpiryDate>=GETDATE()
              AND (UsageLimit<=0 OR ISNULL(UsedCount,0)<UsageLimit)
            ORDER BY CouponID DESC
            """;
        return jdbc.queryForList(sql);
    }

    @PostMapping("/discounts") @Transactional
    public Map<String,Object> createDiscount(@RequestBody Map<String,Object> b) {
        requireStaff();
        String code = str(b,"CouponCode","code").toUpperCase(Locale.ROOT);
        String name = str(b,"CouponName","name");
        if (code.isBlank() || name.isBlank()) throw bad("Mã và tên giảm giá là bắt buộc.");
        try {
            Long id = jdbc.queryForObject("""
              INSERT INTO dbo.Coupons (CouponCode,CouponName,DiscountType,DiscountValue,DiscountPercent,MinOrderAmount,MaxDiscountAmount,UsageLimit,UsedCount,StartDate,ExpiryDate,IsActive,Description,IsFlashSale)
              OUTPUT INSERTED.CouponID VALUES (?,?,?,?,?,?,?,?,0,?, ?,?,?,0)
              """, Long.class, code,name,strDefault(b,"DiscountType","discount_type","Phần trăm"),decimal(b,"DiscountValue","value",BigDecimal.ZERO),integer(b,"DiscountPercent","percent",0),decimal(b,"MinOrderAmount","min_order",BigDecimal.ZERO),decimal(b,"MaxDiscountAmount","max_discount",BigDecimal.ZERO),Math.max(0,integer(b,"UsageLimit","quantity",0)),dateOrNull(b.get("StartDate")),expiry(b.get("ExpiryDate")),truthy(first(b,"IsActive","active")),str(b,"Description","description"));
            return Map.of("success",true,"id",id);
        } catch (org.springframework.dao.DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã giảm giá đã tồn tại."); }
    }

    @PutMapping("/discounts/{id}")
    public Map<String,Object> updateDiscount(@PathVariable long id,@RequestBody Map<String,Object> b) {
        requireStaff();
        int n=jdbc.update("""
          UPDATE dbo.Coupons SET CouponCode=COALESCE(?,CouponCode),CouponName=COALESCE(?,CouponName),DiscountType=COALESCE(?,DiscountType),
          DiscountValue=COALESCE(?,DiscountValue),DiscountPercent=COALESCE(?,DiscountPercent),MinOrderAmount=COALESCE(?,MinOrderAmount),
          MaxDiscountAmount=COALESCE(?,MaxDiscountAmount),UsageLimit=COALESCE(?,UsageLimit),StartDate=COALESCE(?,StartDate),
          ExpiryDate=COALESCE(?,ExpiryDate),IsActive=COALESCE(?,IsActive),Description=COALESCE(?,Description) WHERE CouponID=?
          """, nullable(b,"CouponCode","code"),nullable(b,"CouponName","name"),nullable(b,"DiscountType","discount_type"),nullableDecimal(b,"DiscountValue","value"),nullableInt(b,"DiscountPercent","percent"),nullableDecimal(b,"MinOrderAmount","min_order"),nullableDecimal(b,"MaxDiscountAmount","max_discount"),nullableInt(b,"UsageLimit","quantity"),dateOrNull(first(b,"StartDate","start_date")),dateOrNull(first(b,"ExpiryDate","expiry")),nullableBool(b,"IsActive","active"),nullable(b,"Description","description"),id);
        if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy mã giảm giá."); return Map.of("success",true,"id",id);
    }

    @DeleteMapping("/discounts/{id}") public Map<String,Object> deleteDiscount(@PathVariable long id) { requireStaff(); int n=jdbc.update("UPDATE dbo.Coupons SET IsActive=0 WHERE CouponID=?",id); if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy mã giảm giá."); return Map.of("success",true); }

    @GetMapping("/variantDiscounts")
    public List<Map<String,Object>> variantDiscounts() { requireStaff(); return jdbc.queryForList("""
      SELECT vd.VariantDiscountID id,vd.ProductVariantID variant_id,vd.ProductID product_id,vd.ColorName color,vd.ColorHex color_hex,
             vd.DiscountType discount_type,vd.DiscountValue value,vd.DiscountPercent AS [percent],vd.MaxDiscountAmount max_discount,
             vd.Quantity quantity,vd.UsedCount used,vd.StartDate start_date,vd.EndDate end_date,vd.Reason reason,
             vd.IsActive active,vd.Description description,p.ProductName product_name,v.Size size
      FROM dbo.VariantDiscounts vd LEFT JOIN dbo.Products p ON p.ProductID=vd.ProductID
      LEFT JOIN dbo.ProductVariants v ON v.ProductVariantID=vd.ProductVariantID ORDER BY vd.VariantDiscountID DESC
      """); }

    @PostMapping("/variantDiscounts") @Transactional
    public Map<String,Object> createVariantDiscount(@RequestBody Map<String,Object> b) {
        requireStaff(); long variant=longValue(first(b,"ProductVariantID","variant_id"));
        long product=longValue(first(b,"ProductID","product_id")); if(variant<=0||product<=0) throw bad("Phải chọn biến thể sản phẩm.");
        Long id=jdbc.queryForObject("""
          INSERT INTO dbo.VariantDiscounts (ProductVariantID,ProductID,ColorName,ColorHex,DiscountType,DiscountValue,DiscountPercent,MaxDiscountAmount,Quantity,UsedCount,StartDate,EndDate,Reason,IsActive,Description,CreatedAt)
          OUTPUT INSERTED.VariantDiscountID VALUES (?,?,?,?,?,?,?,?,?,0,?,?,?,?,?,GETDATE())
          """,Long.class,variant,product,nullable(b,"ColorName","color"),nullable(b,"ColorHex","color_hex"),strDefault(b,"DiscountType","discount_type","Theo phần trăm"),decimal(b,"DiscountValue","value",BigDecimal.ZERO),integer(b,"DiscountPercent","percent",0),decimal(b,"MaxDiscountAmount","max_discount",BigDecimal.ZERO),Math.max(0,integer(b,"Quantity","quantity",0)),dateOrNull(first(b,"StartDate","start_date")),dateOrNull(first(b,"EndDate","end_date")),str(b,"Reason","reason"),truthy(first(b,"IsActive","active")),str(b,"Description","description"));
        return Map.of("success",true,"id",id);
    }

    @PutMapping("/variantDiscounts/{id}")
    public Map<String,Object> updateVariantDiscount(@PathVariable long id,@RequestBody Map<String,Object> b) {
        requireStaff(); int n=jdbc.update("""
          UPDATE dbo.VariantDiscounts SET ProductVariantID=COALESCE(?,ProductVariantID),ProductID=COALESCE(?,ProductID),ColorName=COALESCE(?,ColorName),ColorHex=COALESCE(?,ColorHex),
          DiscountType=COALESCE(?,DiscountType),DiscountValue=COALESCE(?,DiscountValue),DiscountPercent=COALESCE(?,DiscountPercent),MaxDiscountAmount=COALESCE(?,MaxDiscountAmount),
          Quantity=COALESCE(?,Quantity),StartDate=?,EndDate=?,Reason=COALESCE(?,Reason),IsActive=COALESCE(?,IsActive),Description=COALESCE(?,Description) WHERE VariantDiscountID=?
          """,nullableLong(b,"ProductVariantID","variant_id"),nullableLong(b,"ProductID","product_id"),nullable(b,"ColorName","color"),nullable(b,"ColorHex","color_hex"),nullable(b,"DiscountType","discount_type"),nullableDecimal(b,"DiscountValue","value"),nullableInt(b,"DiscountPercent","percent"),nullableDecimal(b,"MaxDiscountAmount","max_discount"),nullableInt(b,"Quantity","quantity"),dateOrNull(first(b,"StartDate","start_date")),dateOrNull(first(b,"EndDate","end_date")),nullable(b,"Reason","reason"),nullableBool(b,"IsActive","active"),nullable(b,"Description","description"),id);
        if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy giảm giá biến thể."); return Map.of("success",true,"id",id);
    }

    @DeleteMapping("/variantDiscounts/{id}") public Map<String,Object> deleteVariantDiscount(@PathVariable long id) { requireStaff(); int n=jdbc.update("UPDATE dbo.VariantDiscounts SET IsActive=0 WHERE VariantDiscountID=?",id); if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy giảm giá biến thể."); return Map.of("success",true); }

    @GetMapping("/returns")
    public List<Map<String,Object>> returns() {
        requireStaff(); List<Map<String,Object>> rows=jdbc.queryForList("""
          SELECT r.ReturnID id,r.ReturnID ReturnID,r.OrderID order_id,r.OrderID OrderID,r.ReturnType return_type,r.ReturnType ReturnType,
          r.TrackingNumber tracking_number,r.Reason reason,r.Status status,r.Status Status,r.RefundAmount refund_amount,r.RefundAmount RefundAmount,
          r.CreatedAt created_at,r.InspectionNote inspection_note,r.ResolutionNote resolution_note,r.RestockedAt restocked_at,r.RefundedAt refunded_at,
          r.WalletCreditedAt wallet_credited_at,o.PaymentMethod payment_method,o.PaymentStatus payment_status
          FROM dbo.Returns r LEFT JOIN dbo.Orders o ON o.OrderID=r.OrderID ORDER BY r.CreatedAt DESC,r.ReturnID DESC
          """);
        for(Map<String,Object> r:rows) r.put("details",jdbc.queryForList("SELECT rd.ReturnDetailID id,rd.OrderDetailID order_detail_id,rd.Quantity quantity,rd.Reason reason,rd.[Condition] condition,od.ProductNameSnapshot name,od.UnitPrice price,od.Size size,od.Color color FROM dbo.ReturnDetails rd LEFT JOIN dbo.OrderDetails od ON od.OrderDetailID=rd.OrderDetailID WHERE rd.ReturnID=?",r.get("id")));
        return rows;
    }

    @PostMapping("/returns") @Transactional
    public Map<String,Object> createReturn(@RequestBody Map<String,Object> b) {
        JwtPrincipal actor = currentUser();
        long orderId=longValue(first(b,"order_id","OrderID")); if(orderId<=0) throw bad("Mã đơn hàng không hợp lệ.");
        List<Long> owners = jdbc.queryForList("SELECT TOP 1 UserID FROM dbo.Orders WHERE OrderID=?", Long.class, orderId);
        if (owners.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng.");
        boolean staff = actor.roleId()==1 || actor.roleId()==3 || actor.roleId()==4;
        Long ownerId = owners.get(0);
        if (!staff && (ownerId == null || ownerId.longValue() != actor.id())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn chỉ có thể yêu cầu đổi trả cho đơn hàng của mình.");
        List<Map<String,Object>> items=maps(b.get("items"));
        BigDecimal refund=decimal(first(b,"refund_amount","RefundAmount"),null);
        if(refund==null && !items.isEmpty()) { refund=BigDecimal.ZERO; for(Map<String,Object> it:items) refund=refund.add(decimal(first(it,"price","UnitPrice"),BigDecimal.ZERO).multiply(BigDecimal.valueOf(Math.max(0,integer(it,"quantity","Quantity",0))))); }
        if(refund==null) refund=jdbc.queryForObject("SELECT ISNULL(TotalAmount,0) FROM dbo.Orders WHERE OrderID=?",BigDecimal.class,orderId);
        String status=strDefault(b,"status","Status","Chờ xử lý");
        Long id=jdbc.queryForObject("""
          INSERT INTO dbo.Returns (OrderID,ReturnType,TrackingNumber,PostOfficeID,ReturnAddress,Reason,Status,RefundAmount,CreatedAt,UpdatedAt,InspectionNote,ResolutionNote)
          OUTPUT INSERTED.ReturnID VALUES (?,?,?,?,?,?,?,?,GETDATE(),GETDATE(),?,?)
          """,Long.class,orderId,strDefault(b,"return_type","ReturnType","CUSTOMER"),nullable(b,"tracking_number","TrackingNumber"),nullableInt(b,"post_office_id","PostOfficeID"),nullable(b,"return_address","ReturnAddress"),str(b,"reason","Reason"),status,refund,nullable(b,"inspection_note","InspectionNote"),nullable(b,"resolution_note","ResolutionNote"));
        if(items.isEmpty()) {
            // Chưa nhận được kiện hàng: lưu lịch sử yêu cầu nhưng không tự thêm lại hàng hay thay đổi biến thể.
        } else for(Map<String,Object> it:items) {
            long orderDetail=longValue(first(it,"order_detail_id","OrderDetailID")); int qty=integer(it,"quantity","Quantity",0);
            if(orderDetail<=0||qty<=0) continue;
            Integer belongs=jdbc.queryForObject("SELECT COUNT(1) FROM dbo.OrderDetails WHERE OrderDetailID=? AND OrderID=?",Integer.class,orderDetail,orderId);
            if(belongs==null||belongs==0) throw bad("Chi tiết trả hàng không thuộc đơn đã chọn.");
            jdbc.update("INSERT INTO dbo.ReturnDetails(ReturnID,OrderDetailID,Quantity,Reason,[Condition]) VALUES(?,?,?,?,?)",id,orderDetail,qty,str(b,"reason","Reason"),nullable(it,"condition","Condition"));
        }
        if(status.equalsIgnoreCase("Hoàn tất")||status.equalsIgnoreCase("Đã hoàn tất")) jdbc.update("UPDATE dbo.Returns SET RefundedAt=COALESCE(RefundedAt,GETDATE()) WHERE ReturnID=?",id);
        return Map.of("success",true,"id",id,"ReturnID",id,"status",status,"refund_amount",refund);
    }

    @PutMapping("/returns/{id}/status")
    @Transactional
    public Map<String,Object> updateReturnStatus(@PathVariable long id,@RequestBody Map<String,Object> b) {
        JwtPrincipal actor = requireStaff();
        String status=str(b,"status","Status"); if(status.isBlank()) throw bad("Thiếu trạng thái đổi trả.");
        List<Map<String,Object>> existing = jdbc.queryForList("""
            SELECT r.ReturnID, r.OrderID, r.RefundAmount, r.WalletCreditedAt, o.UserID
            FROM dbo.Returns r WITH (UPDLOCK, ROWLOCK)
            LEFT JOIN dbo.Orders o ON o.OrderID=r.OrderID WHERE r.ReturnID=?
            """, id);
        if(existing.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu đổi trả.");
        int n=jdbc.update("UPDATE dbo.Returns SET Status=?,InspectionNote=COALESCE(?,InspectionNote),ResolutionNote=COALESCE(?,ResolutionNote),UpdatedAt=GETDATE(),ApprovedAt=CASE WHEN ? IN (N'Chấp nhận hoàn tiền',N'Đã hoàn tất',N'Hoàn tất') THEN COALESCE(ApprovedAt,GETDATE()) ELSE ApprovedAt END,RefundedAt=CASE WHEN ? IN (N'Đã hoàn tiền',N'Đã hoàn tất',N'Hoàn tất') THEN COALESCE(RefundedAt,GETDATE()) ELSE RefundedAt END,UpdatedBy=? WHERE ReturnID=?",status,nullable(b,"inspection_note","inspectionNote"),nullable(b,"resolution_note","resolutionNote"),status,status,actor.id(),id);
        if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu đổi trả.");

        String normalized = java.text.Normalizer.normalize(status, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
        boolean refunded = Set.of("dahoantien", "dahoantat", "hoantat", "completed", "refunded").contains(normalized);
        Map<String,Object> row = existing.get(0);
        Object userIdRaw = row.get("UserID");
        Object creditedAt = row.get("WalletCreditedAt");
        BigDecimal refund = decimal(row.get("RefundAmount"), BigDecimal.ZERO);
        if (refunded && userIdRaw != null && creditedAt == null && refund.signum() > 0) {
            long userId = longValue(userIdRaw);
            List<Long> walletIds = jdbc.queryForList("SELECT TOP 1 WalletID FROM dbo.ShoeGroupWallets WITH (UPDLOCK, ROWLOCK) WHERE UserID=? ORDER BY WalletID", Long.class, userId);
            long walletId;
            if (walletIds.isEmpty()) {
                jdbc.update("INSERT INTO dbo.ShoeGroupWallets(UserID,Balance,CreatedAt,UpdatedAt) VALUES(?,0,GETDATE(),GETDATE())", userId);
                walletId = jdbc.queryForObject("SELECT TOP 1 WalletID FROM dbo.ShoeGroupWallets WHERE UserID=? ORDER BY WalletID DESC", Long.class, userId);
            } else walletId = walletIds.get(0);
            BigDecimal balance = jdbc.queryForObject("SELECT Balance FROM dbo.ShoeGroupWallets WITH (UPDLOCK, ROWLOCK) WHERE WalletID=?", BigDecimal.class, walletId);
            if (balance == null) balance = BigDecimal.ZERO;
            BigDecimal after = balance.add(refund);
            jdbc.update("UPDATE dbo.ShoeGroupWallets SET Balance=?,UpdatedAt=GETDATE() WHERE WalletID=?", after, walletId);
            jdbc.update("INSERT INTO dbo.ShoeGroupWalletTransactions(UserID,ReturnID,TransactionType,Amount,BalanceAfter,Description,CreatedAt) VALUES(?,?, 'REFUND',?,?,?,GETDATE())", userId, id, refund, after, "Hoàn tiền từ yêu cầu đổi trả #" + id);
            jdbc.update("UPDATE dbo.Returns SET WalletCreditedAt=GETDATE(),RefundedAt=COALESCE(RefundedAt,GETDATE()) WHERE ReturnID=? AND WalletCreditedAt IS NULL", id);
        }
        return Map.of("success",true,"id",id,"status",status);
    }


    @PostMapping("/categories") public Map<String,Object> createCategory(@RequestBody Map<String,Object>b){requireStaff(); String name=str(b,"name","CategoryName"); if(name.isBlank()) throw bad("Thiếu tên danh mục."); Long id=jdbc.queryForObject("INSERT INTO dbo.Categories(CategoryName,IsActive,Sport) OUTPUT INSERTED.CategoryID VALUES(?,1,?)",Long.class,name,nullable(b,"sport","Sport"));return Map.of("success",true,"id",id);}
    @PutMapping("/categories/{id}") public Map<String,Object> updateCategory(@PathVariable long id,@RequestBody Map<String,Object>b){requireStaff();int n=jdbc.update("UPDATE dbo.Categories SET CategoryName=COALESCE(?,CategoryName),Sport=COALESCE(?,Sport),IsActive=COALESCE(?,IsActive) WHERE CategoryID=?",nullable(b,"name","CategoryName"),nullable(b,"sport","Sport"),nullableBool(b,"active","IsActive"),id);return result(n,id);}
    @DeleteMapping("/categories/{id}") public Map<String,Object> deleteCategory(@PathVariable long id){requireStaff();return result(jdbc.update("UPDATE dbo.Categories SET IsActive=0 WHERE CategoryID=?",id),id);}

    @PostMapping("/brands") public Map<String,Object> createBrand(@RequestBody Map<String,Object>b){requireStaff();String name=str(b,"name","BrandName");if(name.isBlank())throw bad("Thiếu tên thương hiệu.");Long id=jdbc.queryForObject("INSERT INTO dbo.Brands(BrandName,IsActive,LogoURL,SortOrder) OUTPUT INSERTED.BrandID VALUES(?,1,?,?)",Long.class,name,nullable(b,"logo_url","LogoURL"),integer(b,"sort_order","SortOrder",0));return Map.of("success",true,"id",id);}
    @PutMapping("/brands/{id}") public Map<String,Object> updateBrand(@PathVariable long id,@RequestBody Map<String,Object>b){requireStaff();return result(jdbc.update("UPDATE dbo.Brands SET BrandName=COALESCE(?,BrandName),LogoURL=COALESCE(?,LogoURL),SortOrder=COALESCE(?,SortOrder),IsActive=COALESCE(?,IsActive) WHERE BrandID=?",nullable(b,"name","BrandName"),nullable(b,"logo_url","LogoURL"),nullableInt(b,"sort_order","SortOrder"),nullableBool(b,"active","IsActive"),id),id);}
    @DeleteMapping("/brands/{id}") public Map<String,Object> deleteBrand(@PathVariable long id){requireStaff();return result(jdbc.update("UPDATE dbo.Brands SET IsActive=0 WHERE BrandID=?",id),id);}

    @PostMapping("/collections") public Map<String,Object> createCollection(@RequestBody Map<String,Object>b){requireStaff();String name=str(b,"name","CollectionName");if(name.isBlank())throw bad("Thiếu tên bộ sưu tập.");Long id=jdbc.queryForObject("INSERT INTO dbo.Collections(CollectionName,Slug,BrandID,IsActive) OUTPUT INSERTED.CollectionID VALUES(?,?,?,1)",Long.class,name,strDefault(b,"slug","Slug",slug(name)),nullableInt(b,"brand_id","BrandID"));return Map.of("success",true,"id",id);}
    @PutMapping("/collections/{id}") public Map<String,Object> updateCollection(@PathVariable long id,@RequestBody Map<String,Object>b){requireStaff();return result(jdbc.update("UPDATE dbo.Collections SET CollectionName=COALESCE(?,CollectionName),Slug=COALESCE(?,Slug),BrandID=COALESCE(?,BrandID),IsActive=COALESCE(?,IsActive) WHERE CollectionID=?",nullable(b,"name","CollectionName"),nullable(b,"slug","Slug"),nullableInt(b,"brand_id","BrandID"),nullableBool(b,"active","IsActive"),id),id);}
    @DeleteMapping("/collections/{id}") public Map<String,Object> deleteCollection(@PathVariable long id){requireStaff();return result(jdbc.update("UPDATE dbo.Collections SET IsActive=0 WHERE CollectionID=?",id),id);}

    @PostMapping("/materials") public Map<String,Object> createMaterial(@RequestBody Map<String,Object>b){requireStaff();String name=str(b,"name","MaterialName");Long id=jdbc.queryForObject("INSERT INTO dbo.Materials(MaterialName,IsActive) OUTPUT INSERTED.MaterialID VALUES(?,1)",Long.class,name);return Map.of("success",true,"id",id);}
    @PutMapping("/materials/{id}") public Map<String,Object> updateMaterial(@PathVariable long id,@RequestBody Map<String,Object>b){requireStaff();return result(jdbc.update("UPDATE dbo.Materials SET MaterialName=COALESCE(?,MaterialName),IsActive=COALESCE(?,IsActive) WHERE MaterialID=?",nullable(b,"name","MaterialName"),nullableBool(b,"active","IsActive"),id),id);}
    @DeleteMapping("/materials/{id}") public Map<String,Object> deleteMaterial(@PathVariable long id){requireStaff();return result(jdbc.update("UPDATE dbo.Materials SET IsActive=0 WHERE MaterialID=?",id),id);}

    @PostMapping("/colors") public Map<String,Object> createColor(@RequestBody Map<String,Object>b){requireStaff();String name=str(b,"name","ColorName");Long id=jdbc.queryForObject("INSERT INTO dbo.Colors(ColorName,ColorHex,IsActive,SortOrder) OUTPUT INSERTED.ColorID VALUES(?,?,1,?)",Long.class,name,strDefault(b,"hex","ColorHex","#000000"),integer(b,"sort_order","SortOrder",0));return Map.of("success",true,"id",id);}
    @PutMapping("/colors/{id}") public Map<String,Object> updateColor(@PathVariable long id,@RequestBody Map<String,Object>b){requireStaff();return result(jdbc.update("UPDATE dbo.Colors SET ColorName=COALESCE(?,ColorName),ColorHex=COALESCE(?,ColorHex),SortOrder=COALESCE(?,SortOrder),IsActive=COALESCE(?,IsActive) WHERE ColorID=?",nullable(b,"name","ColorName"),nullable(b,"hex","ColorHex"),nullableInt(b,"sort_order","SortOrder"),nullableBool(b,"active","IsActive"),id),id);}
    @DeleteMapping("/colors/{id}") public Map<String,Object> deleteColor(@PathVariable long id){requireStaff();return result(jdbc.update("UPDATE dbo.Colors SET IsActive=0 WHERE ColorID=?",id),id);}

    @PostMapping("/sizes") public Map<String,Object> createSize(@RequestBody Map<String,Object>b){requireStaff();String name=str(b,"name","SizeName");Long id=jdbc.queryForObject("INSERT INTO dbo.Sizes(SizeName,SizeStandard,IsActive,SortOrder) OUTPUT INSERTED.SizeID VALUES(?,?,1,?)",Long.class,name,strDefault(b,"standard","SizeStandard","EU"),integer(b,"sort_order","SortOrder",0));return Map.of("success",true,"id",id);}
    @PutMapping("/sizes/{id}") public Map<String,Object> updateSize(@PathVariable long id,@RequestBody Map<String,Object>b){requireStaff();return result(jdbc.update("UPDATE dbo.Sizes SET SizeName=COALESCE(?,SizeName),SizeStandard=COALESCE(?,SizeStandard),SortOrder=COALESCE(?,SortOrder),IsActive=COALESCE(?,IsActive) WHERE SizeID=?",nullable(b,"name","SizeName"),nullable(b,"standard","SizeStandard"),nullableInt(b,"sort_order","SortOrder"),nullableBool(b,"active","IsActive"),id),id);}
    @DeleteMapping("/sizes/{id}") public Map<String,Object> deleteSize(@PathVariable long id){requireStaff();return result(jdbc.update("UPDATE dbo.Sizes SET IsActive=0 WHERE SizeID=?",id),id);}

    private static JwtPrincipal currentUser(){Authentication a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!(a.getPrincipal() instanceof JwtPrincipal u))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập.");return u;}
    private static JwtPrincipal requireAdmin(){JwtPrincipal u=currentUser();if(u.roleId()!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ quản trị viên mới được tạo hoặc phân quyền tài khoản.");return u;}
    private static JwtPrincipal requireStaff(){JwtPrincipal u=currentUser();if(u.roleId()!=1&&u.roleId()!=3&&u.roleId()!=4)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ quản trị viên hoặc nhân viên được phép thực hiện thao tác này.");return u;}
    private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private static Map<String,Object> result(int n,long id){if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bản ghi.");return Map.of("success",true,"id",id);}
    private static Object first(Map<String,Object>b,String a,String c){return b.containsKey(a)?b.get(a):b.get(c);}
    private static String str(Map<String,Object>b,String a,String c){Object x=first(b,a,c);return x==null?"":String.valueOf(x).trim();}
    private static String strDefault(Map<String,Object>b,String a,String c,String d){String s=str(b,a,c);return s.isBlank()?d:s;}
    private static Object nullable(Map<String,Object>b,String a,String c){Object x=first(b,a,c);if(x==null)return null;String s=String.valueOf(x).trim();return s.isBlank()?null:s;}
    private static int integer(Map<String,Object>b,String a,String c,int fallback){try{return Integer.parseInt(String.valueOf(first(b,a,c)));}catch(Exception e){return fallback;}}
    private static Long nullableLong(Map<String,Object>b,String a,String c){try{Object v=first(b,a,c);return v==null||String.valueOf(v).isBlank()?null:Long.valueOf(String.valueOf(v));}catch(Exception e){return null;}}
    private static long longValue(Object x){try{return Long.parseLong(String.valueOf(x));}catch(Exception e){return 0;}}
    private static Integer nullableInt(Map<String,Object>b,String a,String c){Object v=first(b,a,c);if(v==null||String.valueOf(v).isBlank())return null;try{return Integer.valueOf(String.valueOf(v));}catch(Exception e){return null;}}
    private static BigDecimal decimal(Object x,BigDecimal fallback){try{return x==null?fallback:new BigDecimal(String.valueOf(x));}catch(Exception e){return fallback;}}
    private static BigDecimal decimal(Map<String,Object>b,String a,String c,BigDecimal fallback){return decimal(first(b,a,c),fallback);}
    private static BigDecimal nullableDecimal(Map<String,Object>b,String a,String c){Object v=first(b,a,c);return v==null||String.valueOf(v).isBlank()?null:decimal(v,null);}
    private static Boolean nullableBool(Map<String,Object>b,String a,String c){Object v=first(b,a,c);return v==null?null:truthy(v);}
    private static boolean truthy(Object v){if(v==null)return false;if(v instanceof Boolean b)return b;if(v instanceof Number n)return n.intValue()!=0;String s=String.valueOf(v).trim();return !(s.isEmpty()||s.equals("0")||s.equalsIgnoreCase("false")||s.equalsIgnoreCase("off"));}
    private static LocalDate dateOrNull(Object x){if(x==null||String.valueOf(x).isBlank())return null;try{return LocalDate.parse(String.valueOf(x).substring(0,10));}catch(Exception e){return null;}}
    private static java.sql.Date expiry(Object x){LocalDate d=dateOrNull(x);return java.sql.Date.valueOf(d==null?LocalDate.of(2099,12,31):d);}
    private static String slug(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s-]","").trim().replaceAll("[\\s-]+","-");}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> maps(Object x){if(!(x instanceof List<?> l))return List.of();List<Map<String,Object>> out=new ArrayList<>();for(Object v:l)if(v instanceof Map<?,?> m){Map<String,Object> z=new LinkedHashMap<>();m.forEach((k,val)->z.put(String.valueOf(k),val));out.add(z);}return out;}
}
