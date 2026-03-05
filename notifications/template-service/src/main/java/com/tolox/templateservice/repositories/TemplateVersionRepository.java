package com.tolox.templateservice.repositories;

import com.tolox.templateservice.enums.ChannelType;
import com.tolox.templateservice.model.TemplateVersion;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface TemplateVersionRepository extends R2dbcRepository<TemplateVersion, UUID> {
    Flux<TemplateVersion> findByTemplateId(UUID templateId);
    Mono<TemplateVersion> findByTemplateIdAndVersionNumber(UUID templateId, Integer versionNumber);
    Mono<TemplateVersion> findByTemplateIdAndVersionNumberAndChannelAndLocale(UUID templateId, Integer versionNumber, ChannelType channel, String locale);
}
