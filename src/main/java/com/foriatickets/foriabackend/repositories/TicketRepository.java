package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.TicketEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TicketRepository extends CrudRepository<TicketEntity, UUID> {

    @Query("SELECT COUNT(id) " +
            "FROM TicketEntity t " +
            "WHERE t.ticketTypeConfigEntity.id = ?1 " +
            "AND t.eventEntity.id = ?2 " +
            "AND t.status NOT IN ('CANCELED', 'CANCELED_FRAUD')")
    int countActiveTicketsIssuedByType(UUID ticketTypeConfigId, UUID eventId);
}

