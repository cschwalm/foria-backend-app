package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.EventEntity;
import com.foriatickets.foriabackend.entities.TicketFeeConfigEntity;
import com.foriatickets.foriabackend.entities.TicketTypeConfigEntity;
import com.foriatickets.foriabackend.entities.VenueEntity;
import com.foriatickets.foriabackend.repositories.EventRepository;
import com.foriatickets.foriabackend.repositories.TicketFeeConfigRepository;
import com.foriatickets.foriabackend.repositories.TicketTypeConfigRepository;
import com.foriatickets.foriabackend.repositories.VenueRepository;
import io.swagger.model.Event;
import io.swagger.model.TicketFeeConfig;
import io.swagger.model.TicketTypeConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.AbstractConverter;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
public class EventServiceImpl implements EventService {

    private static final Logger LOG = LogManager.getLogger();

    private ModelMapper modelMapper;

    private EventEntity eventEntity;
    private UUID eventId;
    private EventRepository eventRepository;
    private TicketFeeConfigRepository ticketFeeConfigRepository;
    private TicketTypeConfigRepository ticketTypeConfigRepository;
    private VenueRepository venueRepository;

    public EventServiceImpl(UUID eventId, EventRepository eventRepository,
                            TicketFeeConfigRepository ticketFeeConfigRepository,
                            TicketTypeConfigRepository ticketTypeConfigRepository,
                            VenueRepository venueRepository) {

        modelMapper = new ModelMapper();

        Converter<UUID, VenueEntity> entityConverter = new AbstractConverter<UUID, VenueEntity>() {

            protected VenueEntity convert(UUID source) {

                VenueEntity venueEntity = new VenueEntity();
                return venueEntity.setId(source);
            }
        };

        PropertyMap<EventEntity, Event> eventDtoMap = new PropertyMap<EventEntity, Event>() {

            @Override
            protected void configure() {

                map().getAddress().setStreetAddress(source.getEventStreetAddress());
                map().getAddress().setCity(source.getEventCity());
                map().getAddress().setState(source.getEventState());
                map().getAddress().setZip(source.getEventPostal());
                map().getAddress().setCountry(source.getEventCountry());
                map().setTime(source.getEventTime());
                map().setVenueId(source.getVenueEntity().getId());
            }
        };

        PropertyMap<Event, EventEntity> eventEntityMap = new PropertyMap<Event, EventEntity>() {

            @Override
            protected void configure() {

                map().setEventStreetAddress(source.getAddress().getStreetAddress());
                map().setEventCity(source.getAddress().getCity());
                map().setEventState(source.getAddress().getState());
                map().setEventPostal(source.getAddress().getZip());
                map().setEventCountry(source.getAddress().getCountry());
                map().setEventTime(source.getTime());
                using(entityConverter).map(source.getVenueId()).setVenueEntity(null);
            }
        };

        modelMapper.addMappings(eventDtoMap);
        modelMapper.addMappings(eventEntityMap);

        this.eventId = eventId;
        this.eventRepository = eventRepository;
        this.ticketFeeConfigRepository = ticketFeeConfigRepository;
        this.ticketTypeConfigRepository = ticketTypeConfigRepository;
        this.venueRepository = venueRepository;

        if (eventId == null) {
            return;
        }

        Optional<EventEntity> eventEntity = this.eventRepository.findById(eventId);
        if (!eventEntity.isPresent()) {
            LOG.warn("Supplied event ID {} does not exist.", eventId);
            return;
        }

        this.eventEntity = eventEntity.get();
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

        this.eventEntity = eventRepository.save(eventEntity);
        event.setId(eventEntity.getId());

        for (TicketFeeConfig ticketFeeConfig : event.getTicketFeeConfig()) {
            TicketFeeConfigEntity ticketFeeConfigEntity = modelMapper.map(ticketFeeConfig, TicketFeeConfigEntity.class);
            ticketFeeConfigEntity.setEventEntity(this.eventEntity);
            ticketFeeConfigEntity = ticketFeeConfigRepository.save(ticketFeeConfigEntity);
            ticketFeeConfig.setId(ticketFeeConfigEntity.getId());
        }

        for (TicketTypeConfig ticketTypeConfig : event.getTicketTypeConfig()) {
            TicketTypeConfigEntity ticketTypeConfigEntity = modelMapper.map(ticketTypeConfig, TicketTypeConfigEntity.class);
            ticketTypeConfigEntity.setEventEntity(this.eventEntity);
            ticketTypeConfigEntity = ticketTypeConfigRepository.save(ticketTypeConfigEntity);
            ticketTypeConfig.setId(ticketTypeConfigEntity.getId());
        }

        LOG.info("Created event entry with ID: {}", eventEntity.getId());
        return event;
    }

    @Override
    public Optional<Event> getEvent() {

        if (eventEntity == null) {
            LOG.warn("Failed to return event with ID: {}", eventId);
            return Optional.empty();
        }

        return Optional.of(modelMapper.map(eventEntity, Event.class));
    }
}
