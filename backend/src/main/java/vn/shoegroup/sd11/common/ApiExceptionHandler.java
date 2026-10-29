package vn.shoegroup.sd11.common;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        return response(ex.getStatusCode().value(), ex.getReason() == null ? "Yêu cầu không hợp lệ." : ex.getReason());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDatabase(DataAccessException ex) {
        DatabaseDiagnostics.log("API database request failed", ex);
        String message = DatabaseDiagnostics.userMessage(ex);
        return response(message.startsWith("Đã vào được database") ? 500 : 503, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        System.err.println("[API-ERROR] " + ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "no detail" : ex.getMessage()));
        return response(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Máy chủ gặp lỗi không mong muốn. Hãy xem log [API-ERROR] trong terminal backend.");
    }

    private ResponseEntity<Map<String, Object>> response(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
