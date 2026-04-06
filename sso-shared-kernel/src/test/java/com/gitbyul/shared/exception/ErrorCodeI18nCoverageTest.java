package com.gitbyul.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ErrorCodeI18nCoverageTest {

    @Test
    void everyErrorCodeHasClasspathFallbackMessage() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/messages.properties")) {
            assertThat(in).isNotNull();
            properties.load(in);
        }
        for (ErrorCode ec : ErrorCode.values()) {
            String key = I18nMessageKeys.error(ec);
            assertThat(properties.getProperty(key))
                    .as("Missing fallback for key %s", key)
                    .isNotBlank();
        }
    }

    @Test
    void i18nKeyMatchesErrorCodeCode() {
        assertThat(I18nMessageKeys.error(ErrorCode.TENANT_NOT_IDENTIFIED))
                .isEqualTo("error.TENANT_NOT_IDENTIFIED");
        assertThat(I18nMessageKeys.error(ErrorCode.TENANT_MISMATCH))
                .isEqualTo("error.SECURITY_TENANT_MISMATCH");
    }
}
