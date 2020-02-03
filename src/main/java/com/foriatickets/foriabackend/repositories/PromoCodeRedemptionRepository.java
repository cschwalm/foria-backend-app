package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.PromoCodeRedemptionEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface PromoCodeRedemptionRepository extends CrudRepository<PromoCodeRedemptionEntity, UUID> {
}
