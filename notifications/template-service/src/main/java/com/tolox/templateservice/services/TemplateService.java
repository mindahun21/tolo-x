package com.tolox.templateservice.services;

import com.tolox.templateservice.dto.*;
import com.tolox.templateservice.engine.TemplateRenderer;
import com.tolox.templateservice.enums.ChannelType;
import com.tolox.templateservice.enums.TemplateEngineEnum;
import com.tolox.templateservice.enums.VersionStatus;
import com.tolox.templateservice.model.Template;
import com.tolox.templateservice.model.TemplateVersion;
import com.tolox.templateservice.repositories.TemplateRepository;
import com.tolox.templateservice.repositories.TemplateVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final HierarchicalCacheService cacheService;
    private final Map<TemplateEngineEnum, TemplateRenderer> renderers;

    public TemplateService(TemplateRepository templateRepository,
                           TemplateVersionRepository templateVersionRepository,
                           HierarchicalCacheService cacheService,
                           List<TemplateRenderer> rendererList) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.cacheService = cacheService;
        this.renderers = rendererList.stream()
                .collect(Collectors.toMap(TemplateRenderer::getEngineType, Function.identity()));
    }

    // --- Template CRUD ---

    public Mono<TemplateResponse> createTemplate(TemplateRequest request) {
        log.info("Creating template: {}/{}", request.applicationCode(), request.templateCode());
        Template template = new Template(
                UUID.randomUUID(),
                request.applicationCode(),
                request.templateCode(),
                request.description(),
                null, 
                null, 
                null  
        );
        return templateRepository.save(template)
                .map(this::mapToTemplateResponse);
    }

    public Mono<TemplateResponse> getTemplate(UUID id) {
        return templateRepository.findById(id)
                .map(this::mapToTemplateResponse);
    }

    public Flux<TemplateResponse> getAllTemplates() {
        return templateRepository.findAll()
                .map(this::mapToTemplateResponse);
    }

    public Mono<TemplateResponse> updateTemplate(UUID id, TemplateRequest request) {
        return templateRepository.findById(id)
                .flatMap(existing -> {
                    Template updated = new Template(
                            existing.id(),
                            request.applicationCode(),
                            request.templateCode(),
                            request.description(),
                            existing.activeVersionNumber(),
                            existing.createdAt(),
                            null 
                    );
                    String key = "template:" + existing.applicationCode() + ":" + existing.templateCode();
                    return cacheService.evict(key)
                            .then(templateRepository.save(updated));
                })
                .map(this::mapToTemplateResponse);
    }

    public Mono<Void> deleteTemplate(UUID id) {
        return templateRepository.findById(id)
                .flatMap(template -> {
                    String key = "template:" + template.applicationCode() + ":" + template.templateCode();
                    return cacheService.evict(key)
                            .then(templateRepository.deleteById(id));
                });
    }

    // --- Template Version CRUD ---

    public Mono<TemplateVersionResponse> createVersion(UUID templateId, Integer versionNumber, TemplateVersionRequest request) {
        log.info("Creating version {} for template {}", versionNumber, templateId);
        
        TemplateRenderer renderer = renderers.get(request.engine());
        if (renderer == null) {
            return Mono.error(new RuntimeException("Unsupported engine: " + request.engine()));
        }

        if (request.subject() != null && !renderer.validate(request.subject())) {
            return Mono.error(new RuntimeException("Subject contains invalid " + request.engine() + " syntax"));
        }
        if (!renderer.validate(request.body())) {
            return Mono.error(new RuntimeException("Body contains invalid " + request.engine() + " syntax"));
        }

        TemplateVersion version = new TemplateVersion(
                UUID.randomUUID(),
                templateId,
                versionNumber,
                request.channel(),
                request.locale(),
                request.subject(),
                request.body(),
                request.engine(),
                VersionStatus.DRAFT,
                null 
        );
        return templateVersionRepository.save(version)
                .map(this::mapToVersionResponse);
    }

    public Flux<TemplateVersionResponse> getVersionsForTemplate(UUID templateId) {
        return templateVersionRepository.findByTemplateId(templateId)
                .map(this::mapToVersionResponse);
    }

    public Mono<TemplateResponse> publishVersion(UUID templateId, Integer versionNumber) {
        log.info("Publishing version {} for template {}", versionNumber, templateId);
        return templateRepository.findById(templateId)
                .flatMap(template -> {
                    Template updated = new Template(
                            template.id(),
                            template.applicationCode(),
                            template.templateCode(),
                            template.description(),
                            versionNumber,
                            template.createdAt(),
                            null
                    );
                    String key = "template:" + template.applicationCode() + ":" + template.templateCode();
                    return cacheService.evict(key)
                            .then(templateRepository.save(updated));
                })
                .map(this::mapToTemplateResponse);
    }

    // --- Variable Discovery ---

    public Mono<VariableDiscoveryResponse> discoverVariables(UUID templateId) {
        return templateRepository.findById(templateId)
                .flatMap(template -> {
                    if (template.activeVersionNumber() == null) {
                        return Mono.error(new RuntimeException("No active version for template to discover variables from"));
                    }
                    return discoverVariablesForVersion(templateId, template.activeVersionNumber());
                });
    }

    public Mono<VariableDiscoveryResponse> discoverVariablesForVersion(UUID templateId, Integer versionNumber) {
        return templateVersionRepository.findByTemplateId(templateId)
                .filter(v -> v.versionNumber().equals(versionNumber))
                .collectList()
                .map(versions -> {
                    Map<ChannelType, Set<String>> channelVars = new HashMap<>();
                    Set<String> allVars = new HashSet<>();

                    for (TemplateVersion version : versions) {
                        TemplateRenderer renderer = renderers.get(version.engine());
                        if (renderer != null) {
                            Set<String> vars = new HashSet<>();
                            if (version.subject() != null) {
                                vars.addAll(renderer.extractVariables(version.subject()));
                            }
                            vars.addAll(renderer.extractVariables(version.body()));
                            
                            // 🚀 SECURITY/USABILITY: Filter out 'assets.*' variables.
                            // Downstream services shouldn't provide data for assets 
                            // as they are provided automatically by the AssetRegistry.
                            Set<String> filteredVars = vars.stream()
                                    .filter(v -> !v.startsWith("assets."))
                                    .collect(Collectors.toSet());

                            channelVars.put(version.channel(), filteredVars);
                            allVars.addAll(filteredVars);
                        }
                    }
                    return new VariableDiscoveryResponse(channelVars, allVars);
                });
    }

    // --- Mapping ---

    private TemplateResponse mapToTemplateResponse(Template t) {
        return new TemplateResponse(
                t.id(),
                t.applicationCode(),
                t.templateCode(),
                t.description(),
                t.activeVersionNumber(),
                t.createdAt(),
                t.updatedAt()
        );
    }

    private TemplateVersionResponse mapToVersionResponse(TemplateVersion v) {
        return new TemplateVersionResponse(
                v.id(),
                v.templateId(),
                v.versionNumber(),
                v.channel(),
                v.locale(),
                v.subject(),
                v.body(),
                v.engine(),
                v.status(),
                v.createdAt()
        );
    }
}
