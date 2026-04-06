package com.gitbyul.shared.util;

import java.time.Instant;

/**
 * 운영 환경용 {@link TimeProvider} — 시스템 시각.
 */
public final class SystemTimeProvider implements TimeProvider {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
