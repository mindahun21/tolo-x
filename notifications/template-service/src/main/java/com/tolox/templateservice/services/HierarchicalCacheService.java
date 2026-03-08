package com.tolox.templateservice.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class HierarchicalCacheService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper; 

    // L1 Cache: In-memory (Caffeine)
    private final Cache<String, Object> l1Cache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES) 
            .maximumSize(1000)
            .build();

    /**
     * Get from cache using L1 -> L2 -> DB pattern
     */
    public <T> Mono<T> getOrFetch(String key, Class<T> clazz, Supplier<Mono<T>> dbFetcher) {
        // 1. Try L1 (Local In-Memory)
        Object l1Value = l1Cache.getIfPresent(key);
        if (l1Value != null) {
            log.info("🚀 L1 CACHE HIT: key={}", key);
            return Mono.just(objectMapper.convertValue(l1Value, clazz));
        }

        // 2. Try L2 (Distributed Redis)
        return redisTemplate.opsForValue().get(key)
                .map(val -> {
                    log.info("☁️ L2 CACHE HIT: key={}", key);
                    T typedVal = objectMapper.convertValue(val, clazz);
                    l1Cache.put(key, typedVal); // Populate back to L1
                    return typedVal;
                })
                .switchIfEmpty(
                        // 3. Fallback to DB
                        dbFetcher.get()
                                .doOnNext(val -> {
                                    log.info("📂 CACHE MISS (DB Fetch): key={}", key);
                                    // Populate L2 (Redis)
                                    redisTemplate.opsForValue().set(key, val, Duration.ofHours(1)).subscribe();
                                    // Populate L1 (Local)
                                    l1Cache.put(key, val);
                                })
                );
    }

    /**
     * Evict from both caches
     */
    public Mono<Void> evict(String key) {
        log.info("🔥 EVICTING CACHE: key={}", key);
        l1Cache.invalidate(key);
        return redisTemplate.opsForValue().delete(key).then();
    }
}
