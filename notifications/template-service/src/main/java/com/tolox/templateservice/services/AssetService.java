package com.tolox.templateservice.services;

import com.tolox.templateservice.model.Asset;
import com.tolox.templateservice.repositories.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final HierarchicalCacheService cacheService;
    
    private static final String ASSETS_CACHE_KEY = "assets:registry";

    /**
     * Get the global asset registry as a map (Cached)
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, String>> getAssetRegistry() {
        // Use Map.class as the target type for the cache.
        // This solves the 'HashMap lower bounds' and generic inference errors.
        return cacheService.getOrFetch(ASSETS_CACHE_KEY, (Class<Map>) (Class) Map.class, 
                () -> assetRepository.findAll()
                        .collectList()
                        .map(assets -> {
                            Map<String, String> map = new HashMap<>(); // Standard HashMap
                            for (Asset asset : assets) {
                                map.put(asset.assetKey(), asset.assetUrl());
                            }
                            return map;
                        }))
                .map(res -> (Map<String, String>) res);
    }

    /**
     * CRUD: Create an Asset
     */
    public Mono<Asset> createAsset(Asset asset) {
        Asset toSave = new Asset(
                UUID.randomUUID(),
                asset.assetKey(),
                asset.assetUrl(),
                asset.description(),
                null, 
                null
        );
        return assetRepository.save(toSave)
                .flatMap(saved -> cacheService.evict(ASSETS_CACHE_KEY).thenReturn(saved));
    }

    /**
     * CRUD: List all Assets
     */
    public Flux<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    /**
     * CRUD: Update an Asset
     */
    public Mono<Asset> updateAsset(UUID id, Asset request) {
        return assetRepository.findById(id)
                .flatMap(existing -> {
                    Asset updated = new Asset(
                            existing.id(),
                            request.assetKey(),
                            request.assetUrl(),
                            request.description(),
                            existing.createdAt(),
                            null
                    );
                    return assetRepository.save(updated);
                })
                .flatMap(saved -> cacheService.evict(ASSETS_CACHE_KEY).thenReturn(saved));
    }

    /**
     * CRUD: Delete an Asset
     */
    public Mono<Void> deleteAsset(UUID id) {
        return assetRepository.deleteById(id)
                .then(cacheService.evict(ASSETS_CACHE_KEY));
    }
}
