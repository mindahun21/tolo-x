package com.tolox.templateservice.services;

import com.tolox.templateservice.dto.*;
import com.tolox.templateservice.model.Template;
import com.tolox.templateservice.model.TemplateVersion;
import com.tolox.templateservice.repositories.TemplateRepository;
import com.tolox.templateservice.repositories.TemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;

    // --- Template CRUD ---

    public Mono<TemplateResponse> createTemplate(TemplateRequest request) {
        log.info("Creating template: {}/{}", request.applicationCode(), request.templateCode());
        Template template = new Template(
                UUID.randomUUID(),
                request.applicationCode(),
                request.templateCode(),
                request.description(),
                1, // Default active version
                null, // CreatedAt handled by auditing
                null  // UpdatedAt handled by auditing
        );
        return templateRepository.save(template)
                .onErrorMap(ex -> {
                    log.error("Failed to save template for app: {}", template.applicationCode());
                    return ex;
                })
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
                            null // Trigger UpdatedAt
                    );
                    return templateRepository.save(updated);
                })
                .map(this::mapToTemplateResponse);
    }

    public Mono<Void> deleteTemplate(UUID id) {
        return templateRepository.deleteById(id);
    }

    // --- Template Version CRUD ---

    public Mono<TemplateVersionResponse> createVersion(UUID templateId, Integer versionNumber, TemplateVersionRequest request) {
        log.info("Creating version {} for template {}", versionNumber, templateId);
        TemplateVersion version = new TemplateVersion(
                UUID.randomUUID(),
                templateId,
                versionNumber,
                request.channel(),
                request.locale(),
                request.subject(),
                request.body(),
                request.engine(),
                com.tolox.templateservice.enums.VersionStatus.DRAFT,
                null // CreatedAt
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
                    return templateRepository.save(updated);
                })
                .map(this::mapToTemplateResponse);
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
