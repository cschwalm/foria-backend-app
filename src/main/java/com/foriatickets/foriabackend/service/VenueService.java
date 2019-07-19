package com.foriatickets.foriabackend.service;

import org.openapitools.model.Venue;

import java.util.Optional;
import java.util.UUID;

/**
 * Venue service to preform venue management. Scoped to the session.
 *
 * @author Corbin Schwalm
 */
public interface VenueService {

    /**
     * On-board a venue to create events.
     *
     * @param venue Venue to created.
     * @return Venue object populated with UUID.
     */
    Venue createVenue(Venue venue);

    /**
     * Returns the venue scoped at request.
     * @param venueId id
     * @return Scoped venue.
     */
    Optional<Venue> getVenue(UUID venueId);
}
