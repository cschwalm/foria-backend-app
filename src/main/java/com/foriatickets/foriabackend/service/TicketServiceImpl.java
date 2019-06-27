package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.TicketEntity;
import com.foriatickets.foriabackend.repositories.EventRepository;
import com.foriatickets.foriabackend.repositories.UserRepository;
import io.swagger.model.Ticket;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
public class TicketServiceImpl implements TicketService {

    private final EventRepository eventRepository;

    private final UserRepository userRepository;

    public TicketServiceImpl(EventRepository eventRepository, UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }


    @Override
    public Ticket issueTicket(UUID purchaserId, UUID eventId, UUID ticketTypeId) {

        Validate.notNull(eventId, "eventId must not be null");
        Validate.notNull(purchaserId, "purchaserId must not be null");
        Validate.notNull(ticketTypeId, "ticketTypeId must not be null");



        TicketEntity ticketEntity = new TicketEntity();
        ticketEntity.setIssuedDate(OffsetDateTime.now());


        return null;
    }
}
