package vn.shoegroup.sd11.product;

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

/** CRUD for catalog products and size/color variants. Inventory is not managed by this API. */
@RestController
@RequestMapping("/api/products")
public class ProductAdminController {
    private final JdbcTemplate jdbc;
    public ProductAdminController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping @Transactional
    public Map<String,Object> create(@RequestBody Map<String,Object> body) {
        requireStaff(); validate(body);
        String name=text(body,"name","ProductName");
        Long id=jdbc.queryForObject("""
          INSERT INTO dbo.Products(CategoryID,BrandID,ProductName,BasePrice,IsActive,ImageURL,Description,ImageGallery,CollectionID,ParentSKU,SalePrice,IsFeatured,ViewCount,CreatedAt,UpdatedAt,MaterialID,WishlistCount)
          OUTPUT INSERTED.ProductID VALUES(?,?,?,?,?,?,?,?,?,?,?, ?,0,GETDATE(),GETDATE(),?,0)
          """,Long.class,integerOrNull(body,"category_id","CategoryID"),integerOrNull(body,"brand_id","BrandID"),name,price(body),truthy(first(body,"active","IsActive")),nullable(body,"image","image_url","ImageURL"),nullable(body,"description","Description"),null,integerOrNull(body,"collection_id","CollectionID"),nullable(body,"sku","ParentSKU"),salePrice(body),truthy(first(body,"is_featured","featured","IsFeatured")),integerOrNull(body,"material_id","MaterialID"));
        saveVariants(id,body); saveImageRows(id,body);
        return Map.of("success",true,"id",id,"product_id",id);
    }

    @PutMapping("/{id}") @Transactional
    public Map<String,Object> update(@PathVariable long id,@RequestBody Map<String,Object> body) {
        requireStaff(); validate(body);
        int n=jdbc.update("""
          UPDATE dbo.Products SET CategoryID=?,BrandID=?,ProductName=?,BasePrice=?,IsActive=?,ImageURL=?,Description=?,CollectionID=?,ParentSKU=?,SalePrice=?,IsFeatured=?,UpdatedAt=GETDATE(),MaterialID=? WHERE ProductID=?
          """,integerOrNull(body,"category_id","CategoryID"),integerOrNull(body,"brand_id","BrandID"),text(body,"name","ProductName"),price(body),truthy(first(body,"active","IsActive")),nullable(body,"image","image_url","ImageURL"),nullable(body,"description","Description"),integerOrNull(body,"collection_id","CollectionID"),nullable(body,"sku","ParentSKU"),salePrice(body),truthy(first(body,"is_featured","featured","IsFeatured")),integerOrNull(body,"material_id","MaterialID"),id);
        if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy sản phẩm.");
        saveVariants(id,body); saveImageRows(id,body);
        return Map.of("success",true,"id",id);
    }

    @PutMapping("/{id}/restore") public Map<String,Object> restore(@PathVariable long id) {
        requireStaff(); int n=jdbc.update("UPDATE dbo.Products SET IsActive=1,UpdatedAt=GETDATE() WHERE ProductID=?",id); if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy sản phẩm."); return Map.of("success",true,"id",id);
    }

