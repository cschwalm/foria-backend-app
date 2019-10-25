package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.OrderTicketEntryEntity;
import com.foriatickets.foriabackend.entities.TicketEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface OrderTicketEntryRepository extends CrudRepository<OrderTicketEntryEntity, UUID> {

    OrderTicketEntryEntity findByTicketEntity(TicketEntity ticketEntity);
}
