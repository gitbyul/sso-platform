package com.gitbyul.shared.web.error;

import com.gitbyul.shared.exception.ErrorCode;
import com.gitbyul.shared.exception.I18nMessageKeys;
import com.gitbyul.shared.exception.SsoDomainException;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class SsoRestExceptionHandlers {

    private static final Logger log = LoggerFactory.getLogger(SsoRestExceptionHandlers.class);

    private final MessageSource messageSource;

    public SsoRestExceptionHandlers(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(SsoDomainException.class)
    public ResponseEntity<ApiErrorResponse> handleSsoDomain(SsoDomainException ex, Locale locale) {
        ErrorCode ec = ex.errorCode();
        String key = I18nMessageKeys.error(ec);
        String message = messageSource.getMessage(key, null, ec.code(), locale);
        return ResponseEntity.status(ec.httpStatus()).body(new ApiErrorResponse(ec.code(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, Locale locale) {
        ErrorCode ec = ErrorCode.INVALID_ARGUMENT;
        String key = I18nMessageKeys.error(ec);
        String base = messageSource.getMessage(key, null, ec.code(), locale);
        String detail =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .collect(Collectors.joining("; "));
        String message = detail.isEmpty() ? base : base + " (" + detail + ")";
        return ResponseEntity.status(ec.httpStatus()).body(new ApiErrorResponse(ec.code(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception ex, Locale locale) {
        log.error("Unhandled exception", ex);
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        String key = I18nMessageKeys.error(ec);
        String message = messageSource.getMessage(key, null, ec.code(), locale);
        return ResponseEntity.status(ec.httpStatus()).body(new ApiErrorResponse(ec.code(), message));
    }
}
