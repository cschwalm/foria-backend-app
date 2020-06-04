package com.foriatickets.foriabackend.service;

/**
 * Service obtains connected IdP users and polls Spotify to load their listening history.
 *
 * @author Corbin Schwalm
 */
public interface SpotifyIngestionService {

    /**
     * Expected to run daily.
     */
    void pollTopArtistsForAllUsers();
}
