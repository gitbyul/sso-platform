package com.gitbyul.shared.exception;

/**
 * SSO 공통 도메인 예외. {@link Throwable#getMessage()}는 로그·디버그용(코드 문자열)이며 UI 언어와 무관하다.
 */
public class SsoDomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public SsoDomainException(ErrorCode errorCode) {
        super(errorCode.code());
        this.errorCode = errorCode;
    }

    public SsoDomainException(ErrorCode errorCode, String detail) {
        super(detail == null || detail.isBlank() ? errorCode.code() : detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
