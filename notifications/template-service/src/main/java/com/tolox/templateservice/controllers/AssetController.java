package com.tolox.templateservice.controllers;

import com.tolox.templateservice.model.Asset;
import com.tolox.templateservice.services.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/notification-template/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Asset> createAsset(@RequestBody Asset asset) {
        return assetService.createAsset(asset);
    }

    @GetMapping
    public Flux<Asset> getAllAssets() {
        return assetService.getAllAssets();
    }

    @PutMapping("/{id}")
    public Mono<Asset> updateAsset(@PathVariable UUID id, @RequestBody Asset asset) {
        return assetService.updateAsset(id, asset);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteAsset(@PathVariable UUID id) {
        return assetService.deleteAsset(id);
    }
}
