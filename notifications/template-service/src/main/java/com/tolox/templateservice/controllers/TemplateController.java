package com.tolox.templateservice.controllers;

import com.tolox.templateservice.dto.TemplateRequest;
import com.tolox.templateservice.dto.TemplateResponse;
import com.tolox.templateservice.dto.TemplateVersionRequest;
import com.tolox.templateservice.dto.TemplateVersionResponse;
import com.tolox.templateservice.services.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/notification-template/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TemplateResponse> createTemplate(@RequestBody TemplateRequest request) {
        return templateService.createTemplate(request);
    }

    @GetMapping("/{id}")
    public Mono<TemplateResponse> getTemplate(@PathVariable UUID id) {
        return templateService.getTemplate(id);
    }

    @GetMapping
    public Flux<TemplateResponse> getAllTemplates() {
        return templateService.getAllTemplates();
    }

    @PutMapping("/{id}")
    public Mono<TemplateResponse> updateTemplate(@PathVariable UUID id, @RequestBody TemplateRequest request) {
        return templateService.updateTemplate(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteTemplate(@PathVariable UUID id) {
        return templateService.deleteTemplate(id);
    }

    // --- Template Version Endpoints ---

    @PostMapping("/{templateId}/versions/{versionNumber}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TemplateVersionResponse> createVersion(
            @PathVariable UUID templateId,
            @PathVariable Integer versionNumber,
            @RequestBody TemplateVersionRequest request) {
        return templateService.createVersion(templateId, versionNumber, request);
    }

    @GetMapping("/{templateId}/versions")
    public Flux<TemplateVersionResponse> getVersions(@PathVariable UUID templateId) {
        return templateService.getVersionsForTemplate(templateId);
    }

    @PatchMapping("/{templateId}/publish/{versionNumber}")
    public Mono<TemplateResponse> publishVersion(
            @PathVariable UUID templateId,
            @PathVariable Integer versionNumber) {
        return templateService.publishVersion(templateId, versionNumber);
    }
}
