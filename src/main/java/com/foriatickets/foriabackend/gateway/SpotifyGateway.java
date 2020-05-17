package com.foriatickets.foriabackend.gateway;

import com.wrapper.spotify.model_objects.specification.Artist;

import java.util.List;

/**
 * Gateway allows access to Spotify OAuth2 REST API.
 * Authorized client applications can use OAuth2 refresh tokens to obtain data obtain an access token and access user
 * private data.
 *
 * @author Corbin Schwalm
 */
public interface SpotifyGateway {

    List<Artist> getUsersTopArtists(String refreshToken);
}
