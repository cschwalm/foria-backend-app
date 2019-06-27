package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.TicketFeeConfigEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface TicketFeeConfigRepository extends CrudRepository<TicketFeeConfigEntity, UUID> {
}

