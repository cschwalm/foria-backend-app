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
     * Authorizes user to become a member of the venue.
     * This authorizes the account to preform actions on behalf of the venue.
     * Required to scan tickets and etc.
     *
     * @param venueId The venue to authorize.
     * @param userId The user to authorize.
     */
    void authorizeUser(UUID venueId, UUID userId);

    /**
     * Deauthorizes user to remove a member of the venue.
     * This deauthorizes the account to preform actions on behalf of the venue.
     *
     * @param venueId The venue to deauthorize.
     * @param userId The user to deauthorize.
     */
    void deauthorizeUser(UUID venueId, UUID userId);

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
