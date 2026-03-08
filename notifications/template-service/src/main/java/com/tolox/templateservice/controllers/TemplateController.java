package com.tolox.templateservice.controllers;

import com.tolox.templateservice.dto.*;
import com.tolox.templateservice.services.RenderingService;
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
    private final RenderingService renderingService;

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

    // --- Variable Discovery ---

    @GetMapping("/{id}/variables")
    public Mono<VariableDiscoveryResponse> discoverActiveVariables(@PathVariable UUID id) {
        return templateService.discoverVariables(id);
    }

    @GetMapping("/{id}/versions/{versionNumber}/variables")
    public Mono<VariableDiscoveryResponse> discoverVersionVariables(
            @PathVariable UUID id, 
            @PathVariable Integer versionNumber) {
        return templateService.discoverVariablesForVersion(id, versionNumber);
    }

    // --- Rendering & Preview ---

    @PostMapping("/render")
    public Mono<RenderResponse> render(@RequestBody RenderRequest request) {
        return renderingService.render(request);
    }

    @PostMapping("/{templateId}/versions/{versionNumber}/preview")
    public Mono<RenderResponse> preview(
            @PathVariable UUID templateId,
            @PathVariable Integer versionNumber,
            @RequestBody RenderRequest request) {
        return renderingService.preview(templateId, versionNumber, request);
    }
}
