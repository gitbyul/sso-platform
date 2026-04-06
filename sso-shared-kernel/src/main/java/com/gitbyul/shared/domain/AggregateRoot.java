package com.gitbyul.shared.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 모든 도메인 Aggregate Root의 공통 기반.
 * <p>
 * 도메인 이벤트를 내부에 누적했다가(미발행 상태) 필요한 시점에 바깥으로 꺼내갈 수 있게 한다.
 */
public abstract class AggregateRoot {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * 도메인 이벤트를 누적한다.
     */
    protected final void raiseEvent(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        domainEvents.add(event);
    }

    /**
     * 누적된 도메인 이벤트 목록 (읽기 전용).
     */
    public final List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public final boolean hasDomainEvents() {
        return !domainEvents.isEmpty();
    }

    /**
     * 누적된 도메인 이벤트를 비운다.
     */
    public final void clearDomainEvents() {
        domainEvents.clear();
    }
}