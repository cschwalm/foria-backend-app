package com.foriatickets.foriabackend.service;

import org.openapitools.model.Event;

import java.util.List;
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
     * Returns a list of events that are currently running sorted by their event start date.
     *
     * @return A sorted list of events.
     */
    List<Event> getAllActiveEvents();

    /**
     * Returns the venue scoped at request.
     * @param eventId id
     * @return Scoped venue.
     */
    Event getEvent(UUID eventId);

    /**
     * Allows the user to update an event's basic metadata.
     * Separate endpoints are required for price/fee config.
     *
     * @param eventId The existing event to update.
     * @param updatedEvent New data to use.
     * @return The updated Event.
     */
    Event updateEvent(UUID eventId, Event updatedEvent);
}
