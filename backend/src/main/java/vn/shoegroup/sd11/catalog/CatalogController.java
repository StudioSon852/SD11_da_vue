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
    @PostMapping("/shipping/quote")
    public Map<String,Object> quote(@RequestBody Map<String,Object> body) {
        Object raw = body.get("shippingMethodId");
        if (raw == null) raw = body.get("shipping_method_id");
        int id;
        try { id = Integer.parseInt(String.valueOf(raw)); } catch (Exception e) { id = 0; }
        String methodCode = String.valueOf(body.getOrDefault("methodCode", body.getOrDefault("method_code", ""))).trim();
        List<Map<String,Object>> rows = id > 0
            ? jdbc.queryForList("SELECT TOP 1 ShippingMethodID id, MethodName name, MethodCode code, ISNULL(BasePrice,0) basePrice, ISNULL(PricePerKm,0) pricePerKm, ISNULL(EstimatedTimeText,N'') eta FROM dbo.ShippingMethods WHERE ShippingMethodID=? AND ISNULL(IsActive,1)=1", id)
            : List.of();
        if (rows.isEmpty() && !methodCode.isBlank()) rows = jdbc.queryForList("SELECT TOP 1 ShippingMethodID id, MethodName name, MethodCode code, ISNULL(BasePrice,0) basePrice, ISNULL(PricePerKm,0) pricePerKm, ISNULL(EstimatedTimeText,N'') eta FROM dbo.ShippingMethods WHERE UPPER(MethodCode)=UPPER(?) AND ISNULL(IsActive,1)=1", methodCode);
        if (rows.isEmpty()) rows = jdbc.queryForList("SELECT TOP 1 ShippingMethodID id, MethodName name, MethodCode code, ISNULL(BasePrice,0) basePrice, ISNULL(PricePerKm,0) pricePerKm, ISNULL(EstimatedTimeText,N'') eta FROM dbo.ShippingMethods WHERE ISNULL(IsActive,1)=1 ORDER BY ShippingMethodID");
        if (rows.isEmpty()) return Map.of("success", true, "shippingFee", 30000, "fee", 30000, "basePrice", 30000, "pricePerKm", 0, "distanceKm", 0, "methodCode", methodCode.isBlank() ? "STANDARD" : methodCode, "eta", "2-5 ngày");
        Map<String,Object> row = rows.get(0);
        Object fee = row.get("basePrice");
        Object perKm = row.get("pricePerKm");
        Object resolvedCode = row.get("code");
        BigDecimal price = fee == null ? BigDecimal.valueOf(30000) : new BigDecimal(String.valueOf(fee));
        BigDecimal perKmPrice = perKm == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(perKm));
        return Map.of("success", true, "shippingFee", price, "fee", price, "basePrice", price,
            "pricePerKm", perKmPrice, "distanceKm", 0, "methodCode", resolvedCode == null ? methodCode : resolvedCode,
            "eta", row.get("eta"), "method", row);
    }
}
