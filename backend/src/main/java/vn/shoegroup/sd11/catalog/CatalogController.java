package vn.shoegroup.sd11.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final JdbcTemplate jdbc;
    public CatalogController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/categories")
    public List<Map<String,Object>> categories() {
        return jdbc.queryForList("SELECT CategoryID id, CategoryID category_id, CategoryName name, CategoryName category_name, LOWER(REPLACE(CategoryName,N' ',N'-')) slug FROM dbo.Categories WHERE ISNULL(IsActive,1)=1 ORDER BY CategoryName");
    }
    @GetMapping("/brands")
    public List<Map<String,Object>> brands() {
        return jdbc.queryForList("SELECT BrandID id, BrandID brand_id, BrandName name, BrandName brand_name FROM dbo.Brands WHERE ISNULL(IsActive,1)=1 ORDER BY BrandName");
    }
    @GetMapping("/materials")
    public List<Map<String,Object>> materials() {
        return jdbc.queryForList("SELECT MaterialID id, MaterialName name, MaterialName material_name FROM dbo.Materials WHERE ISNULL(IsActive,1)=1 ORDER BY MaterialName");
    }
    @GetMapping("/colors")
    public List<Map<String,Object>> colors() {
        return jdbc.queryForList("SELECT ColorID id, ColorName name, ColorName color_name, ColorHex hex, ColorHex color_hex FROM dbo.Colors WHERE IsActive=1 ORDER BY SortOrder, ColorName");
    }
    @GetMapping("/sizes")
    public List<Map<String,Object>> sizes() {
        return jdbc.queryForList("SELECT SizeID id, SizeName name, SizeName size_name, SizeStandard standard FROM dbo.Sizes WHERE IsActive=1 ORDER BY SortOrder, SizeName");
    }
    @GetMapping("/collections")
    public List<Map<String,Object>> collections() {
        return jdbc.queryForList("SELECT CollectionID id, CollectionName name, Slug slug, BrandID brand_id FROM dbo.Collections WHERE ISNULL(IsActive,1)=1 ORDER BY CollectionName");
    }
    @GetMapping("/shippingmethods")
    public List<Map<String,Object>> shippingMethods() {
        return jdbc.queryForList("SELECT ShippingMethodID id, MethodName name, MethodCode code, ISNULL(BasePrice,0) basePrice, ISNULL(EstimatedTimeText,N'') eta FROM dbo.ShippingMethods WHERE ISNULL(IsActive,1)=1 ORDER BY ShippingMethodID");
    }
    @GetMapping("/paymentmethods")
    public List<Map<String,Object>> paymentMethods() {
        return jdbc.queryForList("SELECT PaymentMethodID id, MethodName name, MethodCode code, Description description, IconURL icon FROM dbo.PaymentMethods WHERE ISNULL(IsActive,1)=1 ORDER BY ISNULL(SortOrder,999), MethodName");
    }
    @GetMapping("/postoffices")
    public List<Map<String,Object>> postOffices() {
        return jdbc.queryForList("SELECT PostOfficeID id, PostOfficeID PostOfficeID, Name name, Name Name, Address address, Address Address, Province province, District district, Ward ward, Phone phone, Phone Phone FROM dbo.PostOffices WHERE ISNULL(IsActive,1)=1 ORDER BY Province, District, Name");
    }
    @GetMapping("/shipping/origin")
    public Map<String,Object> shippingOrigin() {
        Map<String,String> settings = readStoreSettings();
        Map<String,Object> out = new java.util.LinkedHashMap<>();
        out.put("address", settings.getOrDefault("STORE_ORIGIN_ADDRESS", "249 Xã Đàn, Đống Đa, Hà Nội"));
        out.put("latitude", parseDouble(settings.get("STORE_ORIGIN_LATITUDE"), 21.0150));
        out.put("longitude", parseDouble(settings.get("STORE_ORIGIN_LONGITUDE"), 105.8290));
        return out;
    }

    @PostMapping("/shipping/quote")
    public Map<String,Object> quote(@RequestBody Map<String,Object> body) {
        Object raw = body.get("shippingMethodId");
        if (raw == null) raw = body.get("shipping_method_id");
        int id;
        try { id = Integer.parseInt(String.valueOf(raw)); } catch (Exception e) { id = 0; }
        String methodCode = String.valueOf(body.getOrDefault("methodCode", body.getOrDefault("method_code", ""))).trim();
        List<Map<String,Object>> rows = id > 0
            ? jdbc.queryForList("SELECT TOP 1 ShippingMethodID id, MethodName name, MethodCode code, ISNULL(BasePrice,15700) basePrice, ISNULL(PricePerKm,5400) pricePerKm, ISNULL(EstimatedTimeText,N'2-5 ngày') eta FROM dbo.ShippingMethods WHERE ShippingMethodID=? AND ISNULL(IsActive,1)=1", id)
            : List.of();
        if (rows.isEmpty() && !methodCode.isBlank()) rows = jdbc.queryForList("SELECT TOP 1 ShippingMethodID id, MethodName name, MethodCode code, ISNULL(BasePrice,15700) basePrice, ISNULL(PricePerKm,5400) pricePerKm, ISNULL(EstimatedTimeText,N'2-5 ngày') eta FROM dbo.ShippingMethods WHERE UPPER(MethodCode)=UPPER(?) AND ISNULL(IsActive,1)=1", methodCode);
        if (rows.isEmpty()) rows = jdbc.queryForList("SELECT TOP 1 ShippingMethodID id, MethodName name, MethodCode code, ISNULL(BasePrice,15700) basePrice, ISNULL(PricePerKm,5400) pricePerKm, ISNULL(EstimatedTimeText,N'2-5 ngày') eta FROM dbo.ShippingMethods WHERE ISNULL(IsActive,1)=1 ORDER BY ShippingMethodID");

        Map<String,String> settings = readStoreSettings();
        double originLat = parseDouble(settings.get("STORE_ORIGIN_LATITUDE"), 21.0150);
        double originLng = parseDouble(settings.get("STORE_ORIGIN_LONGITUDE"), 105.8290);
        BigDecimal recipientLat = optionalDecimal(body.get("recipientLatitude"));
        BigDecimal recipientLng = optionalDecimal(body.get("recipientLongitude"));
        boolean hasGps = recipientLat != null && recipientLng != null
            && recipientLat.compareTo(new BigDecimal("-90")) >= 0 && recipientLat.compareTo(new BigDecimal("90")) <= 0
            && recipientLng.compareTo(new BigDecimal("-180")) >= 0 && recipientLng.compareTo(new BigDecimal("180")) <= 0
            && (recipientLat.signum() != 0 || recipientLng.signum() != 0);
        if (!hasGps) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Thiếu tọa độ GPS địa chỉ nhận hàng để tính phí vận chuyển."
            );
        }
        double exactKm = haversineKm(originLat, originLng, recipientLat.doubleValue(), recipientLng.doubleValue());
        BigDecimal km = BigDecimal.valueOf(exactKm);
        BigDecimal fee = shippingFee(km);
        Map<String,Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        BigDecimal base = row.get("basePrice") == null ? new BigDecimal("15700") : decimal(row.get("basePrice"));
        Map<String,Object> out = new java.util.LinkedHashMap<>();
        out.put("success", true);
        out.put("shippingFee", fee);
        out.put("fee", fee);
        out.put("basePrice", base);
        out.put("pricePerKm", new BigDecimal("5400"));
        out.put("distanceKm", BigDecimal.valueOf(exactKm).setScale(2, java.math.RoundingMode.HALF_UP));
        out.put("methodCode", row.getOrDefault("code", methodCode.isBlank() ? "STANDARD" : methodCode));
        out.put("eta", row.getOrDefault("eta", "2-5 ngày"));
        out.put("method", row);
        out.put("estimatedDistance", false);
        out.put("recipientLatitude", recipientLat);
        out.put("recipientLongitude", recipientLng);
        out.put("originAddress", settings.getOrDefault("STORE_ORIGIN_ADDRESS", "249 Xã Đàn, Đống Đa, Hà Nội"));
        out.put("distanceMethod", "Haversine (tọa độ GPS)");
        return out;
    }

    private Map<String,String> readStoreSettings() {
        Map<String,String> out = new java.util.HashMap<>();
        try {
            for (Map<String,Object> row : jdbc.queryForList("SELECT SettingKey,SettingValue FROM dbo.StoreSettings WHERE SettingKey IN ('STORE_ORIGIN_ADDRESS','STORE_ORIGIN_LATITUDE','STORE_ORIGIN_LONGITUDE')")) {
                Object key = row.get("SettingKey");
                if (key != null) out.put(String.valueOf(key), row.get("SettingValue") == null ? "" : String.valueOf(row.get("SettingValue")));
            }
        } catch (org.springframework.dao.DataAccessException ignored) {
            // The migration creates StoreSettings. These fixed defaults keep the API diagnosable before it is run.
        }
        return out;
    }

    private static double parseDouble(String value, double fallback) {
        try { double v = Double.parseDouble(value); return Double.isFinite(v) ? v : fallback; }
        catch (Exception e) { return fallback; }
    }

    private static BigDecimal optionalDecimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return new BigDecimal(String.valueOf(value)); } catch (Exception e) { return null; }
    }

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = Math.toRadians(lat1), radLat2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371d * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0d, 1d - a)));
    }

    /** <=500m free; <=2km 15,700; <=3km 19,700; beyond 3km add 5,400 for each started km. */
    private static BigDecimal shippingFee(BigDecimal distance) {
        BigDecimal km = distance == null || distance.signum() < 0 ? BigDecimal.ZERO : distance;
        if (km.compareTo(new BigDecimal("0.5")) <= 0) return BigDecimal.ZERO;
        if (km.compareTo(new BigDecimal("2")) <= 0) return new BigDecimal("15700");
        if (km.compareTo(new BigDecimal("3")) <= 0) return new BigDecimal("19700");
        BigDecimal extraKm = km.subtract(new BigDecimal("3")).setScale(0, java.math.RoundingMode.CEILING);
        return new BigDecimal("19700").add(extraKm.multiply(new BigDecimal("5400")));
    }

    private static BigDecimal decimal(Object value) {
        try { return new BigDecimal(String.valueOf(value)); } catch (Exception e) { return BigDecimal.ZERO; }
    }

}
