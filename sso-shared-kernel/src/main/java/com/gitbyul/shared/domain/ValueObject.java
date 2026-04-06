package com.gitbyul.shared.domain;

import java.util.Arrays;
import java.util.Objects;

/**
 * Value Object의 공통 기반.
 * <p>
 * {@link #getEqualityComponents()} 가 같은 경우에만 동일하다고 본다.
 */
public abstract class ValueObject {

    /**
     * equals/hashCode에 사용할 구성요소 목록.
     */
    protected abstract Object[] getEqualityComponents();

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ValueObject that = (ValueObject) o;
        return Arrays.equals(getEqualityComponents(), that.getEqualityComponents());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass(), Arrays.hashCode(getEqualityComponents()));
    }
}

