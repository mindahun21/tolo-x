package com.tolox.templateservice.repositories;

import com.tolox.templateservice.model.Template;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface TemplateRepository extends R2dbcRepository<Template, UUID> {
    Mono<Template> findByApplicationCodeAndTemplateCode(String applicationCode, String templateCode);
}
