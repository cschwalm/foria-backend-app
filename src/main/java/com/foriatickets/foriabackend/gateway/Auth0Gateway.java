package com.foriatickets.foriabackend.gateway;

import com.auth0.json.mgmt.users.User;

import java.util.List;

public interface Auth0Gateway {

    String AUTH0_SPOTIFY_CONNECTION_NAME = "spotify";

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
}
