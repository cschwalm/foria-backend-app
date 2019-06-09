package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.EventEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface EventRepository extends CrudRepository<EventEntity, UUID> {
}
