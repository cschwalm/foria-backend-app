package com.foriatickets.foriabackend.gateway;

import com.google.gson.JsonSyntaxException;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import com.wrapper.spotify.requests.data.personalization.simplified.GetUsersTopArtistsRequest;
import org.apache.hc.core5.http.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Profile("!mock")
public class SpotifyGatewayImpl implements SpotifyGateway {

    private static final Logger LOG = LogManager.getLogger();

    private static final String SPOTIFY_CLIENT_ID_FIELD = "client_id";
    private static final String SPOTIFY_CLIENT_SECRET_FIELD = "client_secret";

    private static final String TIME_RANGE = "short_term";

    private final Map<String, String> spotifySecretMap;

    public SpotifyGatewayImpl(@Autowired AWSSecretsManagerGateway awsSecretsManagerGateway,
                              @Value("${spotifyApiKey}") String spotifyApiKey) {

        final Optional<Map<String, String>> auth0Secrets = awsSecretsManagerGateway.getAllSecrets(spotifyApiKey);
        if (!auth0Secrets.isPresent() || auth0Secrets.get().isEmpty()) {
            LOG.error("Failed to obtain Spotify Secrets. Halting application start!");
            throw new RuntimeException("Failed to obtain Spotify Secrets. Halting application start!");
        }

        spotifySecretMap = auth0Secrets.get();
    }

    @Override
    public List<Artist> getUsersTopArtists(String refreshToken) {

        final SpotifyApi spotifyApi = obtainUserAccessToken(refreshToken);

        final GetUsersTopArtistsRequest getUsersTopArtistsRequest = spotifyApi.getUsersTopArtists()
                .time_range(TIME_RANGE)
                .build();

        final List<Artist> artists;
        try {

            final Paging<Artist> artistPaging = getUsersTopArtistsRequest.execute();
            artists = Arrays.asList(artistPaging.getItems());

        } catch (IOException | SpotifyWebApiException | ParseException e) {
            LOG.error("Spotify API Error During Artist Pull: " + e.getMessage());
            throw new RuntimeException("Spotify API Error During Artist Pull: " + e.getMessage());
        }

        LOG.debug("Obtained {} artists for user.", artists.size());
        return artists;
    }

    /**
     * Converts the user refresh token into an access token that can be used to query user data.
     *
     * @param refreshToken Token obtained from prior user authorization. Should not be cached.
     * @return Configured client that can process requests on behalf of user.
     */
    private SpotifyApi obtainUserAccessToken(String refreshToken) {

        final String clientId = spotifySecretMap.get(SPOTIFY_CLIENT_ID_FIELD);
        final String clientSecret = spotifySecretMap.get(SPOTIFY_CLIENT_SECRET_FIELD);

        if (clientId == null || clientSecret == null || refreshToken == null) {
            LOG.error("Failed to load required secrets to obtain Spotify access token!");
            throw new RuntimeException("Failed to load required secrets to obtain Spotify access token!");
        }

        final SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build();

        final AuthorizationCodeRefreshRequest authorizationCodeRefreshRequest = spotifyApi.authorizationCodeRefresh()
                .build();

        try {
            final AuthorizationCodeCredentials authorizationCodeCredentials = authorizationCodeRefreshRequest.execute();
            LOG.debug("Spotify token obtained. Expires in: " + authorizationCodeCredentials.getExpiresIn());

            //Set access and refresh token for further "spotifyApi" object usage
            spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
            spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());

        } catch (IOException | SpotifyWebApiException | ParseException | JsonSyntaxException e) {
            LOG.error("Spotify API Error During Token Refresh: " + e.getMessage());
            throw new RuntimeException("Spotify API Error During Token Refresh: " + e.getMessage());
        }

        return spotifyApi;
    }
}
