package com.gitbyul.shared.util;

import java.time.Instant;

/**
 * 현재 시각을 주입 가능하게 만들기 위한 추상화.
 * <p>
 * 구현: {@link SystemTimeProvider}, {@link FixedTimeProvider}
 */
public interface TimeProvider {

    Instant now();
}
