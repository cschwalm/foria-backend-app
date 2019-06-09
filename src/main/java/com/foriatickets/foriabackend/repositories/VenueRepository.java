package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.VenueEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface VenueRepository extends CrudRepository<VenueEntity, UUID> {

}