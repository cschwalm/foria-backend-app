package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.EventEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends CrudRepository<EventEntity, UUID> {

    List<EventEntity> findAllByOrderByEventStartTimeAsc();
}
