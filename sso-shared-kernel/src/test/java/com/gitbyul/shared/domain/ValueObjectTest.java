package com.gitbyul.shared.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValueObjectTest {

    @Test
    void sameComponents_areEqual() {
        Money a = new Money(10, "KRW");
        Money b = new Money(10, "KRW");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void differentComponents_areNotEqual() {
        assertThat(new Money(10, "KRW")).isNotEqualTo(new Money(11, "KRW"));
        assertThat(new Money(10, "KRW")).isNotEqualTo(new Money(10, "USD"));
    }

    private static final class Money extends ValueObject {
        private final int amount;
        private final String currency;

        Money(int amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }

        @Override
        protected Object[] getEqualityComponents() {
            return new Object[] { amount, currency };
        }
    }
}
