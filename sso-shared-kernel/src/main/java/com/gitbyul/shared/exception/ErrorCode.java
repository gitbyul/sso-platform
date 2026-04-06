package com.gitbyul.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * 공통 예외 코드·HTTP 상태. 사용자 노출 문구는 DB/MessageSource 키 {@link I18nMessageKeys#error(ErrorCode)}.
 */
public enum ErrorCode {
    TENANT_NOT_IDENTIFIED("TENANT_NOT_IDENTIFIED", HttpStatus.BAD_REQUEST),
    TENANT_MISMATCH("SECURITY_TENANT_MISMATCH", HttpStatus.FORBIDDEN),
    INVALID_ARGUMENT("INVALID_ARGUMENT", HttpStatus.BAD_REQUEST),
    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus httpStatus;

    ErrorCode(String code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
