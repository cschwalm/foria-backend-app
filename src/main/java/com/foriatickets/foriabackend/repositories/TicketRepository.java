package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.TicketEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface TicketRepository extends CrudRepository<TicketEntity, UUID> {
}

