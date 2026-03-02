package com.tolox.notificationpreferenceservice.context;

import com.tolox.notificationpreferenceservice.model.NotificationType;
import com.tolox.notificationpreferenceservice.repository.NotificationTypeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationTypeCache}.
 * Verifies L1 (Memory) and DB fallback logic.
 */
@ExtendWith(MockitoExtension.class)
class NotificationTypeCacheTest {

    @Mock
    private NotificationTypeRepository repository;

    @InjectMocks
    private NotificationTypeCache cache;

    @Test
    @DisplayName("Should return from DB on cache MISS and then HIT on second call")
    void get_WhenCacheMiss_ShouldFetchFromDbAndThenHitCache() {
        // GIVEN
        String code = "LOGIN_OTP";
        NotificationType type = new NotificationType();
        type.setCode(code);
        
        // Mock DB behavior
        when(repository.findByCode(code)).thenReturn(Mono.just(type));

        // WHEN: First call (Case: MISS)
        StepVerifier.create(cache.get(code))
                .expectNext(type)
                .verifyComplete();

        // THEN: Verify DB was queried
        verify(repository, times(1)).findByCode(code);

        // WHEN: Second call (Case: HIT)
        StepVerifier.create(cache.get(code))
                .expectNext(type)
                .verifyComplete();

        // THEN: DB should NOT be queried a second time
        verifyNoMoreInteractions(repository);
    }
}
