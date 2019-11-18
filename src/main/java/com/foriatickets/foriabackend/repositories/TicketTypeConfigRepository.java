package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.TicketTypeConfigEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TicketTypeConfigRepository extends CrudRepository<TicketTypeConfigEntity, UUID> {
}

