package com.foriatickets.foriabackend.service;

import org.openapitools.model.Event;

import java.util.Optional;
import java.util.UUID;

/**
 * Event service to preform venue management.
 *
 * @author Corbin Schwalm
 */
public interface EventService {

    /**
     * Create an event.
     *
     * @param event Event to created.
     * @return Event object populated with UUID.
     */
    Event createEvent(Event event);

    /**
     * Returns the venue scoped at request.
     * @param eventId id
     * @return Scoped venue.
     */
    Optional<Event> getEvent(UUID eventId);
}
