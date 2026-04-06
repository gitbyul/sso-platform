package com.gitbyul.shared.exception;

/**
 * DB/MessageSource 번역 키 규약: {@code error.{@link ErrorCode#code()}}.
 */
public final class I18nMessageKeys {

    private I18nMessageKeys() {
    }

    public static String error(ErrorCode errorCode) {
        return "error." + errorCode.code();
    }
}
