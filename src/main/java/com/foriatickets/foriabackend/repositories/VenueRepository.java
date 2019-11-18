package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.VenueEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VenueRepository extends CrudRepository<VenueEntity, UUID> {

}