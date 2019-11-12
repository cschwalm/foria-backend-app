package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.VenueAccessEntity;
import org.openapitools.model.Venue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
     * Returns metadata for each venue the user has access to along with event info.
     *
     * @return List of venues.
     */
    List<Venue> getAllVenues();

    /**
     * Returns the venue scoped at request.
     * @param venueId id
     * @return Scoped venue.
     */
    Optional<Venue> getVenue(UUID venueId);

    /**
     * Helper method to determine if a user has permission to access a venue.
     *
     * @param venueId The venue ID to check.
     * @param venueAccessEntities List loaded form user permissions.
     * @return True if access is granted; false otherwise.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean checkVenueAuthorization(UUID venueId, Set<VenueAccessEntity> venueAccessEntities) {

        if (venueId == null || venueAccessEntities == null) {
            return false;
        }

        boolean isAuthorized = false;
        for (VenueAccessEntity venueAccessEntity : venueAccessEntities) {
            if (venueAccessEntity.getVenueEntity().getId().toString().equals(venueId.toString())) {
                isAuthorized = true;
            }
        }

        return isAuthorized;
    }
}
