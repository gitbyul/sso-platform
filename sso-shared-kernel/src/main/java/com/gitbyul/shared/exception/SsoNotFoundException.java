package com.gitbyul.shared.exception;

/**
 * 리소스 미존재 예외.
 */
public class SsoNotFoundException extends SsoDomainException {

    public SsoNotFoundException(String detail) {
        super(ErrorCode.NOT_FOUND, detail);
    }

    public SsoNotFoundException() {
        super(ErrorCode.NOT_FOUND);
    }
}

