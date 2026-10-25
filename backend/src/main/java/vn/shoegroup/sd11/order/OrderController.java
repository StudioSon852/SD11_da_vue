package vn.shoegroup.sd11.order;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.shoegroup.sd11.auth.JwtPrincipal;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final JdbcTemplate jdbc;
    public OrderController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping
    @Transactional
    public Map<String,Object> create(@RequestBody Map<String,Object> body,
                                    @RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey) {
        JwtPrincipal user = currentUser();
        boolean posOrder = body.containsKey("products") && !body.containsKey("items");
        List<Map<String,Object>> items = readItems(body.containsKey("items") ? body.get("items") : body.get("products"));
        if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giỏ hàng đang trống.");
        if (items.size() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng có quá nhiều dòng sản phẩm.");

        String customerName = firstText(body, "customerName", "fullName", "customer_name");
        String phone = firstText(body, "phone", "customerPhone", "customer_phone");
        String address = firstText(body, "shippingAddress", "address", "fullAddress");
        Map<String,Object> customer = object(body.get("customer"));
        if (customerName.isBlank()) customerName = firstText(customer, "fullName", "full_name", "name");
        if (phone.isBlank()) phone = firstText(customer, "phone", "customerPhone");
        if (address.isBlank()) address = firstText(customer, "address", "shippingAddress");
        if (customerName.isBlank() && posOrder) customerName = "Khách lẻ";
        if (!posOrder && (customerName.isBlank() || phone.isBlank() || address.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tên, số điện thoại và địa chỉ nhận hàng.");

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map<String,Object> item : items) {
            long productId = positiveLong(first(item,"product_id","id_product","productId"), "Mã sản phẩm");
            int qty = positiveInt(first(item,"quantity","qty"), "Số lượng");
            if (qty > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng mỗi sản phẩm không được vượt quá 1000.");
            Map<String,Object> product = jdbc.query("SELECT ProductID, ProductName, BasePrice, SalePrice, ImageURL FROM dbo.Products WHERE ProductID=? AND ISNULL(IsActive,1)=1", rs -> {
                if (!rs.next()) return null;
                Map<String,Object> x = new LinkedHashMap<>(); x.put("id", rs.getLong("ProductID")); x.put("name",rs.getString("ProductName")); x.put("base",rs.getBigDecimal("BasePrice")); x.put("sale",rs.getBigDecimal("SalePrice")); x.put("image",rs.getString("ImageURL")); return x;
            }, productId);
            if (product == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Một sản phẩm trong giỏ không còn kinh doanh.");
            Object variantRaw = first(item,"variant_id","variantId","ProductVariantID","product_variant_id");
            BigDecimal price = product.get("base") instanceof BigDecimal b ? b : BigDecimal.ZERO;
            BigDecimal sale = product.get("sale") instanceof BigDecimal b ? b : BigDecimal.ZERO;
            if (sale.signum() > 0 && sale.compareTo(price) < 0) price = sale;
            Long variantId = null;
            if (variantRaw != null && !String.valueOf(variantRaw).isBlank()) {
                variantId = positiveLong(variantRaw,"Mã biến thể");
                List<BigDecimal> variantPrice = jdbc.query("SELECT TOP 1 CAST(p.BasePrice + ISNULL(v.PriceAdjustment,0) AS decimal(18,2)) FROM dbo.ProductVariants v JOIN dbo.Products p ON p.ProductID=v.ProductID WHERE v.ProductVariantID=? AND v.ProductID=? AND ISNULL(v.IsActive,1)=1", (rs, rowNum) -> rs.getBigDecimal(1), variantId, productId);
                if (variantPrice.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size hoặc màu đã chọn không còn hợp lệ.");
                if (variantPrice.get(0) != null) price = variantPrice.get(0);
                item.put("_variantId", variantId);
            }
            item.put("_productId", productId); item.put("_quantity", qty); item.put("_price", price);
            if (String.valueOf(item.getOrDefault("name", "")).isBlank()) item.put("name", product.get("name"));
            item.put("_image", product.get("image"));
            subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(qty)));
        }

        Map<String,Object> shippingMethod = object(body.get("shippingMethod"));
        String shipCode = firstText(shippingMethod,"code","MethodCode");
        Map<String,Object> shipDb = jdbc.query("SELECT TOP 1 ShippingMethodID id, MethodName name, MethodCode code, ISNULL(BasePrice,30000) fee FROM dbo.ShippingMethods WHERE (?='' OR MethodCode=?) AND ISNULL(IsActive,1)=1 ORDER BY ShippingMethodID", rs -> {
            if (!rs.next()) return null; Map<String,Object> r=new LinkedHashMap<>(); r.put("id",rs.getInt("id"));r.put("name",rs.getString("name"));r.put("fee",rs.getBigDecimal("fee"));return r;
        }, shipCode, shipCode);
        BigDecimal shippingFee = shipDb != null && shipDb.get("fee") instanceof BigDecimal b ? b : new BigDecimal("30000");
        BigDecimal discount = decimal(first(body,"discount","discountAmount","discount_amount"));
        if (discount.signum() < 0) discount = BigDecimal.ZERO;
        if (discount.compareTo(subtotal.add(shippingFee)) > 0) discount = subtotal.add(shippingFee);
        BigDecimal total = subtotal.add(shippingFee).subtract(discount).max(BigDecimal.ZERO);
        Map<String,Object> payment = object(body.get("paymentMethod"));
        String paymentCode = firstText(payment,"code","MethodCode");
        String paymentName = firstText(payment,"name","MethodName");
        if (paymentName.isBlank()) paymentName = firstText(body,"payment_method","paymentMethodName");
        if (paymentName.isBlank()) paymentName = paymentCode.equalsIgnoreCase("BANK") ? "Chuyển khoản ngân hàng" : "Thanh toán khi nhận hàng";
        String requestedPaymentStatus = firstText(body,"payment_status","paymentStatus");
        String paymentStatus = posOrder && isPaid(requestedPaymentStatus) ? "Đã thanh toán" : (paymentCode.equalsIgnoreCase("BANK") ? "Chờ thanh toán" : "Chưa thanh toán");
        String requestedStatus = firstText(body,"status","orderStatus");
        String status = posOrder && !requestedStatus.isBlank() ? requestedStatus : "Đang xử lý";
        String note = firstText(body,"note","orderNote","order_note");
        if (note.isBlank()) note = firstText(customer,"note","orderNote");
        Integer shipId = shipDb == null ? null : ((Number)shipDb.get("id")).intValue();
        final String finalCustomerName = customerName;
        final String finalPhone = phone;
        final String finalAddress = address;
        final String finalPaymentName = paymentName;
        final String finalPaymentStatus = paymentStatus;
        final String finalStatus = status;
        final String finalNote = note;
        final Integer finalShipId = shipId;
        Long orderId = jdbc.query("""
            INSERT INTO dbo.Orders (UserID,TotalAmount,OrderDate,Status,ShippingAddress,CustomerName,CustomerPhone,PaymentMethod,ShippingFee,DiscountAmount,OrderNote,ShippingMethodID,PaymentStatus,IsCountedAsRevenue,UpdatedAt)
            OUTPUT INSERTED.OrderID VALUES (?,?,GETDATE(),?,?,?,?,?,?,?,?,?,?,0,GETDATE())
            """, rs -> rs.next()?rs.getLong(1):null, user.id(), total, finalStatus, finalAddress, finalCustomerName, finalPhone, finalPaymentName, shippingFee, discount, finalNote, finalShipId, finalPaymentStatus);
        for (Map<String,Object> item : items) {
            jdbc.update("""
                INSERT INTO dbo.OrderDetails (OrderID,ProductID,Quantity,UnitPrice,Size,Color,ProductVariantID,ProductNameSnapshot,SKUSnapshot,ColorHex,ImageURLSnapshot)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, orderId, item.get("_productId"), item.get("_quantity"), item.get("_price"), first(item,"size","size_name"), first(item,"color","color_name"), item.get("_variantId"), String.valueOf(item.getOrDefault("name","Sản phẩm")), null, first(item,"color_hex","hex"), item.get("_image"));
        }
        jdbc.update("INSERT INTO dbo.OrderStatusHistory (OrderID,OldStatus,NewStatus,Note,ChangedBy,ChangedAt) VALUES (?,NULL,?,?,?,GETDATE())", orderId, status, "Tạo đơn hàng qua SD_11", user.id());
        if (isPaid(paymentStatus)) {
            jdbc.update("UPDATE dbo.Orders SET PaymentConfirmedAt=COALESCE(PaymentConfirmedAt,GETDATE()), RevenueEligibleDate=COALESCE(RevenueEligibleDate,GETDATE()), IsCountedAsRevenue=1 WHERE OrderID=?", orderId);
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("success",true); result.put("orderId",orderId); result.put("OrderID",orderId); result.put("totalAmount",total); result.put("subtotalAmount",subtotal); result.put("shippingFee",shippingFee); result.put("discountAmount",discount); result.put("payment_status",paymentStatus); result.put("status",status); result.put("message","Đặt hàng thành công.");
        return result;
    }

    @GetMapping
    public List<Map<String,Object>> list() {
        JwtPrincipal u=currentUser();
        List<Map<String,Object>> orders = (u.roleId()==1 || u.roleId()==3 || u.roleId()==4)
            ? jdbc.queryForList("SELECT o.OrderID id,o.OrderID order_id,o.UserID user_id,o.TotalAmount total,o.TotalAmount total_amount,o.OrderDate order_date,o.Status status,o.PaymentStatus payment_status,o.CustomerName customer_name,o.CustomerPhone customer_phone,o.ShippingAddress shipping_address,o.PaymentMethod payment_method,o.TrackingNumber tracking_number FROM dbo.Orders o ORDER BY o.OrderDate DESC")
            : jdbc.queryForList("SELECT o.OrderID id,o.OrderID order_id,o.UserID user_id,o.TotalAmount total,o.TotalAmount total_amount,o.OrderDate order_date,o.Status status,o.PaymentStatus payment_status,o.CustomerName customer_name,o.CustomerPhone customer_phone,o.ShippingAddress shipping_address,o.PaymentMethod payment_method,o.TrackingNumber tracking_number FROM dbo.Orders o WHERE o.UserID=? ORDER BY o.OrderDate DESC", u.id());
        for (Map<String,Object> order : orders) {
            long orderId = number(order.get("id"));
            order.put("products", jdbc.queryForList("SELECT od.OrderDetailID id,od.OrderDetailID order_detail_id,od.ProductID product_id,od.ProductVariantID variant_id,od.ProductNameSnapshot name,od.Quantity quantity,od.UnitPrice price,od.Size size,od.Color color,od.ImageURLSnapshot image FROM dbo.OrderDetails od WHERE od.OrderID=?", orderId));
        }
        return orders;
    }

    @GetMapping("/{id}")
    public Map<String,Object> detail(@PathVariable long id) {
        JwtPrincipal u=currentUser();
        List<Map<String,Object>> rows = (u.roleId()==1 || u.roleId()==3 || u.roleId()==4)
            ? jdbc.queryForList("SELECT OrderID id,OrderID order_id,UserID user_id,TotalAmount total,Status status,PaymentStatus payment_status,OrderDate order_date,CustomerName customer_name,CustomerPhone customer_phone,ShippingAddress shipping_address FROM dbo.Orders WHERE OrderID=?",id)
            : jdbc.queryForList("SELECT OrderID id,OrderID order_id,UserID user_id,TotalAmount total,Status status,PaymentStatus payment_status,OrderDate order_date,CustomerName customer_name,CustomerPhone customer_phone,ShippingAddress shipping_address FROM dbo.Orders WHERE OrderID=? AND UserID=?",id,u.id());
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng.");
        Map<String,Object> result=new LinkedHashMap<>(rows.get(0));
        result.put("items",jdbc.queryForList("SELECT OrderDetailID id,ProductID product_id,Quantity quantity,UnitPrice price,Size size,Color color,ProductVariantID variant_id,ProductNameSnapshot name,ImageURLSnapshot image FROM dbo.OrderDetails WHERE OrderID=?",id));
        return result;
    }


    @PutMapping("/{id}/address")
    @Transactional
    public Map<String,Object> updateShippingAddress(@PathVariable long id, @RequestBody Map<String,Object> body) {
        JwtPrincipal user = currentUser();
        Object addressValue = first(body, "addressId", "AddressID", "address_id");
        long addressId = positiveLong(addressValue, "Mã địa chỉ");

        List<Map<String,Object>> orders = jdbc.queryForList(
            "SELECT OrderID,UserID,Status,ShippingAddress,AddressID,AddressChangedAt FROM dbo.Orders WHERE OrderID=? AND UserID=?",
            id, user.id());
        if (orders.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
        Map<String,Object> order = orders.get(0);
        Object changedAt = order.get("AddressChangedAt");
        if (changedAt != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng này đã được đổi địa chỉ một lần.");

        String status = String.valueOf(order.getOrDefault("Status", ""));
        String normalizedStatus = java.text.Normalizer.normalize(status, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT)
            .replaceAll("[\\s_-]+", "");
        if (normalizedStatus.contains("giaohang") || normalizedStatus.contains("vanchuyen") ||
            normalizedStatus.contains("delivered") || normalizedStatus.contains("received") ||
            normalizedStatus.contains("cancel") || normalizedStatus.contains("huy") ||
            normalizedStatus.contains("hoantat")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể đổi địa chỉ khi đơn hàng đã giao vận hoặc kết thúc.");
        }

        List<Map<String,Object>> addresses = jdbc.queryForList("""
            SELECT AddressID,RecipientName,Phone,Province,District,Ward,AddressLine,FullAddress
            FROM dbo.UserAddresses WHERE AddressID=? AND UserID=?
            """, addressId, user.id());
        if (addresses.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ không tồn tại hoặc không thuộc tài khoản của bạn.");
        Map<String,Object> address = addresses.get(0);
        long currentAddressId = number(order.get("AddressID"));
        String fullAddress = textValue(address.get("FullAddress"));
        if (fullAddress.isBlank()) {
            fullAddress = String.join(", ", List.of("AddressLine", "Ward", "District", "Province").stream()
                .map(key -> textValue(address.get(key))).filter(part -> !part.isBlank()).toList());
        }
        if (currentAddressId == addressId || Objects.equals(textValue(order.get("ShippingAddress")), fullAddress)) {
            Map<String,Object> unchanged = new LinkedHashMap<>();
            unchanged.put("success", true); unchanged.put("unchanged", true);
            unchanged.put("shippingAddress", textValue(order.get("ShippingAddress")));
            return unchanged;
        }
        int updated = jdbc.update("""
            UPDATE dbo.Orders SET AddressID=?,ShippingAddress=?,CustomerName=COALESCE(?,CustomerName),
                CustomerPhone=COALESCE(?,CustomerPhone),AddressChangedAt=GETDATE(),UpdatedAt=GETDATE()
            WHERE OrderID=? AND UserID=? AND AddressChangedAt IS NULL
            """, addressId, fullAddress, address.get("RecipientName"), address.get("Phone"), id, user.id());
        if (updated == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể đổi địa chỉ lần nữa cho đơn hàng này.");
        return Map.of("success", true, "unchanged", false, "shippingAddress", fullAddress, "addressId", addressId);
    }

    private static String textValue(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    @PutMapping("/{id}/status")
    @Transactional
    public Map<String,Object> updateStatus(@PathVariable long id, @RequestBody Map<String,Object> body) {
        JwtPrincipal user = currentUser();
        if (user.roleId() != 1 && user.roleId() != 3 && user.roleId() != 4) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền cập nhật đơn hàng.");
        String requested = firstText(body, "status", "Status");
        if (requested.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu trạng thái đơn hàng.");
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT Status,PaymentStatus FROM dbo.Orders WHERE OrderID=?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
        String oldStatus = String.valueOf(rows.get(0).getOrDefault("Status", ""));
        String paymentStatus = String.valueOf(rows.get(0).getOrDefault("PaymentStatus", ""));
        boolean delivered = requested.equalsIgnoreCase("Đã giao hàng thành công") || requested.equalsIgnoreCase("Đã nhận hàng") || requested.equalsIgnoreCase("Hoàn tất");
        jdbc.update("UPDATE dbo.Orders SET Status=?, UpdatedAt=GETDATE(), DeliveredDate=CASE WHEN ?=1 THEN COALESCE(DeliveredDate,GETDATE()) ELSE DeliveredDate END WHERE OrderID=?", requested, delivered ? 1 : 0, id);
        jdbc.update("INSERT INTO dbo.OrderStatusHistory (OrderID,OldStatus,NewStatus,Note,ChangedBy,ChangedAt) VALUES (?,?,?,?,?,GETDATE())", id, oldStatus, requested, firstText(body,"note","Note"), user.id());
        if (delivered && !isPaid(paymentStatus) && !requested.equalsIgnoreCase("Đã hủy")) {
            jdbc.update("UPDATE dbo.Orders SET PaymentStatus=N'Đã thanh toán',PaymentConfirmedAt=COALESCE(PaymentConfirmedAt,GETDATE()),RevenueEligibleDate=COALESCE(RevenueEligibleDate,GETDATE()),IsCountedAsRevenue=1,UpdatedAt=GETDATE() WHERE OrderID=?", id);
            paymentStatus = "Đã thanh toán";
        }
        return Map.of("success",true,"id",id,"status",requested,"payment_status",paymentStatus);
    }

    @PutMapping("/{id}/payment")
    @Transactional
    public Map<String,Object> updatePayment(@PathVariable long id,@RequestBody Map<String,Object> body) {
        JwtPrincipal u=currentUser();
        String requested=firstText(body,"payment_status","paymentStatus","status");
        boolean paid=isPaid(requested);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT OrderID,UserID,PaymentStatus,Status FROM dbo.Orders WHERE OrderID=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng.");
        Map<String,Object> order=rows.get(0);
        if(number(order.get("UserID"))!=u.id() && u.roleId()!=1 && u.roleId()!=3 && u.roleId()!=4) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền cập nhật thanh toán đơn này.");
        String current=String.valueOf(order.getOrDefault("PaymentStatus",""));
        if (paid && u.roleId() != 1 && u.roleId() != 3 && u.roleId() != 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Thanh toán chỉ được xác nhận bởi nhân viên hoặc hệ thống thanh toán.");
        }
        if(paid) {
            jdbc.update("UPDATE dbo.Orders SET PaymentStatus=N'Đã thanh toán',PaymentConfirmedAt=COALESCE(PaymentConfirmedAt,GETDATE()),RevenueEligibleDate=COALESCE(RevenueEligibleDate,GETDATE()),IsCountedAsRevenue=1,UpdatedAt=GETDATE() WHERE OrderID=?",id);
        } else {
            jdbc.update("UPDATE dbo.Orders SET PaymentStatus=?,UpdatedAt=GETDATE() WHERE OrderID=?",requested.isBlank()?"Chưa thanh toán":requested,id);
        }
        return Map.of("success",true,"orderId",id,"payment_status",paid?"Đã thanh toán":(requested.isBlank()?"Chưa thanh toán":requested),"alreadyPaid",isPaid(current));
    }

    private static boolean isPaid(String s) {
        String raw = s == null ? "" : s.trim();
        if (raw.equalsIgnoreCase("Đã thanh toán") || raw.equalsIgnoreCase("Thanh toán thành công") || raw.equalsIgnoreCase("Hoàn tất thanh toán")) return true;
        String n = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT)
            .replaceAll("[\\s_-]+", "");
        return Set.of("paid","success","succeeded","completed","dathanhtoan","dathanhtoanthanhcong","thanhtoanthanhcong","thanhcong").contains(n);
    }
    private static JwtPrincipal currentUser(){Authentication a= SecurityContextHolder.getContext().getAuthentication(); if(a==null||!(a.getPrincipal() instanceof JwtPrincipal u)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập.");return u;}
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object o){return o instanceof Map<?,?> m?(Map<String,Object>)m:new LinkedHashMap<>();}
    private static List<Map<String,Object>> readItems(Object o){List<Map<String,Object>> out=new ArrayList<>(); if(o instanceof List<?> list) for(Object x:list){if(x instanceof Map<?,?> m){Map<String,Object> r=new LinkedHashMap<>();m.forEach((k,v)->r.put(String.valueOf(k),v));out.add(r);}}return out;}
    private static Object first(Map<String,Object> m,String... keys){for(String k:keys)if(m.containsKey(k)&&m.get(k)!=null)return m.get(k);return null;}
    private static String firstText(Map<String,Object> m,String... keys){Object o=first(m,keys);return o==null?"":String.valueOf(o).trim();}
    private static long positiveLong(Object o,String label){try{long n=Long.parseLong(String.valueOf(o));if(n>0)return n;}catch(Exception ignored){}throw new ResponseStatusException(HttpStatus.BAD_REQUEST,label+" không hợp lệ.");}
    private static int positiveInt(Object o,String label){long n=positiveLong(o,label);if(n>Integer.MAX_VALUE)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,label+" không hợp lệ.");return (int)n;}
    private static long number(Object o){try{return Long.parseLong(String.valueOf(o));}catch(Exception e){return -1;}}
    private static BigDecimal decimal(Object o){try{return new BigDecimal(String.valueOf(o));}catch(Exception e){return BigDecimal.ZERO;}}
}
