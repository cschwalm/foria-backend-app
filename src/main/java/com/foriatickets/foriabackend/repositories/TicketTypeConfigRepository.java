package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.TicketTypeConfigEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface TicketTypeConfigRepository extends CrudRepository<TicketTypeConfigEntity, UUID> {
}

