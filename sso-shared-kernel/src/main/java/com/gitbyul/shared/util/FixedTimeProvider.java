package com.gitbyul.shared.util;

import java.time.Instant;

/**
 * 테스트용 {@link TimeProvider} — 고정 시각.
 */
public final class FixedTimeProvider implements TimeProvider {

    private final Instant fixedInstant;

    public FixedTimeProvider(Instant fixedInstant) {
        this.fixedInstant = fixedInstant;
    }

    @Override
    public Instant now() {
        return fixedInstant;
    }
}
