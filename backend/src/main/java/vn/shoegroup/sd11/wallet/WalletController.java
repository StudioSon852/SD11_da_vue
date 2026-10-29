package vn.shoegroup.sd11.wallet;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.shoegroup.sd11.auth.JwtPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    private final JdbcTemplate jdbc;
    public WalletController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    @Transactional
    public Map<String, Object> wallet() {
        JwtPrincipal user = currentUser();
        long walletId = ensureWallet(user.id());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("walletId", walletId);
        result.put("balance", balance(walletId));
        result.put("currency", "VND");
        return result;
    }

    @GetMapping("/transactions")
    @Transactional
    public Map<String, Object> transactions() {
        JwtPrincipal user = currentUser();
        ensureWallet(user.id());
        List<Map<String, Object>> ledger = jdbc.queryForList("""
            SELECT WalletTransactionID id, TransactionType type, Amount amount,
                   BalanceAfter balance_after, Description description, CreatedAt created_at
            FROM dbo.ShoeGroupWalletTransactions WHERE UserID=? ORDER BY CreatedAt DESC, WalletTransactionID DESC
            """, user.id());
        List<Map<String, Object>> withdrawals = jdbc.queryForList("""
            SELECT WithdrawalID id, Amount amount, Method method, Destination destination,
                   HolderName holder_name, Status status, Note note, CreatedAt created_at, ProcessedAt processed_at
            FROM dbo.ShoeGroupWalletWithdrawals WHERE UserID=? ORDER BY CreatedAt DESC, WithdrawalID DESC
            """, user.id());
        return Map.of("success", true, "transactions", ledger, "withdrawals", withdrawals);
    }

    @PostMapping("/withdrawals")
    @Transactional
    public Map<String, Object> withdraw(@RequestBody Map<String, Object> body) {
        JwtPrincipal user = currentUser();
        String method = text(body.get("method")).toUpperCase(Locale.ROOT);
        String destination = text(body.get("destination")).replaceAll("\\s+", "");
        String holderName = text(body.get("holder_name"));
        BigDecimal amount;
        try { amount = new BigDecimal(String.valueOf(body.get("amount"))); }
        catch (Exception ex) { throw bad("Số tiền rút không hợp lệ."); }
        if (amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("1000000000")) > 0)
            throw bad("Số tiền rút phải lớn hơn 0 và không vượt quá 1 tỷ đồng.");
        if (!method.equals("VISA") && !method.equals("MOMO")) throw bad("Phương thức rút tiền không được hỗ trợ.");
        if (method.equals("VISA")) {
            if (!destination.matches("4[0-9]{12,18}") || !luhnValid(destination)) throw bad("Số thẻ Visa không hợp lệ.");
            if (holderName.isBlank() || holderName.length() > 120) throw bad("Vui lòng nhập tên chủ thẻ Visa.");
        } else if (!destination.matches("0(?:3|5|7|8|9)[0-9]{8}")) {
            throw bad("Số điện thoại MoMo không hợp lệ.");
        }

        long walletId = ensureWallet(user.id());
        BigDecimal current = jdbc.queryForObject("SELECT Balance FROM dbo.ShoeGroupWallets WITH (UPDLOCK, ROWLOCK) WHERE WalletID=?", BigDecimal.class, walletId);
        if (current == null) current = BigDecimal.ZERO;
        if (current.compareTo(amount) < 0) throw bad("Số dư ví không đủ để thực hiện giao dịch.");
        BigDecimal after = current.subtract(amount);
        jdbc.update("UPDATE dbo.ShoeGroupWallets SET Balance=?,UpdatedAt=GETDATE() WHERE WalletID=?", after, walletId);
        jdbc.update("""
            INSERT INTO dbo.ShoeGroupWalletTransactions(UserID,ReturnID,TransactionType,Amount,BalanceAfter,Description,CreatedAt)
            VALUES(?,NULL,'WITHDRAWAL',?,?,?,GETDATE())
            """, user.id(), amount.negate(), after,
            "Yêu cầu rút tiền " + amount.toPlainString() + " VND về " + (method.equals("VISA") ? "thẻ Visa" : "ví MoMo"));
        Long withdrawalId = jdbc.queryForObject("""
            INSERT INTO dbo.ShoeGroupWalletWithdrawals(UserID,Amount,Method,Destination,HolderName,Status,Note,CreatedAt)
            OUTPUT INSERTED.WithdrawalID VALUES(?,?,?,?,?,N'Chờ xử lý',NULL,GETDATE())
            """, Long.class, user.id(), amount, method, destination, holderName.isBlank() ? null : holderName);
        return Map.of("success", true, "id", withdrawalId, "balance", after, "status", "Chờ xử lý");
    }

    private long ensureWallet(long userId) {
        List<Long> ids = jdbc.queryForList("SELECT TOP 1 WalletID FROM dbo.ShoeGroupWallets WITH (UPDLOCK, ROWLOCK) WHERE UserID=? ORDER BY WalletID", Long.class, userId);
        if (!ids.isEmpty()) return ids.get(0);
        jdbc.update("INSERT INTO dbo.ShoeGroupWallets(UserID,Balance,CreatedAt,UpdatedAt) VALUES(?,0,GETDATE(),GETDATE())", userId);
        return jdbc.queryForObject("SELECT TOP 1 WalletID FROM dbo.ShoeGroupWallets WHERE UserID=? ORDER BY WalletID DESC", Long.class, userId);
    }

    private BigDecimal balance(long walletId) {
        BigDecimal value = jdbc.queryForObject("SELECT Balance FROM dbo.ShoeGroupWallets WHERE WalletID=?", BigDecimal.class, walletId);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static JwtPrincipal currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof JwtPrincipal user))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        return user;
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static boolean luhnValid(String digits) {
        int sum = 0; boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (doubleDigit) { n *= 2; if (n > 9) n -= 9; }
            sum += n; doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
