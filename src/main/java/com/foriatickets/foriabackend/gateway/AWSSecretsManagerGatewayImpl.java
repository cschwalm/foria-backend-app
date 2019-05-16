package com.foriatickets.foriabackend.gateway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.util.Optional;

@Service
public class AWSSecretsManagerGatewayImpl implements AWSSecretsManagerGateway {

    private static final Logger LOG = LogManager.getLogger();
    private SecretsManagerClient secretsManagerClient;

    public AWSSecretsManagerGatewayImpl() {

        secretsManagerClient = SecretsManagerClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    @Override
    public Optional<String> getSecretString(String secretFriendlyName) {

        GetSecretValueResponse payload = getSecretPayload(secretFriendlyName);

        if (payload == null) {
            return Optional.empty();
        }

        LOG.info("Successfully loaded secret with key: {}", secretFriendlyName);
        return Optional.of(payload.secretString());
    }

    @Override
    public Optional<DBInfo> getDbInfo(String secretFriendlyName) {

        GetSecretValueResponse payload = getSecretPayload(secretFriendlyName);

        if (payload == null || payload.secretString() == null) {
            return Optional.empty();
        }

        DBInfo info;

        JsonObject root = new JsonParser().parse(payload.secretString()).getAsJsonObject();

        String username = root.get("username").getAsString();
        String password = root.get("password").getAsString();
        String engine = root.get("engine").getAsString();
        String host = root.get("host").getAsString();
        String port = root.get("port").getAsString();

        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password) || StringUtils.isEmpty(engine) ||StringUtils.isEmpty(host) || StringUtils.isEmpty(port)) {
            LOG.error("Required parameter missing from database config in AWS Secrets Manager!");
            return Optional.empty();
        }

        info = new DBInfo()
                .setUsername(username)
                .setPassword(password)
                .setEngine(engine)
                .setHost(host)
                .setPort(port);


        LOG.info("Successfully loaded database connection info from AWS Secrets Manager.");
        return Optional.of(info);
    }

    /**
     * Gets secret if found. Otherwise returns null on error.
     *
     * @param secretFriendlyName Tag
     * @return payload
     */
    private GetSecretValueResponse getSecretPayload(String secretFriendlyName) {

        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(secretFriendlyName)
                .build();

        try {
            return secretsManagerClient.getSecretValue(getSecretValueRequest);
        } catch (DecryptionFailureException | InvalidRequestException | InvalidParameterException | InternalServiceErrorException | ResourceNotFoundException e) {
            LOG.error("Failed to obtain secret from AWS Secrets Manager! Secret key: {} - Err: {}", secretFriendlyName, e.getMessage());
            return null;
        }
    }
}
