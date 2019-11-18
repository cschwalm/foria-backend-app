package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.TicketFeeConfigEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TicketFeeConfigRepository extends CrudRepository<TicketFeeConfigEntity, UUID> {
}

