package com.gitbyul.shared.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SsoDomainExceptionTest {

    @Test
    void carriesErrorCode() {
        SsoDomainException ex = new SsoDomainException(ErrorCode.TENANT_NOT_IDENTIFIED);
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.TENANT_NOT_IDENTIFIED);
    }
}
