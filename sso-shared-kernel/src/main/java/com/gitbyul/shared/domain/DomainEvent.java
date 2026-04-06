package com.gitbyul.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 도메인 이벤트 마커 인터페이스.
 * <p>
 * 기본 구현을 제공하되(occurredAt), 이벤트별로 {@link #eventId()} 를 반드시 정의한다.
 */
public interface DomainEvent {

    /**
     * 이벤트 식별자.
     */
    UUID eventId();

    /**
     * 이벤트 발생 시각 (기본: 현재 시각).
     */
    default Instant occurredAt() {
        return Instant.now();
    }
}