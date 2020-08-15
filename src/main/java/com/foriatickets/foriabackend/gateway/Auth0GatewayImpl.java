package com.foriatickets.foriabackend.gateway;

import com.auth0.client.auth.AuthAPI;
import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.exception.Auth0Exception;
import com.auth0.exception.IdTokenValidationException;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.tickets.EmailVerificationTicket;
import com.auth0.json.mgmt.users.Identity;
import com.auth0.json.mgmt.users.User;
import com.auth0.json.mgmt.users.UsersPage;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.net.AuthRequest;
import com.auth0.net.Request;
import com.auth0.utils.tokens.IdTokenVerifier;
import com.auth0.utils.tokens.SignatureVerifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.server.ResponseStatusException;

import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Profile("!mock")
public class Auth0GatewayImpl implements Auth0Gateway {

    private static final Logger LOG = LogManager.getLogger();

    private static final String AUTH0_DOMAIN_FIELD = "domain";
    private static final String AUTH0_CLIENT_ID_FIELD = "client_id";
    private static final String AUTH0_CLIENT_SECRET_FIELD = "client_secret";

    private ManagementAPI auth0;
    private final Map<String, String> auth0SecretMap;
    private long tokenExpirySecs = -1L;

    private final IdTokenVerifier idTokenVerifier;

    public Auth0GatewayImpl(@Autowired AWSSecretsManagerGateway awsSecretsManagerGateway,
                            @Value("${auth0ManagementKey}") String auth0ManagementKey,
                            @Value(value = "${auth0.issuer}") String issuer,
                            @Value(value = "${auth0.foriaWebAppAudience}") String foriaWebAppAudience) {

        final Optional<Map<String, String>> auth0Secrets = awsSecretsManagerGateway.getAllSecrets(auth0ManagementKey);
        if (!auth0Secrets.isPresent() || auth0Secrets.get().isEmpty()) {
            LOG.error("Failed to obtain Auth0 Secrets. Halting application start!");
            throw new RuntimeException("Failed to obtain Auth0 Secrets. Halting application start!");
        }

        auth0SecretMap = auth0Secrets.get();
        refreshToken();

        //Configure ID Token verifier.
        final JwkProvider provider = new JwkProviderBuilder(issuer).build();
        final SignatureVerifier signatureVerifier = SignatureVerifier.forRS256(keyId -> {
            try {
                return (RSAPublicKey) provider.get(keyId).getPublicKey();
            } catch (JwkException jwke) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to validate JWT. Key load fail.");
            }
        });

        idTokenVerifier = IdTokenVerifier.init(issuer, foriaWebAppAudience, signatureVerifier).build();

