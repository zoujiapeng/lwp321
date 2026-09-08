package cn.edu.talk.common;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class Errors {
    private static final Logger log = LoggerFactory.getLogger(Errors.class);
    private ResponseEntity<?> error(int code, String text) {
        return ResponseEntity.status(code).body(Map.of("message", text, "status", code));
    }
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> business(ApiException ex) { return error(ex.status, ex.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        var first = ex.getBindingResult().getFieldErrors().get(0);
        return error(400, first.getField() + ": " + first.getDefaultMessage());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> format(Exception ex) { return error(400, "请求格式不正确，请检查输入内容"); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> large(Exception ex) { return error(400, "文件不能超过 2MB"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> conflict(Exception ex) { return error(409, "账号或学号重复，或该数据存在关联记录"); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ex) {
        // Do not put request bodies, passwords or conversation contents in logs.
        log.error("Request failed: {}", ex.getClass().getSimpleName());
        return error(500, "服务暂时无法完成操作，请重试或联系管理员");
    }
}
