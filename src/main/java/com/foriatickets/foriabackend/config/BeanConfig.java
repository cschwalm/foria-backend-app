package com.foriatickets.foriabackend.config;

import com.foriatickets.foriabackend.entities.EventEntity;
import com.foriatickets.foriabackend.entities.TicketEntity;
import com.foriatickets.foriabackend.entities.VenueEntity;
import com.foriatickets.foriabackend.repositories.EventRepository;
import com.foriatickets.foriabackend.repositories.TicketFeeConfigRepository;
import com.foriatickets.foriabackend.repositories.TicketTypeConfigRepository;
import com.foriatickets.foriabackend.repositories.VenueRepository;
import com.foriatickets.foriabackend.service.*;
import org.modelmapper.AbstractConverter;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.openapitools.model.Event;
import org.openapitools.model.Ticket;
import org.openapitools.model.Venue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * Configures non-singleton beans for use.
 */
@Configuration
public class BeanConfig {

    private EventRepository eventRepository;
    private VenueRepository venueRepository;
    private TicketFeeConfigRepository ticketFeeConfigRepository;
    private TicketTypeConfigRepository ticketTypeConfigRepository;

    @Autowired TicketService ticketService;

    public BeanConfig(@Autowired EventRepository eventRepository, @Autowired VenueRepository venueRepository,
                      @Autowired TicketFeeConfigRepository ticketFeeConfigRepository, @Autowired TicketTypeConfigRepository ticketTypeConfigRepository) {
        this.eventRepository = eventRepository;
        this.ticketFeeConfigRepository = ticketFeeConfigRepository;
        this.ticketTypeConfigRepository = ticketTypeConfigRepository;
        this.venueRepository = venueRepository;
    }

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public EventService eventService(UUID eventId) {
        return new EventServiceImpl(eventId, eventRepository, ticketFeeConfigRepository, ticketTypeConfigRepository, venueRepository, modelMapper(), ticketService);
    }

    @Bean
    @Scope(value = SCOPE_SINGLETON)
    public ModelMapper modelMapper() {

        ModelMapper modelMapper = new ModelMapper();
        for (PropertyMap map : getModelMappers()) {

            //noinspection unchecked
            modelMapper.addMappings(map);
        }

        return modelMapper;
    }

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public VenueService venueService(UUID venueId) {
        return new VenueServiceImpl(venueId, venueRepository, modelMapper());
    }

    public static List<PropertyMap> getModelMappers() {
        Converter<UUID, EventEntity> eventEntityConverter = new AbstractConverter<UUID, EventEntity>() {

            protected EventEntity convert(UUID source) {

                EventEntity eventEntity = new EventEntity();
                return eventEntity.setId(source);
            }
        };

        PropertyMap<Ticket, TicketEntity> ticketEntityMap = new PropertyMap<Ticket, TicketEntity>() {

            @Override
            protected void configure() {

                using(eventEntityConverter).map(source.getEventId()).setEventEntity(null);
            }
        };

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

        PropertyMap<VenueEntity, Venue> venueDtoMap = new PropertyMap<VenueEntity, Venue>() {

            @Override
            protected void configure() {

                map().getAddress().setStreetAddress(source.getContactStreetAddress());
                map().getAddress().setCity(source.getContactCity());
                map().getAddress().setState(source.getContactState());
                map().getAddress().setZip(source.getContactZip());
                map().getAddress().setCountry(source.getContactCountry());
            }
        };

        PropertyMap<Venue, VenueEntity> venueEntityMap = new PropertyMap<Venue, VenueEntity>() {

            @Override
            protected void configure() {

                map().setContactStreetAddress(source.getAddress().getStreetAddress());
                map().setContactCity(source.getAddress().getCity());
                map().setContactState(source.getAddress().getState());
                map().setContactZip(source.getAddress().getZip());
                map().setContactCountry(source.getAddress().getCountry());
            }
        };

        List<PropertyMap> list = new ArrayList<>();
        list.add(eventDtoMap);
        list.add(eventEntityMap);
        list.add(ticketEntityMap);
        list.add(venueDtoMap);
        list.add(venueEntityMap);
        return list;
    }
}
