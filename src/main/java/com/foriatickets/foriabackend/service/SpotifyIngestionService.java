package com.foriatickets.foriabackend.service;

import org.openapitools.model.UserTopArtists;

import java.util.UUID;

/**
 * Service obtains connected IdP users and polls Spotify to load their listening history.
 *
 * @author Corbin Schwalm
 */
public interface SpotifyIngestionService {

    /**
     * Expected to run daily.
     */
    @SuppressWarnings("unused")
    void pollTopArtistsForAllUsers();

    /**
     * Builds payload to query users top artists from all providers.
     * If the permalink UUID is null, the latest result is returned.
     *
     * @param permalinkUUID Primary ID of loaded results.
     * @return Ordered list of top artists with associated metadata.
     */
    UserTopArtists processTopArtists(UUID permalinkUUID);
}
