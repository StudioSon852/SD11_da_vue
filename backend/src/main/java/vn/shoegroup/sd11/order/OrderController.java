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
import java.math.RoundingMode;
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
        Long orderUserId = resolveOrderCustomerId(body, posOrder, user);
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
        Map<String,Object> shipDb = jdbc.query("SELECT TOP 1 ShippingMethodID id, MethodName name, MethodCode code, ISNULL(BasePrice,15700) fee FROM dbo.ShippingMethods WHERE (?='' OR MethodCode=?) AND ISNULL(IsActive,1)=1 ORDER BY ShippingMethodID", rs -> {
            if (!rs.next()) return null; Map<String,Object> r=new LinkedHashMap<>(); r.put("id",rs.getInt("id"));r.put("name",rs.getString("name"));r.put("fee",rs.getBigDecimal("fee"));return r;
        }, shipCode, shipCode);
        Object rawDistance = first(shippingMethod,"distanceKm","distance_km");
        if (rawDistance == null) rawDistance = first(body,"distanceKm","deliveryDistanceKm","delivery_distance_km");
        BigDecimal distanceKm = decimal(rawDistance).max(BigDecimal.ZERO);
        BigDecimal recipientLatitude = optionalDecimal(first(shippingMethod,"recipientLatitude","latitude"));
        BigDecimal recipientLongitude = optionalDecimal(first(shippingMethod,"recipientLongitude","longitude"));
        if (recipientLatitude == null) recipientLatitude = optionalDecimal(first(body,"recipientLatitude","recipient_latitude"));
        if (recipientLongitude == null) recipientLongitude = optionalDecimal(first(body,"recipientLongitude","recipient_longitude"));
        boolean hasRecipientGps = validCoordinates(recipientLatitude, recipientLongitude);
        if (!posOrder && !hasRecipientGps) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu tọa độ GPS của địa chỉ nhận hàng. Hãy kiểm tra địa chỉ và tính lại phí giao hàng.");
        }
        if (hasRecipientGps) {
            double originLat = storeOriginCoordinate("STORE_ORIGIN_LATITUDE", 21.0150);
            double originLng = storeOriginCoordinate("STORE_ORIGIN_LONGITUDE", 105.8290);
            distanceKm = BigDecimal.valueOf(haversineKm(originLat, originLng, recipientLatitude.doubleValue(), recipientLongitude.doubleValue()));
        }
        // Đơn online luôn tính lại phí từ GPS; không nhận khoảng cách hoặc phí do browser tự khai.
        BigDecimal shippingFee = posOrder ? BigDecimal.ZERO : calculateShippingFee(distanceKm);
        // Với POS, coupon và ưu đãi thành viên được tính riêng. % hạng luôn được
        // xác định lại từ chi tiêu đã ghi nhận trong database, không tin % do browser gửi.
        BigDecimal couponDiscount = decimal(posOrder
            ? first(body,"coupon_discount_amount","couponDiscountAmount","discount","discountAmount","discount_amount")
            : first(body,"discount","discountAmount","discount_amount"));
        if (couponDiscount.signum() < 0) couponDiscount = BigDecimal.ZERO;
        BigDecimal discountBase = subtotal.add(shippingFee).subtract(couponDiscount).max(BigDecimal.ZERO);
        Map<String,Object> memberBenefit = posOrder ? getMemberBenefit(orderUserId) : Map.of("tierCode", "", "tierName", "", "discountPercent", 0, "spent", BigDecimal.ZERO, "minSpend", BigDecimal.ZERO);
        int memberDiscountPercent = posOrder ? (int) number(memberBenefit.get("discountPercent")) : 0;
        BigDecimal memberDiscount = posOrder
            ? discountBase.multiply(BigDecimal.valueOf(Math.max(0, memberDiscountPercent))).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal discount = couponDiscount.add(memberDiscount).min(subtotal.add(shippingFee));
        BigDecimal total = subtotal.add(shippingFee).subtract(discount).max(BigDecimal.ZERO);
        Map<String,Object> payment = object(body.get("paymentMethod"));
        String paymentCode = firstText(payment,"code","MethodCode");
        String paymentName = firstText(payment,"name","MethodName");
        if (paymentName.isBlank()) {
            Object rawPaymentName = body.get("paymentMethod");
            if (rawPaymentName instanceof String value) paymentName = value.trim();
            else paymentName = firstText(body,"payment_method","paymentMethodName");
        }
        if (paymentName.isBlank()) paymentName = paymentCode.equalsIgnoreCase("BANK") ? "Chuyển khoản ngân hàng" : "Thanh toán khi nhận hàng";
        boolean bankPayment = isBankMethod(paymentCode, paymentName);
        String requestedPaymentStatus = firstText(body,"payment_status","paymentStatus");
        BigDecimal cashReceived = decimal(first(body,"cash_received","cashReceived","amount_tendered","amountTendered","cash_given"));
        BigDecimal cashChange = BigDecimal.ZERO;
        boolean paidRequested = isPaid(requestedPaymentStatus);
        if (posOrder && !bankPayment) {
            if (cashReceived.compareTo(total) < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiền khách đưa chưa đủ tổng hóa đơn.");
            cashChange = cashReceived.subtract(total).max(BigDecimal.ZERO);
        }
        String paymentStatus = posOrder && paidRequested
            ? "Đã thanh toán"
            : (bankPayment ? "Chờ xác nhận thanh toán" : "Chờ thanh toán");
        String requestedStatus = firstText(body,"status","orderStatus");
        // Bank transfer: create -> payment form -> staff confirms -> order confirmed.
        // COD: create -> waiting confirmation -> processing -> delivery -> payment -> complete.
        String status = posOrder
            ? (requestedStatus.isBlank() ? (paidRequested ? "Đã hoàn thành" : "Thanh toán") : requestedStatus)
            : (bankPayment ? "Thanh toán" : "Chờ xác nhận");
        if (posOrder && !paidRequested && !bankPayment) status = "Thanh toán";
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
            """, rs -> rs.next()?rs.getLong(1):null, orderUserId, total, finalStatus, finalAddress, finalCustomerName, finalPhone, finalPaymentName, shippingFee, discount, finalNote, finalShipId, finalPaymentStatus);
        for (Map<String,Object> item : items) {
            jdbc.update("""
                INSERT INTO dbo.OrderDetails (OrderID,ProductID,Quantity,UnitPrice,Size,Color,ProductVariantID,ProductNameSnapshot,SKUSnapshot,ColorHex,ImageURLSnapshot)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, orderId, item.get("_productId"), item.get("_quantity"), item.get("_price"), first(item,"size","size_name"), first(item,"color","color_name"), item.get("_variantId"), String.valueOf(item.getOrDefault("name","Sản phẩm")), null, first(item,"color_hex","hex"), item.get("_image"));
        }
        String requestedReference = firstText(body,"payment_reference","paymentReference");
        String paymentReference = requestedReference.isBlank()
            ? "FDS-" + orderId + "-" + UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT)
            : requestedReference.substring(0, Math.min(65, requestedReference.length()));
        // Client reference is convenient for showing the QR before the order is saved,
        // but a duplicate must never be persisted. If it collides, issue a distinct
        // server reference and return it so checkout can redraw the QR before display.
        if (jdbc.queryForObject("SELECT COUNT(1) FROM dbo.Orders WHERE PaymentReference=?", Integer.class, paymentReference) > 0) {
            paymentReference = paymentReference + "-" + UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);
            paymentReference = paymentReference.substring(0, Math.min(80, paymentReference.length()));
        }
        BigDecimal latitude = recipientLatitude;
        BigDecimal longitude = recipientLongitude;
        final String channel = posOrder ? "OFFLINE" : "ONLINE";
        final BigDecimal savedCashReceived = posOrder && !bankPayment ? cashReceived : null;
        final BigDecimal savedCashChange = posOrder && !bankPayment ? cashChange : null;
        final String savedPaymentReference = paymentReference;
        jdbc.update("UPDATE dbo.Orders SET SalesChannel=?,CashReceived=?,CashChange=?,PaymentReference=?,CreatedByUserID=?,HandledBy=?,DeliveryDistanceKm=?,RecipientLatitude=?,RecipientLongitude=?,PaymentDueAt=CASE WHEN ? IN (N'Chờ xác nhận thanh toán',N'Chờ thanh toán',N'Chờ xử lý') THEN DATEADD(HOUR,24,GETDATE()) ELSE PaymentDueAt END,OrderConfirmedAt=CASE WHEN ?=N'Đã xác nhận' THEN GETDATE() ELSE OrderConfirmedAt END,CompletedAt=CASE WHEN ? IN (N'Đã hoàn thành',N'Đã nhận hàng',N'Đã giao hàng thành công') THEN GETDATE() ELSE CompletedAt END WHERE OrderID=?",
            channel, savedCashReceived, savedCashChange, savedPaymentReference, user.id(), user.fullName(),
            posOrder ? BigDecimal.ZERO : distanceKm, latitude, longitude,
            paymentStatus, status, status, orderId);
        if (posOrder && isPaid(paymentStatus) && Set.of("dahoanthanh","danhanhang","dagiaohangthanhcong","hoantat").contains(normalize(status))) {
            jdbc.update("INSERT INTO dbo.OrderStatusHistory (OrderID,OldStatus,NewStatus,Note,ChangedBy,ChangedAt) VALUES (?,NULL,N'Đang xử lý',N'Tạo đơn bán tại quầy',?,GETDATE())", orderId, user.id());
            jdbc.update("INSERT INTO dbo.OrderStatusHistory (OrderID,OldStatus,NewStatus,Note,ChangedBy,ChangedAt) VALUES (?,N'Đang xử lý',N'Thanh toán',N'Khách thanh toán tại quầy',?,GETDATE())", orderId, user.id());
            jdbc.update("INSERT INTO dbo.OrderStatusHistory (OrderID,OldStatus,NewStatus,Note,ChangedBy,ChangedAt) VALUES (?,N'Thanh toán',?,N'Hoàn tất bán hàng tại quầy',?,GETDATE())", orderId, status, user.id());
        } else {
            jdbc.update("INSERT INTO dbo.OrderStatusHistory (OrderID,OldStatus,NewStatus,Note,ChangedBy,ChangedAt) VALUES (?,NULL,?,?,?,GETDATE())", orderId, status, posOrder ? "Tạo đơn bán tại quầy" : "Khách tạo đơn trực tuyến", user.id());
        }
        jdbc.update("INSERT INTO dbo.OrderPaymentHistory (OrderID,OldPaymentStatus,NewPaymentStatus,Note,ChangedBy,ChangedAt) VALUES (?,NULL,?,?,?,SYSDATETIME())", orderId, paymentStatus, "Khởi tạo thanh toán", user.id());
        if (bankPayment) {
            jdbc.update("INSERT INTO dbo.PaymentTransactions (OrderID,Provider,ProviderTxnRef,Amount,Status,CreatedAt) VALUES (?,?,?,?,N'PENDING',GETDATE())", orderId, "VietQR", paymentReference, total);
        }
        if (isPaid(paymentStatus)) {
            jdbc.update("UPDATE dbo.Orders SET PaymentConfirmedAt=COALESCE(PaymentConfirmedAt,GETDATE()), RevenueEligibleDate=CASE WHEN Status IN (N'Đã hủy') THEN NULL ELSE COALESCE(RevenueEligibleDate,GETDATE()) END, IsCountedAsRevenue=CASE WHEN Status IN (N'Đã hoàn thành',N'Đã nhận hàng',N'Đã giao hàng thành công') THEN 1 ELSE 0 END WHERE OrderID=?", orderId);
            jdbc.update("UPDATE dbo.PaymentTransactions SET Status=N'SUCCESS',CompletedAt=COALESCE(CompletedAt,GETDATE()) WHERE OrderID=?",orderId);
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("success",true); result.put("orderId",orderId); result.put("OrderID",orderId); result.put("totalAmount",total); result.put("subtotalAmount",subtotal); result.put("shippingFee",shippingFee); result.put("discountAmount",discount); result.put("couponDiscountAmount",couponDiscount); result.put("memberDiscountAmount",memberDiscount); result.put("memberDiscountPercent",memberDiscountPercent); result.put("memberTier",String.valueOf(memberBenefit.getOrDefault("tierName", ""))); result.put("memberSpentBeforeOrder",memberBenefit.getOrDefault("spent",BigDecimal.ZERO)); result.put("distanceKm",posOrder?BigDecimal.ZERO:distanceKm); result.put("payment_status",paymentStatus); result.put("paymentStatus",paymentStatus); result.put("paymentReference",paymentReference); result.put("cashReceived",savedCashReceived); result.put("cashChange",savedCashChange); result.put("status",status); result.put("message",posOrder?"Đơn tại quầy đã được tạo.":"Đơn hàng đã được tạo và đang chờ xác nhận.");
        return result;
    }

    @GetMapping
    public List<Map<String,Object>> list() {
        JwtPrincipal u=currentUser();
        // Nhân viên/Admin có thể xem toàn bộ; tài khoản khách chỉ nhận đơn do chính mình đặt.
        String columns = "SELECT o.OrderID id,o.OrderID order_id,o.UserID user_id,o.TotalAmount total,o.TotalAmount total_amount,"
            + "o.OrderDate order_date,o.OrderDate created_at,o.OrderDate date,o.Status status,o.PaymentStatus payment_status,"
            + "o.CustomerName customer_name,o.CustomerPhone customer_phone,o.ShippingAddress shipping_address,o.AddressID address_id,"
            + "o.PaymentMethod payment_method,o.TrackingNumber tracking_number,o.ShippingFee shipping_fee,o.DiscountAmount discount_amount,"
            + "o.SalesChannel channel,o.HandledBy handled_by,o.PaymentReference payment_reference,o.CashReceived cash_received,"
            + "o.CashChange cash_change,o.DeliveryDistanceKm distance_km,o.CancelReason cancel_reason,o.PaymentDueAt payment_due_at,"
            + "o.PaymentConfirmedAt payment_confirmed_at,o.UpdatedAt updated_at FROM dbo.Orders o ";
        List<Map<String,Object>> orders = (u.roleId()==1 || u.roleId()==3 || u.roleId()==4)
            ? jdbc.queryForList(columns + "ORDER BY o.OrderDate DESC, o.OrderID DESC")
            : jdbc.queryForList(columns + "WHERE o.UserID=? ORDER BY o.OrderDate DESC, o.OrderID DESC", u.id());
        for (Map<String,Object> order : orders) {
            long orderId = number(order.get("id"));
            order.put("products", jdbc.queryForList("SELECT od.OrderDetailID id,od.OrderDetailID order_detail_id,od.ProductID product_id,od.ProductVariantID variant_id,od.ProductNameSnapshot name,od.Quantity quantity,od.UnitPrice price,od.Size size,od.Color color,od.ImageURLSnapshot image FROM dbo.OrderDetails od WHERE od.OrderID=?", orderId));
            order.put("status_history", jdbc.queryForList("SELECT OldStatus old_status,NewStatus new_status,Note note,ChangedAt changed_at,ChangedBy changed_by FROM dbo.OrderStatusHistory WHERE OrderID=? ORDER BY ChangedAt,HistoryID", orderId));
            order.put("payment_history", jdbc.queryForList("SELECT OldPaymentStatus old_status,NewPaymentStatus new_status,Note note,ChangedAt changed_at,ChangedBy changed_by FROM dbo.OrderPaymentHistory WHERE OrderID=? ORDER BY ChangedAt,PaymentHistoryID", orderId));
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
        result.put("status_history",jdbc.queryForList("SELECT OldStatus old_status,NewStatus new_status,Note note,ChangedAt changed_at,ChangedBy changed_by FROM dbo.OrderStatusHistory WHERE OrderID=? ORDER BY ChangedAt,HistoryID",id));
        result.put("payment_history",jdbc.queryForList("SELECT OldPaymentStatus old_status,NewPaymentStatus new_status,Note note,ChangedAt changed_at,ChangedBy changed_by FROM dbo.OrderPaymentHistory WHERE OrderID=? ORDER BY ChangedAt,PaymentHistoryID",id));
        result.put("payments",jdbc.queryForList("SELECT Provider provider,ProviderTxnRef payment_reference,Amount amount,Status status,CreatedAt created_at,CompletedAt completed_at FROM dbo.PaymentTransactions WHERE OrderID=? ORDER BY CreatedAt",id));
        result.put("payment_reference",jdbc.queryForObject("SELECT PaymentReference FROM dbo.Orders WHERE OrderID=?",String.class,id));
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
        String requested = firstText(body, "status", "Status");
        if (requested.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu trạng thái đơn hàng.");
        if (!allowedOrderStatus(requested)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái đơn hàng không nằm trong danh sách cho phép.");

        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT Status,PaymentStatus,PaymentMethod,UserID,SalesChannel FROM dbo.Orders WHERE OrderID=?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
        Map<String,Object> order = rows.get(0);
        String oldStatus = textValue(order.get("Status"));
        String paymentStatus = textValue(order.get("PaymentStatus"));
        String paymentMethod = textValue(order.get("PaymentMethod"));
        String salesChannel = textValue(order.get("SalesChannel"));
        boolean staff = isStaff(user);
        boolean cancel = normalize(requested).equals("dahuy");
        String oldKey = normalize(oldStatus);
        String nextKey = normalize(requested);
        boolean bankTransfer = isBankMethod("", paymentMethod);
        boolean cod = isCodMethod(paymentMethod);
        boolean posOrder = normalize(salesChannel).equals("offline");
        boolean paid = isPaid(paymentStatus);

        if (!staff) {
            long ownerId = number(order.get("UserID"));
            if (ownerId != user.id()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
            if (!cancel) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Khách hàng chỉ có thể hủy đơn của chính mình.");
        }
        if (oldKey.equals(nextKey)) return Map.of("success",true,"id",id,"status",oldStatus,"payment_status",paymentStatus,"unchanged",true);

        boolean terminalComplete = isCompletedOrderStatus(oldKey);
        boolean terminalCancelled = oldKey.equals("dahuy");
        boolean refundComplete = normalize(paymentStatus).equals("dahoantien");
        if (terminalComplete || terminalCancelled || refundComplete) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                terminalComplete ? "Đơn đã hoàn thành và đã khóa, không thể thao tác thêm."
                    : refundComplete ? "Đơn đã hoàn tiền, không thể thao tác thêm."
                    : "Đơn đã hủy; chỉ được cập nhật tiến trình hoàn tiền nếu còn cần xử lý.");
        }

        String resolutionCode = firstText(body,"resolutionCode","resolution_code").trim().toUpperCase(Locale.ROOT);
        boolean staffDeliveryCancel = staff && cancel && Set.of("danggiaohang", "dangvanchuyen", "giaohangthatbai", "vekho", "thanhtoan").contains(oldKey);
        boolean pendingBankCancel = bankTransfer && !paid
            && Set.of("thanhtoan", "choxacnhan", "chothanhtoan", "choxacnhanthanhtoan").contains(oldKey);
        boolean cancellable = Set.of("choxacnhan", "dangxuly", "choxuly").contains(oldKey)
            || pendingBankCancel || staffDeliveryCancel;
        if (cancel && (!cancellable || (!staff && !Set.of("choxacnhan", "dangxuly", "choxuly", "thanhtoan").contains(oldKey)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Chỉ có thể hủy đơn khi chờ xác nhận/đang xử lý; nhân viên có thể hủy ở bước giao vận theo quy trình.");
        }

        // Chỉ cho phép chuyển đúng bước tiếp theo. Ngoại lệ giao hàng lỗi/về kho được giữ để xử lý vận hành.
        if (!cancel && !isAllowedOrderTransition(oldKey, nextKey, bankTransfer, cod, posOrder, paid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Không thể chuyển từ trạng thái “" + oldStatus + "” sang “" + requested + "”. Hãy xử lý đúng bước tiếp theo của đơn hàng.");
        }

        boolean completed = isCompletedOrderStatus(nextKey);
        boolean confirmed = nextKey.equals("daxacnhan");
        String reason = cancel ? "Đơn hàng đã được hủy." : firstText(body,"reason","cancelReason","cancel_reason","note","Note");
        jdbc.update("UPDATE dbo.Orders SET Status=?, UpdatedAt=GETDATE(), OrderConfirmedAt=CASE WHEN ?=1 THEN COALESCE(OrderConfirmedAt,GETDATE()) ELSE OrderConfirmedAt END, CompletedAt=CASE WHEN ?=1 THEN COALESCE(CompletedAt,GETDATE()) ELSE CompletedAt END, DeliveredDate=CASE WHEN ?=1 THEN COALESCE(DeliveredDate,GETDATE()) ELSE DeliveredDate END, CancelledAt=CASE WHEN ?=1 THEN COALESCE(CancelledAt,GETDATE()) ELSE CancelledAt END, CancellationReason=CASE WHEN ?=1 THEN ? ELSE CancellationReason END, CancelReason=CASE WHEN ?=1 THEN ? ELSE CancelReason END WHERE OrderID=?",
            cancel ? "Đã hủy" : requested, confirmed?1:0, completed?1:0, completed?1:0, cancel?1:0, cancel?1:0, reason, cancel?1:0, reason, id);
        String historyNote = cancel ? "Đơn hàng đã được hủy." : (reason.isBlank() ? "Cập nhật trạng thái bởi "+user.fullName() : reason);
        jdbc.update("INSERT INTO dbo.OrderStatusHistory (OrderID,OldStatus,NewStatus,Note,ChangedBy,ChangedAt) VALUES (?,?,?,?,?,GETDATE())", id, oldStatus, cancel ? "Đã hủy" : requested, historyNote, user.id());

        if (cancel) {
            String updatedPayment = paid ? "Đang hoàn tiền" : (bankTransfer ? "Thất bại" : "Đã hủy");
            jdbc.update("UPDATE dbo.Orders SET PaymentStatus=?,IsCountedAsRevenue=0,RevenueEligibleDate=NULL,UpdatedAt=GETDATE() WHERE OrderID=?",updatedPayment,id);
            if (!updatedPayment.equals(paymentStatus)) recordPaymentHistory(id,paymentStatus,updatedPayment,"Đơn hàng đã được hủy.",user.id());
            if (!paid) jdbc.update("UPDATE dbo.PaymentTransactions SET Status=N'FAILED' WHERE OrderID=? AND Status<>N'SUCCESS'",id);
            paymentStatus = updatedPayment;
        } else if (completed && cod && !isPaid(paymentStatus)) {
            jdbc.update("UPDATE dbo.Orders SET PaymentStatus=N'Đã thanh toán',PaymentConfirmedAt=COALESCE(PaymentConfirmedAt,GETDATE()),RevenueEligibleDate=COALESCE(RevenueEligibleDate,GETDATE()),IsCountedAsRevenue=1,UpdatedAt=GETDATE() WHERE OrderID=?",id);
            recordPaymentHistory(id,paymentStatus,"Đã thanh toán","Thu tiền khi giao hàng hoàn tất",user.id());
            paymentStatus = "Đã thanh toán";
        } else if (completed && isPaid(paymentStatus)) {
            jdbc.update("UPDATE dbo.Orders SET RevenueEligibleDate=COALESCE(RevenueEligibleDate,GETDATE()),IsCountedAsRevenue=1,UpdatedAt=GETDATE() WHERE OrderID=?",id);
        }
        return Map.of("success",true,"id",id,"status",cancel ? "Đã hủy" : requested,"order_status",cancel ? "Đã hủy" : requested,"payment_status",paymentStatus);
    }

    @PutMapping("/{id}/payment")
    @Transactional
    public Map<String,Object> updatePayment(@PathVariable long id,@RequestBody Map<String,Object> body) {
        JwtPrincipal user=currentUser();
        if (!isStaff(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ Admin/Quản lý/Nhân viên được xác nhận thanh toán.");
        String requested=firstText(body,"payment_status","paymentStatus","status");
        if (requested.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiếu trạng thái thanh toán.");
        String normalized = normalize(requested);
        Map<String,String> known = new HashMap<>();
        for (String value : List.of("Chờ xử lý","Chờ xác nhận thanh toán","Chờ thanh toán","Đã xác nhận","Đang xử lý","Đã thanh toán","Đã hoàn thành","Thất bại","Chờ hoàn tiền","Đang hoàn tiền","Đã hoàn tiền","Đã hủy","Chưa thanh toán")) known.put(normalize(value),value);
        if (!known.containsKey(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trạng thái thanh toán không hợp lệ.");
        requested = known.get(normalized);

        List<Map<String,Object>> rows=jdbc.queryForList("SELECT OrderID,UserID,PaymentStatus,Status,PaymentMethod,TotalAmount,PaymentReference,SalesChannel FROM dbo.Orders WHERE OrderID=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng.");
        Map<String,Object> order=rows.get(0);
        String oldPayment=textValue(order.get("PaymentStatus"));
        String orderStatus=textValue(order.get("Status"));
        String paymentMethod=textValue(order.get("PaymentMethod"));
        String salesChannel=textValue(order.get("SalesChannel"));
        String oldPaymentKey=normalize(oldPayment);
        String orderKey=normalize(orderStatus);
        boolean bank=isBankMethod("",paymentMethod);
        boolean cod=isCodMethod(paymentMethod);
        boolean posOrder=normalize(salesChannel).equals("offline");
        boolean paid=isPaid(requested);
        boolean failure=Set.of("thatbai","dahuy").contains(normalize(requested));
        boolean cancelled=orderKey.equals("dahuy");
        boolean orderCompleted=isCompletedOrderStatus(orderKey);
        boolean refundInProgress=normalize(requested).equals("danghoantien") || normalize(requested).equals("chohoantien");
        boolean refundComplete=normalize(requested).equals("dahoantien");

        if (oldPaymentKey.equals(normalize(requested)))
            return Map.of("success",true,"orderId",id,"payment_status",oldPayment,"order_status",orderStatus,"status",orderStatus,"alreadyPaid",isPaid(oldPayment),"unchanged",true);
        if (orderCompleted) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đơn đã hoàn thành và đã khóa, không thể sửa trạng thái thanh toán.");
        if (oldPaymentKey.equals("dahoantien")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đơn đã hoàn tiền, không thể thao tác thêm.");
        if (isPaid(oldPayment) && !paid && !Set.of("danghoantien", "chohoantien", "dahoantien").contains(normalize(requested))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Thanh toán đã được xác nhận; nếu cần hủy hãy hủy đơn trước để tạo tiến trình hoàn tiền.");
        }
        if (bank && paid && !isPaid(oldPayment)
                && !Set.of("thanhtoan", "choxacnhan", "chothanhtoan", "choxacnhanthanhtoan").contains(orderKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ xác nhận chuyển khoản ở bước Thanh toán/Chờ xác nhận thanh toán.");
        }
        if (cod && paid && !isPaid(oldPayment) && !orderKey.equals("thanhtoan")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ xác nhận tiền COD ở bước Thanh toán.");
        }

        // Hoàn tiền chỉ được xử lý sau khi đơn đã hủy và trước khi hoàn tiền hoàn tất.
        if (refundInProgress || refundComplete) {
            if (!cancelled) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ đơn đã hủy mới được cập nhật tiến trình hoàn tiền.");
            boolean wasPaidOrRefunding = isPaid(oldPayment) || Set.of("danghoantien","chohoantien","dahoantien").contains(oldPaymentKey);
            if (!wasPaidOrRefunding) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đơn chưa thu tiền thành công nên không có khoản cần hoàn.");
            jdbc.update("UPDATE dbo.Orders SET PaymentStatus=?,UpdatedAt=GETDATE(),IsCountedAsRevenue=0,RevenueEligibleDate=NULL WHERE OrderID=?",requested,id);
            recordPaymentHistory(id,oldPayment,requested,firstText(body,"note","Note").isBlank() ? "Cập nhật tiến trình hoàn tiền" : firstText(body,"note","Note"),user.id());
            return Map.of("success",true,"orderId",id,"payment_status",requested,"order_status",orderStatus,"status",orderStatus,"alreadyPaid",false);
        }
        if (cancelled && !failure) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đơn đã hủy; chỉ được cập nhật tiến trình hoàn tiền nếu có khoản đã thu.");
        if (!cancelled && (normalize(requested).equals("danghoantien") || normalize(requested).equals("chohoantien") || normalize(requested).equals("dahoantien")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ đơn đã hủy mới được cập nhật tiến trình hoàn tiền.");
        if (failure && !bank) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ đơn chuyển khoản đang chờ mới có thể đánh dấu thanh toán thất bại.");
        if (cancelled && failure) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đơn đã hủy và không cần hủy thêm lần nữa.");

        String nextOrderStatus=orderStatus;
        boolean cancellationRequested=failure;
        if (bank && paid && Set.of("thanhtoan","choxacnhan","chothanhtoan","choxacnhanthanhtoan").contains(normalize(orderStatus))) {
            // POS giữ ở bước Thanh toán sau khi đối soát; nhân viên bấm bước tiếp theo để hoàn tất.
            // Đơn online chuyển tới bước xác nhận đơn.
            nextOrderStatus = posOrder ? "Thanh toán" : "Đã xác nhận";
        } else if (cod && paid && normalize(orderStatus).equals("thanhtoan")) {
            nextOrderStatus="Đã hoàn thành";
        } else if (failure && Set.of("thanhtoan","choxacnhan","chothanhtoan","choxacnhanthanhtoan").contains(normalize(orderStatus))) {
            nextOrderStatus="Đã hủy";
        }
        if (cancellationRequested && !normalize(nextOrderStatus).equals("dahuy")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ có thể báo thanh toán thất bại khi đơn đang chờ thanh toán.");
        if (!cancellationRequested && !paid && !Set.of("choxuly","choxacnhanthanhtoan","chothanhtoan","chuathanhtoan","thatbai","dahuy").contains(normalize(requested))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trạng thái thanh toán không hợp lệ cho thao tác này.");
        }

        String cancelNote="Đơn hàng đã được hủy.";
        jdbc.update("UPDATE dbo.Orders SET PaymentStatus=?,PaymentConfirmedAt=CASE WHEN ?=1 THEN COALESCE(PaymentConfirmedAt,GETDATE()) ELSE PaymentConfirmedAt END,Status=?,CancelledAt=CASE WHEN ?=1 THEN COALESCE(CancelledAt,GETDATE()) ELSE CancelledAt END,CancellationReason=CASE WHEN ?=1 THEN ? ELSE CancellationReason END,CancelReason=CASE WHEN ?=1 THEN ? ELSE CancelReason END,CompletedAt=CASE WHEN ?=1 THEN COALESCE(CompletedAt,GETDATE()) ELSE CompletedAt END,UpdatedAt=GETDATE() WHERE OrderID=?",
            requested,paid?1:0,nextOrderStatus,cancellationRequested?1:0,cancellationRequested?1:0,cancelNote,cancellationRequested?1:0,cancelNote,
            isCompletedOrderStatus(normalize(nextOrderStatus))?1:0,id);
        if (normalize(nextOrderStatus).equals("daxacnhan")) {
            jdbc.update("UPDATE dbo.Orders SET OrderConfirmedAt=COALESCE(OrderConfirmedAt,GETDATE()) WHERE OrderID=?", id);
        }
        if (cancellationRequested) {
            jdbc.update("UPDATE dbo.Orders SET IsCountedAsRevenue=0,RevenueEligibleDate=NULL WHERE OrderID=?",id);
        } else if (paid && isCompletedOrderStatus(normalize(nextOrderStatus))) {
            jdbc.update("UPDATE dbo.Orders SET IsCountedAsRevenue=1,RevenueEligibleDate=COALESCE(RevenueEligibleDate,GETDATE()) WHERE OrderID=?",id);
        }
        recordPaymentHistory(id,oldPayment,requested, cancellationRequested ? cancelNote : firstText(body,"note","Note"),user.id());
        if (!normalize(orderStatus).equals(normalize(nextOrderStatus))) {
            jdbc.update("INSERT INTO dbo.OrderStatusHistory (OrderID,OldStatus,NewStatus,Note,ChangedBy,ChangedAt) VALUES (?,?,?,?,?,GETDATE())",id,orderStatus,nextOrderStatus,cancellationRequested?cancelNote:(paid?"Đã đối soát thanh toán thành công":"Thanh toán không thành công"),user.id());
        }
        if (paid) {
            jdbc.update("UPDATE dbo.PaymentTransactions SET Status=N'SUCCESS',CompletedAt=COALESCE(CompletedAt,GETDATE()) WHERE OrderID=? AND Status<>N'SUCCESS'",id);
        } else if (Set.of("choxuly","choxacnhanthanhtoan","chothanhtoan","chuathanhtoan").contains(normalize(requested))) {
            jdbc.update("UPDATE dbo.PaymentTransactions SET Status=N'PENDING' WHERE OrderID=? AND Status<>N'SUCCESS'",id);
        } else if (failure) {
            jdbc.update("UPDATE dbo.PaymentTransactions SET Status=N'FAILED' WHERE OrderID=? AND Status<>N'SUCCESS'",id);
        }
        return Map.of("success",true,"orderId",id,"payment_status",requested,"order_status",nextOrderStatus,"status",nextOrderStatus,"alreadyPaid",isPaid(oldPayment));
    }

    @GetMapping("/{id}/history")
    public Map<String,Object> history(@PathVariable long id) {
        JwtPrincipal user=currentUser();
        List<Map<String,Object>> owned = (isStaff(user))
            ? jdbc.queryForList("SELECT OrderID FROM dbo.Orders WHERE OrderID=?",id)
            : jdbc.queryForList("SELECT OrderID FROM dbo.Orders WHERE OrderID=? AND UserID=?",id,user.id());
        if (owned.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng.");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("orderHistory",jdbc.queryForList("SELECT OldStatus old_status,NewStatus new_status,Note note,ChangedBy changed_by,ChangedAt changed_at FROM dbo.OrderStatusHistory WHERE OrderID=? ORDER BY ChangedAt,HistoryID",id));
        result.put("paymentHistory",jdbc.queryForList("SELECT OldPaymentStatus old_status,NewPaymentStatus new_status,Note note,ChangedBy changed_by,ChangedAt changed_at FROM dbo.OrderPaymentHistory WHERE OrderID=? ORDER BY ChangedAt,PaymentHistoryID",id));
        result.put("transactions",jdbc.queryForList("SELECT Provider provider,ProviderTxnRef reference,Amount amount,Status status,CreatedAt created_at,CompletedAt completed_at FROM dbo.PaymentTransactions WHERE OrderID=? ORDER BY CreatedAt",id));
        return result;
    }

    private void recordPaymentHistory(long orderId,String oldStatus,String newStatus,String note,long userId) {
        jdbc.update("INSERT INTO dbo.OrderPaymentHistory(OrderID,OldPaymentStatus,NewPaymentStatus,Note,ChangedBy,ChangedAt) VALUES(?,?,?,?,?,SYSDATETIME())",orderId,oldStatus,newStatus,note,userId);
    }

    private static boolean isStaff(JwtPrincipal user) { return user.roleId()==1 || user.roleId()==3 || user.roleId()==4; }
    private static boolean isBankMethod(String code,String name) {
        String s=normalize((code==null?"":code)+" "+(name==null?"":name));
        return s.contains("bank") || s.contains("chuyenkhoan") || s.contains("vietqr");
    }
    private static boolean isCodMethod(String value) {
        String s=normalize(value);
        return s.contains("cod") || s.contains("tienmat") || s.contains("thuhộ") || s.contains("thuho") || s.contains("nhanhang");
    }
    private static boolean isCompletedOrderStatus(String normalizedKey) {
        return Set.of("dagiaohangthanhcong", "danhanhang", "dahoanthanh", "hoantat", "dathoanthanh", "dahoantattrahang").contains(normalizedKey);
    }
    private static boolean isAllowedOrderTransition(String oldKey, String nextKey, boolean bank, boolean cod, boolean posOrder, boolean paid) {
        if (oldKey.equals(nextKey)) return true;
        if (oldKey.equals("choxuly") && nextKey.equals("dangxuly")) return true;
        if (oldKey.equals("giaohangthatbai") && nextKey.equals("vekho")) return true;
        if (oldKey.equals("vekho") && nextKey.equals("dangvanchuyen")) return true;
        if (oldKey.equals("dangvanchuyen") && nextKey.equals("giaohangthatbai")) return true;
        if (posOrder) {
            // POS: Đang xử lý -> Thanh toán -> Đã hoàn thành.
            // Cho phép vào bước Thanh toán khi còn chờ thu tiền; không cho hoàn tất khi chưa thu đủ.
            if ((oldKey.equals("dangxuly") || oldKey.equals("choxuly")) && nextKey.equals("thanhtoan")) return true;
            if (Set.of("choxacnhan", "choxuly", "choxacnhanthanhtoan", "chothanhtoan").contains(oldKey)
                    && nextKey.equals("dangxuly")) return true;
            if (oldKey.equals("thanhtoan") && isCompletedOrderStatus(nextKey)) return paid;
            return false;
        }
        if (bank) {
            if (oldKey.equals("daxacnhan") && nextKey.equals("dangxuly")) return true;
            if (oldKey.equals("dangxuly") && nextKey.equals("danggiaohang")) return true;
            if (oldKey.equals("danggiaohang") && nextKey.equals("dangvanchuyen")) return true;
            if (oldKey.equals("dangvanchuyen") && nextKey.equals("dahoanthanh")) return paid;
            // POS chuyển khoản được tạo tại bước Thanh toán; chỉ payment API mới có quyền hoàn tất.
            return false;
        }
        if (cod) {
            if (oldKey.equals("choxacnhan") && nextKey.equals("dangxuly")) return true;
            if (oldKey.equals("dangxuly") && nextKey.equals("dangvanchuyen")) return true;
            if (oldKey.equals("dangvanchuyen") && nextKey.equals("thanhtoan")) return true;
            if (oldKey.equals("thanhtoan") && nextKey.equals("dahoanthanh")) return true;
            return false;
        }
        // Luồng POS tiền mặt đã hoàn tất từ lúc tạo đơn; không cho sửa sang bước khác.
        return false;
    }

    private static boolean allowedOrderStatus(String value) {
        return Set.of("choxacnhan","choxuly","daxacnhan","dangxuly","dangvanchuyen","danggiaohang","dagiaohangthanhcong","danhanhang","dahoanthanh","hoantat","dahuy","giaohangthatbai","vekho","yeucautrahang","dahoantattrahang","chothanhtoan","dathoanthanh","thanhtoan").contains(normalize(value));
    }
    private static String normalize(String value) {
        if(value==null)return "";
        return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
    }
    private static BigDecimal calculateShippingFee(BigDecimal distance) {
        BigDecimal km=distance==null||distance.signum()<0?BigDecimal.ZERO:distance;
        if(km.compareTo(new BigDecimal("0.5"))<=0)return BigDecimal.ZERO;
        if(km.compareTo(new BigDecimal("2"))<=0)return new BigDecimal("15700");
        if(km.compareTo(new BigDecimal("3"))<=0)return new BigDecimal("19700");
        return new BigDecimal("19700").add(km.subtract(new BigDecimal("3")).setScale(0,java.math.RoundingMode.CEILING).multiply(new BigDecimal("5400")));
    }
    private double storeOriginCoordinate(String key, double fallback) {
        try {
            List<String> values = jdbc.query("SELECT TOP 1 SettingValue FROM dbo.StoreSettings WHERE SettingKey=?", (rs, rowNum) -> rs.getString(1), key);
            if (!values.isEmpty()) {
                double value = Double.parseDouble(values.get(0));
                if (Double.isFinite(value) && value != 0) return value;
            }
        } catch (RuntimeException ignored) { /* fallback is the configured shop coordinate for local demo */ }
        return fallback;
    }
    private static BigDecimal optionalDecimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return new BigDecimal(String.valueOf(value)); } catch (RuntimeException ignored) { return null; }
    }
    private static boolean validCoordinates(BigDecimal latitude, BigDecimal longitude) {
        return latitude != null && longitude != null
            && latitude.compareTo(new BigDecimal("-90")) >= 0 && latitude.compareTo(new BigDecimal("90")) <= 0
            && longitude.compareTo(new BigDecimal("-180")) >= 0 && longitude.compareTo(new BigDecimal("180")) <= 0
            && (latitude.signum() != 0 || longitude.signum() != 0);
    }
    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2-lat1), dLon = Math.toRadians(lon2-lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2) + Math.cos(p1)*Math.cos(p2)*Math.sin(dLon/2)*Math.sin(dLon/2);
        return 2*r*Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0,1-a)));
    }

    private static boolean isPaid(String s) {
        return Set.of("paid","success","succeeded","completed","daxacnhan","dathanhtoan","dathanhtoanthanhcong","thanhtoanthanhcong","thanhcong","hoantatthanhtoan","dahoanthanh").contains(normalize(s));
    }

    /** Online orders belong to the authenticated customer. POS orders belong to the
     * selected customer account, never to the employee who rang up the sale. */
    private Long resolveOrderCustomerId(Map<String,Object> body, boolean posOrder, JwtPrincipal staffOrCustomer) {
        if (!posOrder) return staffOrCustomer.id();
        if (staffOrCustomer.roleId() != 1 && staffOrCustomer.roleId() != 3 && staffOrCustomer.roleId() != 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Admin/Nhân viên được tạo đơn bán tại quầy.");
        }
        Object rawId = first(body, "user_id", "customer_id", "customerId", "UserID");
        if (rawId == null || String.valueOf(rawId).isBlank()) return null; // khách lẻ, không có tài khoản tích lũy
        long customerId = positiveLong(rawId, "Mã khách hàng");
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT UserID user_id,RoleID role_id,ISNULL(IsActive,1) is_active,ISNULL(Source,'') source FROM dbo.Users WHERE UserID=?", customerId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy tài khoản khách hàng đã chọn.");
        Map<String,Object> customer = rows.get(0);
        long roleId = number(customer.get("role_id"));
        String source = String.valueOf(customer.getOrDefault("source", ""));
        if (roleId != 2 && !"POS".equalsIgnoreCase(source)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản được chọn không phải khách hàng.");
        }
        if (number(customer.get("is_active")) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản khách hàng đã bị khóa, không thể bán hàng theo tài khoản này.");
        }
        return customerId;
    }

    /** Returns current spend and the highest active member tier already earned by this customer. */
    private Map<String,Object> getMemberBenefit(Long customerId) {
        BigDecimal spent = BigDecimal.ZERO;
        if (customerId != null) {
            List<BigDecimal> totals = jdbc.query("SELECT COALESCE(SUM(CASE WHEN ISNULL(o.IsCountedAsRevenue,0)=1 THEN o.TotalAmount ELSE 0 END),0) FROM dbo.Orders o WHERE o.UserID=?", (rs, rowNum) -> rs.getBigDecimal(1), customerId);
            if (!totals.isEmpty() && totals.get(0) != null) spent = totals.get(0);
        }
        List<Map<String,Object>> tiers = jdbc.queryForList("SELECT TOP 1 TierCode tierCode,TierName tierName,MinSpend minSpend,DiscountPercent discountPercent FROM dbo.MemberTiers WHERE IsActive=1 AND MinSpend<=? ORDER BY MinSpend DESC,SortOrder DESC,TierID DESC", spent);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("spent", spent);
        if (tiers.isEmpty()) {
            result.put("tierCode", "NEW"); result.put("tierName", "Mới");
            result.put("minSpend", new BigDecimal("100000000")); result.put("discountPercent", 0);
        } else {
            result.putAll(tiers.get(0));
            // Defensive aliases, as JDBC column casing can vary by driver.
            result.putIfAbsent("tierCode", result.get("TIER_CODE"));
            result.putIfAbsent("tierName", result.get("TIER_NAME"));
            result.putIfAbsent("minSpend", result.get("MIN_SPEND"));
            result.putIfAbsent("discountPercent", result.get("DISCOUNT_PERCENT"));
        }
        return result;
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
