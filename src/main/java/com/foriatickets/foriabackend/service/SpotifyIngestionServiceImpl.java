package com.foriatickets.foriabackend.service;

import com.auth0.json.mgmt.users.Identity;
import com.auth0.json.mgmt.users.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foriatickets.foriabackend.entities.UserEntity;
import com.foriatickets.foriabackend.entities.UserMusicInterestsEntity;
import com.foriatickets.foriabackend.gateway.Auth0Gateway;
import com.foriatickets.foriabackend.gateway.SpotifyGateway;
import com.foriatickets.foriabackend.repositories.UserMusicInterestsRepository;
import com.foriatickets.foriabackend.repositories.UserRepository;
import com.wrapper.spotify.model_objects.specification.Artist;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openapitools.model.UserTopArtists;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SpotifyIngestionServiceImpl implements SpotifyIngestionService {

    private final Auth0Gateway auth0Gateway;

    private final SpotifyGateway spotifyGateway;

    private final ObjectMapper objectMapper;

    private final UserMusicInterestsRepository userMusicInterestsRepository;

    private final UserRepository userRepository;

    private static final Logger LOG = LogManager.getLogger();

    public SpotifyIngestionServiceImpl(Auth0Gateway auth0Gateway, SpotifyGateway spotifyGateway, UserMusicInterestsRepository userMusicInterestsRepository, UserRepository userRepository) {
        this.auth0Gateway = auth0Gateway;
        this.spotifyGateway = spotifyGateway;
        this.userMusicInterestsRepository = userMusicInterestsRepository;
        this.userRepository = userRepository;
        objectMapper = new ObjectMapper();
    }

    @Scheduled(cron = "${daily-spotify-cron:-}")
    @SchedulerLock(name = "daily-spotify-job")
    @Override
    public void pollTopArtistsForAllUsers() {

        LOG.info("Starting Spotify data load job at: {}", OffsetDateTime.now());

        //Obtain list of Spotify IdP users.
        final List<User> spotifyUsers = auth0Gateway.obtainSpotifyUsers();
        LOG.debug("Loaded {} Auth0 accounts that have connected spotify.", spotifyUsers.size());

        for (User spotifyUser : spotifyUsers) {

            //Find Spotify Idp
            Identity spotifyId = null;
            final List<Identity> idpList = spotifyUser.getIdentities();
            for (Identity identity : idpList) {
                if (identity.getConnection().equals(Auth0Gateway.AUTH0_SPOTIFY_CONNECTION_NAME)) {
                    spotifyId = identity;
                    break;
                }
            }

            //Ensure IdP is for Spotify.
            if (spotifyId == null) {
                LOG.error("Failed to find connected Spotify identity for connected user: {}", spotifyUser.getId());
                continue;
            }

            //Validate Foria account exists
            final UserEntity userEntity = userRepository.findByAuth0Id(spotifyUser.getId());
            if (userEntity == null) {
                LOG.info("Foria user account missing for connected user: {}. Skipping user.", spotifyUser.getId());
                continue;
            }

            //Load Spotify Data
            final Object spotifyRefreshToken = spotifyId.getValues().get("refresh_token");
            if (spotifyRefreshToken == null) {
                LOG.error("Spotify refresh token missing for connected user: {}", spotifyUser.getId());
                continue;
            }
            final List<Artist> spotifyArtistList = spotifyGateway.getUsersTopArtists(spotifyRefreshToken.toString());
            LOG.debug("Loaded {} Spotify artists for userId: {}", spotifyArtistList.size(), spotifyUser.getId());

            if (spotifyArtistList.isEmpty()) {
                LOG.debug("Skipping Spotify user for no interest data. UserId: {}", spotifyUser.getId());
                continue;
            }

            String json;
            try {
                json = objectMapper.writeValueAsString(spotifyArtistList);
            } catch (JsonProcessingException e) {
                LOG.error("Failed to write Spotify list to JSON!");
                LOG.error(e.getMessage());
                continue;
            }

            //Write entry to db
            UserMusicInterestsEntity userMusicInterestsEntity = new UserMusicInterestsEntity();
            userMusicInterestsEntity.setData(json);
            userMusicInterestsEntity.setProcessedDate(LocalDate.now());
            userMusicInterestsEntity.setUserEntity(userEntity);

            userMusicInterestsEntity = userMusicInterestsRepository.save(userMusicInterestsEntity);
            LOG.debug("Created record with id: {}", userMusicInterestsEntity.getId());
        }

        LOG.info("Finished Spotify data load job at: {}", OffsetDateTime.now());
    }

    @Override
    public UserTopArtists processTopArtists(UUID permalinkUUID) {

        return null;
    }
}