    @DeleteMapping("/{id}") @Transactional
    public Map<String,Object> delete(@PathVariable long id,@RequestParam(defaultValue="0") int soft) {
        requireStaff();
        if(soft==1) { int n=jdbc.update("UPDATE dbo.Products SET IsActive=0,UpdatedAt=GETDATE() WHERE ProductID=?",id); if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy sản phẩm."); jdbc.update("UPDATE dbo.ProductVariants SET IsActive=0 WHERE ProductID=?",id); return Map.of("success",true,"softDeleted",true); }
        Integer exists=jdbc.queryForObject("SELECT COUNT(1) FROM dbo.Products WHERE ProductID=?",Integer.class,id); if(exists==null||exists==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy sản phẩm.");
        Integer orders=jdbc.queryForObject("SELECT COUNT(1) FROM dbo.OrderDetails WHERE ProductID=?",Integer.class,id);
        if(orders!=null&&orders>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Sản phẩm đã xuất hiện trong đơn hàng. Hãy ẩn sản phẩm thay vì xóa cứng để giữ lịch sử giao dịch.");
        jdbc.update("DELETE FROM dbo.VariantDiscounts WHERE ProductID=?",id);
        jdbc.update("DELETE FROM dbo.ProductImages WHERE ProductID=?",id);
        jdbc.update("DELETE FROM dbo.ProductVariants WHERE ProductID=?",id);
        jdbc.update("DELETE FROM dbo.Products WHERE ProductID=?",id);
        return Map.of("success",true,"id",id);
    }

    private void saveVariants(long productId,Map<String,Object> body) {
        Object raw=body.get("variants"); if(!(raw instanceof List<?> list)) return;
        // Leave historical IDs intact; hide missing variants instead of deleting any variant referenced by an older order.
        jdbc.update("UPDATE dbo.ProductVariants SET IsActive=0 WHERE ProductID=?",productId);
        Set<Long> used=new HashSet<>();
        for(Object item:list) {
            if(!(item instanceof Map<?,?> m)) continue;
            Map<String,Object> v=new HashMap<>();m.forEach((k,val)->v.put(String.valueOf(k),val));
            String size=text(v,"size","Size"), color=text(v,"color","ColorName");
            if(size.isBlank()) continue;
            String hex=strOr(v,"hex","color_hex","ColorHex","#cccccc");
            String sku=strOr(v,"sku","ChildSKU","sku","SP-"+productId+"-"+size);
            Long variantId=longOrNull(first(v,"id","variant_id","ProductVariantID"));
            if(variantId!=null && variantId>0 && used.add(variantId)) {
                int n=jdbc.update("UPDATE dbo.ProductVariants SET Size=?,ColorName=?,ColorHex=?,ChildSKU=?,PriceAdjustment=0,IsActive=1,Version=Version+1 WHERE ProductVariantID=? AND ProductID=?",size,color,hex,sku,variantId,productId);
                if(n>0) continue;
            }
            jdbc.update("INSERT INTO dbo.ProductVariants(ProductID,Size,SizeStandard,ColorName,ColorHex,ChildSKU,StockQuantity,PriceAdjustment,IsActive,Version) VALUES(?,?, 'EU',?,?,?,0,0,1,1)",productId,size,color,hex,sku);
        }
    }

    private void saveImageRows(long id,Map<String,Object> body) {
        jdbc.update("DELETE FROM dbo.ProductImages WHERE ProductID=?",id);
        String image=text(body,"image","image_url","ImageURL");
        if(!image.isBlank()) jdbc.update("INSERT INTO dbo.ProductImages(ProductID,ColorName,ImageURL,IsPrimary,SortOrder) VALUES(?,NULL,?,1,0)",id,image);
        Object raw=body.get("images"); if(raw instanceof List<?> l) { int sort=1; for(Object x:l) { if(x instanceof Map<?,?> m) { Object imageValue=m.get("image"); if(imageValue==null) imageValue=m.get("ImageURL"); String url=imageValue==null?"":String.valueOf(imageValue); if(!url.isBlank()) jdbc.update("INSERT INTO dbo.ProductImages(ProductID,ColorName,ImageURL,IsPrimary,SortOrder) VALUES(?,?,?,?,?)",id,m.get("color"),url,false,sort++); } else if(x!=null&&!String.valueOf(x).isBlank()) jdbc.update("INSERT INTO dbo.ProductImages(ProductID,ColorName,ImageURL,IsPrimary,SortOrder) VALUES(?,NULL,?,0,?)",id,String.valueOf(x),sort++); } }
    }
    private static void validate(Map<String,Object>b){if(text(b,"name","ProductName").isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tên sản phẩm là bắt buộc."); if(price(b).signum()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Giá sản phẩm không hợp lệ.");}
    private static JwtPrincipal requireStaff(){Authentication a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!(a.getPrincipal() instanceof JwtPrincipal u))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập.");if(u.roleId()!=1&&u.roleId()!=3&&u.roleId()!=4)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền quản lý sản phẩm.");return u;}
    private static Object first(Map<String,Object>b,String...keys){for(String k:keys)if(b.containsKey(k))return b.get(k);return null;}
    private static String text(Map<String,Object>b,String... keys){Object x=first(b,keys);return x==null?"":String.valueOf(x).trim();}
    private static String strOr(Map<String,Object>b,String a,String c,String d,String fallback){Object x=first(b,a,c,d);return x==null||String.valueOf(x).isBlank()?fallback:String.valueOf(x).trim();}
    private static Object nullable(Map<String,Object>b,String...keys){Object x=first(b,keys);return x==null||String.valueOf(x).isBlank()?null:String.valueOf(x).trim();}
    private static BigDecimal price(Map<String,Object>b){Object x=first(b,"price","base_price","BasePrice");try{return x==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(x));}catch(Exception e){return BigDecimal.ZERO;}}
    private static BigDecimal salePrice(Map<String,Object>b){Object x=first(b,"sale_price","SalePrice");try{if(x==null||String.valueOf(x).isBlank()||new BigDecimal(String.valueOf(x)).signum()<=0)return null;return new BigDecimal(String.valueOf(x));}catch(Exception e){return null;}}
    private static Integer integerOrNull(Map<String,Object>b,String a,String c){Object x=first(b,a,c);if(x==null||String.valueOf(x).isBlank())return null;try{return Integer.valueOf(String.valueOf(x));}catch(Exception e){return null;}}
    private static Long longOrNull(Object x){try{return x==null?null:Long.valueOf(String.valueOf(x));}catch(Exception e){return null;}}
    private static boolean truthy(Object x){if(x==null)return false;if(x instanceof Boolean b)return b;if(x instanceof Number n)return n.intValue()!=0;String s=String.valueOf(x).trim();return !(s.isEmpty()||s.equals("0")||s.equalsIgnoreCase("false")||s.equalsIgnoreCase("off"));}
}
