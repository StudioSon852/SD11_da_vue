package vn.shoegroup.sd11.product;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.shoegroup.sd11.auth.JwtPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final JdbcTemplate jdbc;
    public ProductController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) Integer limit) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        boolean admin = principal instanceof JwtPrincipal u && (u.roleId() == 1 || u.roleId() == 3 || u.roleId() == 4);
        int take = limit == null ? 300 : Math.max(1, Math.min(500, limit));
        String query = q == null ? "" : q.trim();
        String cat = category == null ? "" : category.trim();
        List<Map<String, Object>> products = jdbc.queryForList("""
            SELECT TOP (?) p.ProductID id, p.ProductName name, p.ProductName product_name,
              p.BasePrice base_price,
              CASE WHEN p.SalePrice IS NOT NULL AND p.SalePrice>0 AND p.SalePrice<p.BasePrice THEN p.SalePrice ELSE p.BasePrice END price,
              CASE WHEN p.SalePrice IS NOT NULL AND p.SalePrice>0 AND p.SalePrice<p.BasePrice THEN p.BasePrice ELSE NULL END old_price,
              CASE WHEN p.SalePrice IS NOT NULL AND p.SalePrice>0 AND p.SalePrice<p.BasePrice THEN p.SalePrice ELSE 0 END sale_price,
              p.ImageURL image_url, p.Description description, p.IsFeatured is_featured,
              p.ParentSKU parent_sku, p.ProductID product_id, p.CategoryID category_id,
              COALESCE(c.CategoryName,N'Thời trang') category, COALESCE(c.CategoryName,N'Thời trang') category_name,
              p.BrandID brand_id, COALESCE(b.BrandName,N'ShoeGroup') brand, COALESCE(b.BrandName,N'ShoeGroup') brand_name,
              p.MaterialID material_id, COALESCE(m.MaterialName,N'') material_name,
              p.CollectionID collection_id, COALESCE(co.CollectionName,N'') collection_name,
              ISNULL(p.IsActive,1) active,
              COALESCE(s.SoldCount,0) sold_count
            FROM dbo.Products p
            LEFT JOIN dbo.Categories c ON c.CategoryID=p.CategoryID
            LEFT JOIN dbo.Brands b ON b.BrandID=p.BrandID
            LEFT JOIN dbo.Materials m ON m.MaterialID=p.MaterialID
            LEFT JOIN dbo.Collections co ON co.CollectionID=p.CollectionID
            LEFT JOIN dbo.vw_ProductSoldCount s ON s.ProductID=p.ProductID
            WHERE (?=1 OR ISNULL(p.IsActive,1)=1)
              AND (?='' OR p.ProductName LIKE N'%' + ? + N'%' OR b.BrandName LIKE N'%' + ? + N'%')
              AND (?='' OR c.CategoryName=? OR LOWER(c.CategoryName)=LOWER(?))
            ORDER BY ISNULL(p.IsFeatured,0) DESC, p.ProductID DESC
            """, take, admin ? 1 : 0, query, query, query, cat, cat, cat);
        for (Map<String, Object> product : products) enrichProduct(product, false);
        return products;
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        boolean admin = principal instanceof JwtPrincipal u && (u.roleId() == 1 || u.roleId() == 3 || u.roleId() == 4);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT p.ProductID id, p.ProductID product_id, p.ProductName name, p.ProductName product_name,
              CASE WHEN p.SalePrice IS NOT NULL AND p.SalePrice>0 AND p.SalePrice<p.BasePrice THEN p.SalePrice ELSE p.BasePrice END price,
              CASE WHEN p.SalePrice IS NOT NULL AND p.SalePrice>0 AND p.SalePrice<p.BasePrice THEN p.BasePrice ELSE NULL END old_price,
              CASE WHEN p.SalePrice IS NOT NULL AND p.SalePrice>0 AND p.SalePrice<p.BasePrice THEN p.SalePrice ELSE 0 END sale_price,
              p.BasePrice base_price, p.ImageURL image_url, p.Description description, p.IsFeatured is_featured,
              p.CategoryID category_id, COALESCE(c.CategoryName,N'Thời trang') category, COALESCE(c.CategoryName,N'Thời trang') category_name,
              p.BrandID brand_id, COALESCE(b.BrandName,N'ShoeGroup') brand, COALESCE(b.BrandName,N'ShoeGroup') brand_name,
              p.MaterialID material_id, COALESCE(m.MaterialName,N'') material_name,
              p.CollectionID collection_id, COALESCE(co.CollectionName,N'') collection_name,
              COALESCE(s.SoldCount,0) sold_count
            FROM dbo.Products p LEFT JOIN dbo.Categories c ON c.CategoryID=p.CategoryID
            LEFT JOIN dbo.Brands b ON b.BrandID=p.BrandID LEFT JOIN dbo.Materials m ON m.MaterialID=p.MaterialID
            LEFT JOIN dbo.Collections co ON co.CollectionID=p.CollectionID
            LEFT JOIN dbo.vw_ProductSoldCount s ON s.ProductID=p.ProductID
            WHERE p.ProductID=? AND (?=1 OR ISNULL(p.IsActive,1)=1)
            """, id, admin ? 1 : 0);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm.");
        Map<String, Object> product = rows.get(0);
        enrichProduct(product, true);
        return product;
    }

    @GetMapping("/{id}/sold-count")
    public Map<String, Object> soldCount(@PathVariable long id) {
        Long count = jdbc.queryForObject("SELECT COALESCE((SELECT SoldCount FROM dbo.vw_ProductSoldCount WHERE ProductID=?),0)", Long.class, id);
        long sold = count == null ? 0L : Math.max(0L, count);
        return Map.of("success", true, "productId", id, "soldCount", sold, "sold_count", sold);
    }

    private void enrichProduct(Map<String, Object> product, boolean full) {
        long id = ((Number) product.get("id")).longValue();
        List<Map<String, Object>> variants = jdbc.queryForList("""
            SELECT v.ProductVariantID id, v.ProductVariantID variant_id, v.ProductID product_id,
                   v.Size size, v.Size size_name, v.ColorName color, v.ColorName color_name,
                   v.ColorHex hex, v.ColorHex color_hex, v.ChildSKU sku,
                   CAST(ISNULL(p.BasePrice,0)+ISNULL(v.PriceAdjustment,0) AS decimal(18,0)) price,
                   CAST(CASE WHEN p.SalePrice>0 AND p.SalePrice<p.BasePrice THEN p.SalePrice+ISNULL(v.PriceAdjustment,0) ELSE 0 END AS decimal(18,0)) product_sale_price,
                   COALESCE((
                     SELECT TOP (1) pi.ImageURL
                     FROM dbo.ProductImages pi
                     WHERE pi.ProductID=v.ProductID
                       AND pi.ColorName=v.ColorName
                       AND NULLIF(LTRIM(RTRIM(pi.ImageURL)), '') IS NOT NULL
                     ORDER BY ISNULL(pi.IsPrimary,0) DESC, ISNULL(pi.SortOrder,0), pi.ProductImageID
                   ), p.ImageURL) image_url,
                   ISNULL(v.IsActive,1) active
            FROM dbo.ProductVariants v INNER JOIN dbo.Products p ON p.ProductID=v.ProductID
            WHERE v.ProductID=? AND ISNULL(v.IsActive,1)=1 ORDER BY v.ProductVariantID
            """, id);

        Map<String, Object> bestProductVariantOffer = null;
        BigDecimal bestProductVariantSale = null;
        for (Map<String, Object> variant : variants) {
            BigDecimal base = variant.get("price") instanceof BigDecimal value ? value : new BigDecimal(String.valueOf(variant.getOrDefault("price", 0)));
            BigDecimal productSale = variant.get("product_sale_price") instanceof BigDecimal saleValue ? saleValue : BigDecimal.ZERO;
            BigDecimal bestPrice = productSale.signum() > 0 && productSale.compareTo(base) < 0 ? productSale : base;
            Integer bestDiscountId = null;
            List<Map<String, Object>> offers = jdbc.queryForList("""
                SELECT VariantDiscountID id,DiscountType discount_type,DiscountValue discount_value,
                       MaxDiscountAmount max_discount
                FROM dbo.VariantDiscounts
                WHERE ProductVariantID=? AND ProductID=? AND ISNULL(IsActive,1)=1
                  AND (StartDate IS NULL OR StartDate<=GETDATE())
                  AND (EndDate IS NULL OR EndDate>=GETDATE())
                  AND (ISNULL(Quantity,0)<=0 OR ISNULL(UsedCount,0)<Quantity)
                ORDER BY VariantDiscountID DESC
                """, variant.get("variant_id"), id);
            for (Map<String, Object> offer : offers) {
                BigDecimal value = offer.get("discount_value") instanceof BigDecimal v ? v : BigDecimal.ZERO;
                BigDecimal candidate = base;
                String type = String.valueOf(offer.getOrDefault("discount_type", ""));
                if (type.toLowerCase(java.util.Locale.ROOT).contains("cố định") || type.toLowerCase(java.util.Locale.ROOT).contains("fixed")) {
                    if (value.signum() > 0 && value.compareTo(base) < 0) candidate = value;
                } else {
                    BigDecimal reduction = base.multiply(value.max(BigDecimal.ZERO).min(new BigDecimal("100")))
                        .divide(new BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP);
                    Object rawCap = offer.get("max_discount");
                    BigDecimal cap = rawCap instanceof BigDecimal c ? c : BigDecimal.ZERO;
                    if (cap.signum() > 0) reduction = reduction.min(cap);
                    candidate = base.subtract(reduction).max(BigDecimal.ZERO);
                }
                int offerId = ((Number) offer.get("id")).intValue();
                if (candidate.compareTo(bestPrice) < 0 || (candidate.compareTo(bestPrice) == 0 && bestDiscountId != null && offerId > bestDiscountId)) {
                    bestPrice = candidate;
                    bestDiscountId = offerId;
                }
            }
            if (bestDiscountId != null && bestPrice.compareTo(base) < 0) {
                variant.put("old_price", base);
                variant.put("sale_price", bestPrice);
                variant.put("applied_discount_id", bestDiscountId);
                if (bestProductVariantSale == null || bestPrice.compareTo(bestProductVariantSale) < 0) {
                    bestProductVariantSale = bestPrice;
                    bestProductVariantOffer = variant;
                }
            } else {
                variant.put("old_price", null);
                variant.put("sale_price", bestPrice.compareTo(base) < 0 ? bestPrice : null);
                variant.put("applied_discount_id", null);
            }
        }
        // Product grid uses one representative price; detail/POS still use the selected exact variant.
        if (bestProductVariantOffer != null) {
            product.put("base_price", bestProductVariantOffer.get("old_price"));
            product.put("price", bestProductVariantOffer.get("sale_price"));
            product.put("old_price", bestProductVariantOffer.get("old_price"));
            product.put("sale_price", bestProductVariantOffer.get("sale_price"));
        }

        List<Object> sizes = new ArrayList<>();
        Map<String, Map<String, Object>> colors = new LinkedHashMap<>();
        for (Map<String, Object> variant : variants) {
            Object size = variant.get("size");
            if (size != null && !String.valueOf(size).isBlank()
                && sizes.stream().noneMatch(x -> String.valueOf(x).equals(String.valueOf(size)))) {
                sizes.add(size);
            }
            Object rawColor = variant.get("color");
            String colorName = rawColor == null ? "" : String.valueOf(rawColor).trim();
            if (colorName.isBlank()) continue;
            if (!colors.containsKey(colorName)) {
                Map<String, Object> color = new LinkedHashMap<>();
                color.put("name", colorName);
                color.put("hex", variant.getOrDefault("hex", "#d4d4d4"));
                color.put("image", variant.getOrDefault("image_url", product.getOrDefault("image_url", "")));
                colors.put(colorName, color);
            }
        }

        // ProductImages stores a distinct URL per color. Keep the first ordered URL per color,
        // not the first product cover, so choosing another color switches the storefront image.
        List<Map<String, Object>> images = jdbc.queryForList("""
            SELECT ImageURL image, ColorName color, IsPrimary is_primary
            FROM dbo.ProductImages
            WHERE ProductID=?
            ORDER BY CASE WHEN NULLIF(LTRIM(RTRIM(ColorName)), '') IS NULL THEN 1 ELSE 0 END,
                     ISNULL(IsPrimary,0) DESC, ISNULL(SortOrder,0), ProductImageID
            """, id);
        Map<String, String> imageByColor = new LinkedHashMap<>();
        for (Map<String, Object> image : images) {
            Object rawColor = image.get("color");
            String colorName = rawColor == null ? "" : String.valueOf(rawColor).trim();
            Object rawImage = image.get("image");
            String imageUrl = rawImage == null ? "" : String.valueOf(rawImage).trim();
            if (colorName.isBlank() || imageUrl.isBlank()) continue;
            imageByColor.putIfAbsent(colorName, imageUrl);
        }
        for (Map.Entry<String, Map<String, Object>> entry : colors.entrySet()) {
            String colorImage = imageByColor.get(entry.getKey());
            if (colorImage != null && !colorImage.isBlank()) entry.getValue().put("image", colorImage);
        }
        for (Map<String, Object> variant : variants) {
            Object rawColor = variant.get("color");
            String colorName = rawColor == null ? "" : String.valueOf(rawColor).trim();
            String colorImage = imageByColor.get(colorName);
            if (colorImage != null && !colorImage.isBlank()) variant.put("image_url", colorImage);
            else if (variant.get("image_url") == null) variant.put("image_url", product.getOrDefault("image_url", ""));
        }

        product.put("sizes", sizes);
        product.put("variants", variants);
        product.put("colors", new ArrayList<>(colors.values()));
        product.put("images", images);
        product.put("image", product.get("image_url"));
        product.put("badge", Boolean.TRUE.equals(product.get("is_featured")) ? "NỔI BẬT" : "");
    }

}
