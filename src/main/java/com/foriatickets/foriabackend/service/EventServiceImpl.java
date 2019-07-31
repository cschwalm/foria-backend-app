package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.EventEntity;
import com.foriatickets.foriabackend.entities.TicketFeeConfigEntity;
import com.foriatickets.foriabackend.entities.TicketTypeConfigEntity;
import com.foriatickets.foriabackend.repositories.EventRepository;
import com.foriatickets.foriabackend.repositories.TicketFeeConfigRepository;
import com.foriatickets.foriabackend.repositories.TicketTypeConfigRepository;
import com.foriatickets.foriabackend.repositories.VenueRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.openapitools.model.Event;
import org.openapitools.model.TicketFeeConfig;
import org.openapitools.model.TicketTypeConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

@Scope(scopeName = SCOPE_REQUEST)
@Service
@Transactional
public class EventServiceImpl implements EventService {

    private static final Logger LOG = LogManager.getLogger();

    private ModelMapper modelMapper;
    private EventRepository eventRepository;
    private TicketFeeConfigRepository ticketFeeConfigRepository;
    private TicketTypeConfigRepository ticketTypeConfigRepository;
    private VenueRepository venueRepository;
    private TicketService ticketService;

    @Autowired
    public EventServiceImpl(EventRepository eventRepository,
                            TicketFeeConfigRepository ticketFeeConfigRepository,
                            TicketTypeConfigRepository ticketTypeConfigRepository,
                            VenueRepository venueRepository, ModelMapper modelMapper,
                            TicketService ticketService) {

        this.eventRepository = eventRepository;
        this.ticketFeeConfigRepository = ticketFeeConfigRepository;
        this.ticketTypeConfigRepository = ticketTypeConfigRepository;
        this.venueRepository = venueRepository;
        this.modelMapper = modelMapper;
        this.ticketService = ticketService;
    }

    @Override
    public Event createEvent(Event event) {

        EventEntity eventEntity = modelMapper.map(event, EventEntity.class);

        if (eventEntity.getVenueEntity() == null || !venueRepository.existsById(event.getVenueId())) {
            throw new IllegalArgumentException("Venue does not exist with ID.");
        }

        if (event.getTicketFeeConfig() == null || event.getTicketTypeConfig() == null) {
            throw new IllegalArgumentException("Ticket config must be set.");
        }

        eventEntity = eventRepository.save(eventEntity);
        event.setId(eventEntity.getId());

        for (TicketFeeConfig ticketFeeConfig : event.getTicketFeeConfig()) {
            TicketFeeConfigEntity ticketFeeConfigEntity = modelMapper.map(ticketFeeConfig, TicketFeeConfigEntity.class);
            ticketFeeConfigEntity.setEventEntity(eventEntity);
            ticketFeeConfigEntity = ticketFeeConfigRepository.save(ticketFeeConfigEntity);
            ticketFeeConfig.setId(ticketFeeConfigEntity.getId());
        }

        for (TicketTypeConfig ticketTypeConfig : event.getTicketTypeConfig()) {
            TicketTypeConfigEntity ticketTypeConfigEntity = modelMapper.map(ticketTypeConfig, TicketTypeConfigEntity.class);
            ticketTypeConfigEntity.setEventEntity(eventEntity);
            ticketTypeConfigEntity = ticketTypeConfigRepository.save(ticketTypeConfigEntity);
            ticketTypeConfig.setId(ticketTypeConfigEntity.getId());
        }

        LOG.info("Created event entry with ID: {}", eventEntity.getId());
        return event;
    }

    @Override
    public Event getEvent(UUID eventId) {

        Optional<EventEntity> eventEntityOptional = eventRepository.findById(eventId);
        if (!eventEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID is invalid.");
        }

        EventEntity eventEntity = eventEntityOptional.get();
        Event event = modelMapper.map(eventEntity, Event.class);

        for (TicketTypeConfig ticketTypeConfig : event.getTicketTypeConfig()) {

            int ticketsRemaining = ticketService.countTicketsRemaining(ticketTypeConfig.getId());
            ticketTypeConfig.setAmountRemaining(ticketsRemaining);
        }

        return event;
    }
}
