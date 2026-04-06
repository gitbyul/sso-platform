package com.gitbyul.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CaffeineCachingLocalizedMessageRepositoryTest {

    @Test
    void delegatesOncePerCacheKey() {
        AtomicInteger calls = new AtomicInteger();
        LocalizedMessageRepository delegate = Mockito.mock(LocalizedMessageRepository.class);
        when(delegate.findMessage(eq("k"), eq(Locale.ENGLISH), eq(null)))
                .thenAnswer(
                        inv -> {
                            calls.incrementAndGet();
                            return Optional.of("x");
                        });

        CaffeineCachingLocalizedMessageRepository cache =
                new CaffeineCachingLocalizedMessageRepository(delegate);

        assertThat(cache.findMessage("k", Locale.ENGLISH, null)).contains("x");
        assertThat(cache.findMessage("k", Locale.ENGLISH, null)).contains("x");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void tenantScopedKeysDiffer() {
        LocalizedMessageRepository delegate = Mockito.mock(LocalizedMessageRepository.class);
        when(delegate.findMessage(eq("k"), eq(Locale.ENGLISH), eq("t1")))
                .thenReturn(Optional.of("a"));
        when(delegate.findMessage(eq("k"), eq(Locale.ENGLISH), eq("t2")))
                .thenReturn(Optional.of("b"));

        CaffeineCachingLocalizedMessageRepository cache =
                new CaffeineCachingLocalizedMessageRepository(delegate);

        assertThat(cache.findMessage("k", Locale.ENGLISH, "t1")).contains("a");
        assertThat(cache.findMessage("k", Locale.ENGLISH, "t2")).contains("b");
        verify(delegate, times(1)).findMessage(eq("k"), eq(Locale.ENGLISH), eq("t1"));
        verify(delegate, times(1)).findMessage(eq("k"), eq(Locale.ENGLISH), eq("t2"));
    }
}
