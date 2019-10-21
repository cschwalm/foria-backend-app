package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.VenueAccessEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface VenueAccessRepository extends CrudRepository<VenueAccessEntity, UUID> {

    boolean existsByVenueEntity_IdAndUserEntity_Id(UUID venueId, UUID userId);
    VenueAccessEntity findByVenueEntity_IdAndUserEntity_Id(UUID venueId, UUID userId);
}