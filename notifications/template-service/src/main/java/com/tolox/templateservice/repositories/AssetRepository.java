package com.tolox.templateservice.repositories;

import com.tolox.templateservice.model.Asset;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AssetRepository extends ReactiveCrudRepository<Asset, UUID> {
}
