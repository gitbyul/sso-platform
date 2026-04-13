package com.gitbyul.shared.testsupport;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Spring {@link ObjectProvider}에는 테스트 편의용 정적 empty()/of()가 없어
 * 필터·설정 테스트에서 동일한 익명 구현이 반복되던 부분을 한곳으로 모은다.
 */
public final class TestObjectProviders {

    private TestObjectProviders() {
    }

    /**
     * {@link ObjectProvider#getIfAvailable()}가 항상 {@code null}인 제공자.
     * (빈 미등록과 동일하게 필터 폴백 경로를 태운다.)
     */
    public static <T> ObjectProvider<T> emptyObjectProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getIfAvailable() throws BeansException {
                return null;
            }
        };
    }

    /**
     * 단일 인스턴스를 노출하는 {@link ObjectProvider}.
     */
    public static <T> ObjectProvider<T> objectProviderOf(T bean) {
        return new ObjectProvider<>() {
            @Override
            public T getIfAvailable() throws BeansException {
                return bean;
            }
        };
    }
}
