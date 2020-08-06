package com.foriatickets.foriabackend.gateway;

import com.auth0.json.mgmt.users.User;

import java.util.List;

public interface Auth0Gateway {

    String AUTH0_SPOTIFY_CONNECTION_NAME = "spotify";

    /**
     * Merges two Auth0 accounts to be joined together. Authenticated user will become primary.
     * Secondary user will lose all user/app metadata in process!
     *
     * @param idToken Valid Auth0 id token JWT.
     * @param connection E.g. spotify
     * @param provider E.g. oauth2
     */
    void linkAdditionalAccount(String idToken, String connection, String provider);

    /**
     * Load the Auth0 user by ID.
     *
     * @param auth0UserId ID to load.
     * @return Auth0 user. Throws exception on failure.
     */
    User obtainAuth0User(String auth0UserId);

    /**
     * Obtains all Auth0 users that have connected their accounts via Spotify.
     * Allows for data mining Spotify data.
     *
     * @return List of Auth0 users.
     */
    List<User> obtainSpotifyUsers();

    /**
     * Send an email to the specified user that asks them to click a link to verify their email address.
     */
    void resendUserVerificationEmail();

    /**
     * Unlinks all secondary accounts for primary ID for specified provider/connection.
     * @param provider E.g. spotify
     * @param connection E.g. oauth2
     */
    void unlinkAccountByConnection(String provider, String connection);
}
