package com.foriatickets.foriabackend.config;

import com.foriatickets.foriabackend.entities.EventEntity;
import com.foriatickets.foriabackend.entities.TicketEntity;
import com.foriatickets.foriabackend.entities.VenueEntity;
import org.modelmapper.AbstractConverter;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.openapitools.model.Event;
import org.openapitools.model.Ticket;
import org.openapitools.model.Venue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.token.Sha512DigestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * Configures non-singleton beans for use.
 */
@Configuration
public class BeanConfig {

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

    public static List<PropertyMap> getModelMappers() {

        PropertyMap<TicketEntity, Ticket> ticketMap = new PropertyMap<TicketEntity, Ticket>() {

            @Override
            protected void configure() {

                if (source.getSecret() != null) {
                    map().setSecretHash(Sha512DigestUtils.shaHex(source.getSecret()));
                }
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

                map().setStartTime(source.getEventStartTime());
                map().setEndTime(source.getEventEndTime());
                map().setVenueId(source.getVenueEntity().getId());
            }
        };

        PropertyMap<Event, EventEntity> eventEntityMap = new PropertyMap<Event, EventEntity>() {

            @Override
            protected void configure() {

                map().setEventStartTime(source.getStartTime());
                map().setEventEndTime(source.getEndTime());
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
        list.add(venueDtoMap);
        list.add(venueEntityMap);
        list.add(ticketMap);
        return list;
    }
}