        LOG.info("Successfully connected to Auth0 Management API.");
    }

    @Override
    public void linkAdditionalAccount(String idToken, String connection, String provider) {

        //Load user from Auth0 token.
        final String primaryAuth0Id = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (primaryAuth0Id == null) {
            LOG.error("Attempted to link accounts without an authenticated user.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Attempted to link accounts without an authenticated user.");
        }

        //Decode token to obtain sub for secondary.
        verifyIdToken(idToken);
        final DecodedJWT secondaryJwt = JWT.decode(idToken);
        final String secondaryId = secondaryJwt.getSubject();

        if ( (System.currentTimeMillis() / 1000L) + 5L >= tokenExpirySecs) {
            refreshToken();
        }

        final Request<List<Identity>> request = auth0.users().linkIdentity(primaryAuth0Id, secondaryId, provider, null);
        final List<Identity> identities;
        try {
            identities = request.execute();
        } catch (Auth0Exception e) {
            LOG.error("Failed to link user identities with Auth0! Primary Auth0 ID: {} Msg: {}", primaryAuth0Id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to link user identities with Auth0. Error message: " + e.getMessage());
        }

        if (!identities.isEmpty()) {
            final String primaryUserId = identities.get(0).getUserId();
            LOG.info("Linked accounts with primaryUserId: {}", primaryUserId);
        }
    }

    @Override
    public void unlinkAccountByConnection(String connection, String provider) {

        if (provider == null || connection == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required parameters must not be null.");
        }

        //Load user from Auth0 token.
        final String primaryAuth0Id = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (primaryAuth0Id == null) {
            LOG.error("Attempted to link accounts without an authenticated user.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Attempted to link accounts without an authenticated user.");
        }

        if ( (System.currentTimeMillis() / 1000L) + 5L >= tokenExpirySecs) {
            refreshToken();
        }

        final Request<User> auth0UserRequest = auth0.users().get(primaryAuth0Id, null);
        final User auth0User;
        try {
            auth0User = auth0UserRequest.execute();
        } catch (Auth0Exception ex) {
            LOG.error("Failed to query Auth0 user with ID: {}! Msg: {}", primaryAuth0Id, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to query Auth0 user.");
        }

        //Find all identities with the provider. In edge cases, may be more than one.
        final List<Identity> auth0SecondaryIdentities = auth0User.getIdentities()
                .stream()
                .filter(identity -> provider.equalsIgnoreCase(identity.getProvider()) && connection.equalsIgnoreCase(identity.getConnection()))
                .collect(Collectors.toList());

        if (auth0SecondaryIdentities.isEmpty()) {
            LOG.info("Failed to unlink identity. Not found.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to unlink identity. Not found.");
        }

        for (Identity secondaryIdentity : auth0SecondaryIdentities) {

            final Request<List<Identity>> request = auth0.users().unlinkIdentity(primaryAuth0Id, secondaryIdentity.getUserId(), provider);
            try {
                request.execute();
            } catch (Auth0Exception e) {
                LOG.error("Failed to unlink user identities with Auth0! Msg: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to unlink user identities with Auth0.");
            }
        }

        LOG.debug("Finished unlinking all user identities.");
    }

    @Override
    public User obtainAuth0User(String auth0UserId) {

        if ( (System.currentTimeMillis() / 1000L) + 5L >= tokenExpirySecs) {
            refreshToken();
        }

        final Request<User> request = auth0.users().get(auth0UserId, null);

        try {
            return request.execute();
        } catch (Auth0Exception e) {
            LOG.error("Failed to load Auth0 user by ID. - Msg: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<User> obtainSpotifyUsers() {

        if ( (System.currentTimeMillis() / 1000L) + 5L >= tokenExpirySecs) {
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

        if ( (System.currentTimeMillis() / 1000L) + 5L >= tokenExpirySecs) {
            refreshToken();
        }

        //Load user from Auth0 token.
        final String auth0Id = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        final EmailVerificationTicket emailVerificationTicket = new EmailVerificationTicket(auth0Id);
        final Request<EmailVerificationTicket> jobRequest = auth0.tickets().requestEmailVerification(emailVerificationTicket);
        try {
            jobRequest.execute();
        } catch (Auth0Exception e) {
            LOG.error("Failed to send Auth0 verification email for userID: {} - Msg: {}", auth0Id, e.getMessage());
            throw new RuntimeException(e);
        }
        LOG.info("Auth0 verification email resent for userID: {}.", auth0Id);
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

        tokenExpirySecs = System.currentTimeMillis() / 1000L + tokenHolder.getExpiresIn();
        auth0 = new ManagementAPI(auth0Domain, tokenHolder.getAccessToken());
        LOG.info("Auth0 token refresh completed. New token expires: {} seconds.", tokenExpirySecs);
    }

    /**
     * Preforms standard JWT checks to ensure token is not forged.
     *
     * @param idToken Auth0 issued id token.
     */
    private void verifyIdToken(String idToken) {

        Assert.notNull(idToken, "idToken must not be null.");

        try {
            idTokenVerifier.verify(idToken);
        } catch(IdTokenValidationException idtve) {
            LOG.warn("Failed to validate user supplied ID token. Msg: {}", idtve.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to validate JWT. Key load fail.");
        }

    }
}
