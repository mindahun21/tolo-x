package com.tolox.templateservice.services;

import com.tolox.templateservice.dto.RenderRequest;
import com.tolox.templateservice.dto.RenderResponse;
import com.tolox.templateservice.engine.TemplateRenderer;
import com.tolox.templateservice.enums.TemplateEngineEnum;
import com.tolox.templateservice.model.Template;
import com.tolox.templateservice.model.TemplateVersion;
import com.tolox.templateservice.repositories.TemplateRepository;
import com.tolox.templateservice.repositories.TemplateVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RenderingService {

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository versionRepository;
    private final HierarchicalCacheService cacheService;
    private final AssetService assetService; // New: Global Assets
    private final Map<TemplateEngineEnum, TemplateRenderer> renderers;

    public RenderingService(TemplateRepository templateRepository, 
                            TemplateVersionRepository versionRepository, 
                            HierarchicalCacheService cacheService,
                            AssetService assetService,
                            List<TemplateRenderer> rendererList) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.cacheService = cacheService;
        this.assetService = assetService;
        this.renderers = rendererList.stream()
                .collect(Collectors.toMap(TemplateRenderer::getEngineType, Function.identity()));
    }

    /**
     * Public Render API (uses ACTIVE version and Cache)
     */
    public Mono<RenderResponse> render(RenderRequest request) {
        log.info("Received render request for template: {} and app: {}", request.templateCode(), request.applicationCode());
        
        String templateKey = "template:" + request.applicationCode() + ":" + request.templateCode();
        return cacheService.getOrFetch(templateKey, Template.class, 
                () -> templateRepository.findByApplicationCodeAndTemplateCode(request.applicationCode(), request.templateCode()))
                .switchIfEmpty(Mono.error(new RuntimeException("Template not found: " + request.templateCode())))
                .flatMap(template -> {
                    if (template.activeVersionNumber() == null) {
                        return Mono.error(new RuntimeException("No active version for template"));
                    }
                    
                    List<String> localesToTry = getFallbackLocales(request.locale());
                    return Flux.fromIterable(localesToTry)
                            .concatMap(loc -> {
                                String versionKey = "version:" + template.id() + ":" + template.activeVersionNumber() + ":" + request.channel() + ":" + loc;
                                return cacheService.getOrFetch(versionKey, TemplateVersion.class,
                                        () -> versionRepository.findByTemplateIdAndVersionNumberAndChannelAndLocale(
                                                template.id(), 
                                                template.activeVersionNumber(), 
                                                request.channel(), 
                                                loc
                                        ));
                            })
                            .next(); 
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Template version not found after fallback sequence")))
                .flatMap(version -> renderVersion(version, request.data()));
    }

    /**
     * Admin Preview API
     */
    public Mono<RenderResponse> preview(UUID templateId, Integer versionNumber, RenderRequest request) {
        log.info("Previewing version {} for template {}", versionNumber, templateId);
        
        return versionRepository.findByTemplateId(templateId)
                .filter(v -> v.versionNumber().equals(versionNumber))
                .filter(v -> v.channel().equals(request.channel()))
                .filter(v -> v.locale().equals(request.locale()))
                .next()
                .switchIfEmpty(Mono.error(new RuntimeException("Specific template version not found for preview")))
                .flatMap(version -> renderVersion(version, request.data()));
    }

    /**
     * Core rendering logic with Asset Injection
     */
    private Mono<RenderResponse> renderVersion(TemplateVersion version, Map<String, Object> userData) {
        return assetService.getAssetRegistry()
                .flatMap(assetRegistry -> Mono.fromCallable(() -> {
                    TemplateRenderer renderer = renderers.get(version.engine());
                    if (renderer == null) {
                        throw new RuntimeException("No renderer found for engine: " + version.engine());
                    }

                    // 🛠️ Asset Injection: Merge user data and global assets registry
                    Map<String, Object> finalData = new HashMap<>();
                    if (userData != null) finalData.putAll(userData);
                    finalData.put("assets", assetRegistry); // Automatically adds "assets" map

                    log.debug("Rendering version {} with engine {}", version.versionNumber(), version.engine());
                    String renderedSubject = version.subject() != null ? 
                            renderer.render(version.subject(), finalData) : null;
                    String renderedBody = renderer.render(version.body(), finalData);

                    return new RenderResponse(
                            renderedSubject,
                            renderedBody,
                            version.versionNumber(),
                            version.locale(),
                            Instant.now()
                    );
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private List<String> getFallbackLocales(String requestedLocale) {
        List<String> fallbacks = new ArrayList<>();
        if (requestedLocale == null || requestedLocale.isBlank()) {
            fallbacks.add("en");
            return fallbacks;
        }
        fallbacks.add(requestedLocale);
        if (requestedLocale.contains("-") || requestedLocale.contains("_")) {
            String baseLocale = requestedLocale.split("[-_]")[0];
            if (!baseLocale.equals(requestedLocale)) {
                fallbacks.add(baseLocale);
            }
        }
        if (!fallbacks.contains("en")) {
            fallbacks.add("en");
        }
        return fallbacks;
    }
}
