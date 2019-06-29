package com.foriatickets.foriabackend.service;

import org.openapitools.model.Event;

import java.util.Optional;

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
     * @return Scoped venue.
     */
    Optional<Event> getEvent();
}
