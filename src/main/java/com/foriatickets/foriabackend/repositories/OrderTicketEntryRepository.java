package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.OrderTicketEntryEntity;
import com.foriatickets.foriabackend.entities.TicketEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderTicketEntryRepository extends CrudRepository<OrderTicketEntryEntity, UUID> {

    OrderTicketEntryEntity findByTicketEntity(TicketEntity ticketEntity);
}
