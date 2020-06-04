package com.foriatickets.foriabackend.gateway;

import com.auth0.client.auth.AuthAPI;
import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.jobs.Job;
import com.auth0.json.mgmt.users.User;
import com.auth0.json.mgmt.users.UsersPage;
import com.auth0.net.AuthRequest;
import com.auth0.net.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Profile("!mock")
public class Auth0GatewayImpl implements Auth0Gateway {

    private static final Logger LOG = LogManager.getLogger();

    private static final String AUTH0_DOMAIN_FIELD = "domain";
    private static final String AUTH0_CLIENT_ID_FIELD = "client_id";
    private static final String AUTH0_CLIENT_SECRET_FIELD = "client_secret";

    private ManagementAPI auth0;
    private Map<String, String> auth0SecretMap;
    private long tokenExpiry = -1;

    public Auth0GatewayImpl(@Autowired AWSSecretsManagerGateway awsSecretsManagerGateway,
                            @Value("${auth0ManagementKey}") String auth0ManagementKey) {

        final Optional<Map<String, String>> auth0Secrets = awsSecretsManagerGateway.getAllSecrets(auth0ManagementKey);
        if (!auth0Secrets.isPresent() || auth0Secrets.get().isEmpty()) {
            LOG.error("Failed to obtain Auth0 Secrets. Halting application start!");
            throw new RuntimeException("Failed to obtain Auth0 Secrets. Halting application start!");
        }

        auth0SecretMap = auth0Secrets.get();
        refreshToken();
        LOG.info("Successfully connected to Auth0 Management API.");
    }

    @Override
    public List<User> obtainSpotifyUsers() {

        if (System.currentTimeMillis() >= tokenExpiry + 1000L) {
            refreshToken();
        }

        final UserFilter userFilter = new UserFilter()
                .withQuery("identities.connection:\"" + AUTH0_SPOTIFY_CONNECTION_NAME + "\"")
                .withPage(0, 100)
                .withTotals(true);

        final Request<UsersPage> usersPageRequest = auth0.users().list(userFilter);
        final List<User> users;
        try {
            users = usersPageRequest.execute().getItems();
        } catch (Auth0Exception e) {
            LOG.error("Failed to query Auth0 users for Spotify connection. - Msg: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        LOG.info("Obtained {} Auth0 users that have linked their Spotify accounts.", users.size());
        return users;
    }

    @Override
    public void resendUserVerificationEmail() {

        if (System.currentTimeMillis() >= tokenExpiry + 1000L) {
            refreshToken();
        }

        //Load user from Auth0 token.
        String auth0Id = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        final Request<Job> jobRequest = auth0.jobs().sendVerificationEmail(auth0Id, auth0SecretMap.get(AUTH0_CLIENT_ID_FIELD));
        Job jobResult;
        try {
            jobResult = jobRequest.execute();
        } catch (Auth0Exception e) {
            LOG.error("Failed to send Auth0 verification email for userID: {} - Msg: {}", auth0Id, e.getMessage());
            throw new RuntimeException(e);
        }
        LOG.info("Auth0 verification email resent for userID: {} with requestId: {}", auth0Id, jobResult.getId());
    }

    /**
     * Sets up a Auth0 client if necessary and refreshes token.
     */
    private void refreshToken() {

        if (auth0SecretMap == null || !auth0SecretMap.containsKey(AUTH0_DOMAIN_FIELD)
                || !auth0SecretMap.containsKey(AUTH0_CLIENT_ID_FIELD) || !auth0SecretMap.containsKey(AUTH0_CLIENT_SECRET_FIELD)) {
            LOG.error("Failed to load required Auth0 secrets. Failing application startup!");
            throw new RuntimeException("Failed to load required Auth0 secrets. Failing application startup!");
        }

        final String auth0Domain = auth0SecretMap.get(AUTH0_DOMAIN_FIELD);
        final String auth0ClientId = auth0SecretMap.get(AUTH0_CLIENT_ID_FIELD);
        final String auth0clientSecret = auth0SecretMap.get(AUTH0_CLIENT_SECRET_FIELD);

        final String audience = "https://" + auth0Domain + "/api/v2/";

        AuthAPI authAPI = new AuthAPI(auth0Domain, auth0ClientId, auth0clientSecret);
        AuthRequest authRequest = authAPI.requestToken(audience);

        TokenHolder tokenHolder;
        try {
            tokenHolder = authRequest.execute();
        } catch (Auth0Exception e) {
            LOG.error("Failed to obtain Auth0 token with client secrets. Failing startup. Msg: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }

        tokenExpiry = System.currentTimeMillis() + tokenHolder.getExpiresIn();
        auth0 = new ManagementAPI(auth0Domain, tokenHolder.getAccessToken());
        LOG.info("Auth0 token refresh completed. New token expires: {}", tokenExpiry);
    }
}
