package com.foriatickets.foriabackend.config;

import com.foriatickets.foriabackend.repositories.EventRepository;
import com.foriatickets.foriabackend.repositories.VenueRepository;
import com.foriatickets.foriabackend.service.EventService;
import com.foriatickets.foriabackend.service.EventServiceImpl;
import com.foriatickets.foriabackend.service.VenueService;
import com.foriatickets.foriabackend.service.VenueServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.UUID;

/**
 * Configures non-singleton beans for use.
 */
@Configuration
public class BeanConfig {

    private EventRepository eventRepository;
    private VenueRepository venueRepository;

    public BeanConfig(@Autowired EventRepository eventRepository, @Autowired VenueRepository venueRepository) {
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
    }

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public EventService eventService(UUID eventId) {
        return new EventServiceImpl(eventId, eventRepository);
    }

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public VenueService venueService(UUID venueId) {
        return new VenueServiceImpl(venueId, venueRepository);
    }
}
