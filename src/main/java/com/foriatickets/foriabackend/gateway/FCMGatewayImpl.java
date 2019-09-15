package com.foriatickets.foriabackend.gateway;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Service
@Profile("!mock")
public class FCMGatewayImpl implements FCMGateway {

    private static final Logger LOG = LogManager.getLogger();

    public FCMGatewayImpl(@Autowired AWSSecretsManagerGateway awsSecretsManagerGateway,
                          @Value("${fcmKey}") String fcmKeyName,
                          @Value("${fcmDatabaseUrl}") String fcmDatabaseUrl) throws IOException {

        final Optional<String> fcmKey = awsSecretsManagerGateway.getSecretRaw(fcmKeyName);
        if (!fcmKey.isPresent()) {
            LOG.error("Failed to load FCM key with friendlyName: {}", fcmKeyName);
            throw new RuntimeException("Failed to load FCM key with friendlyName: " + fcmKeyName);
        }

        final InputStream targetStream = new ByteArrayInputStream(fcmKey.get().getBytes());
        final FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(targetStream))
                .setDatabaseUrl(fcmDatabaseUrl)
                .build();

        FirebaseApp.initializeApp(options);
        LOG.debug("Connected to Firebase with service token.");
    }

    @Override
    public void sendPushNotification(String token, Notification notification) {

        if (token == null || notification == null) {
            return;
        }

        final Message message = Message.builder()
                .setToken(token)
                .setNotification(notification)
                .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                .build();

        String response;
        try {
            response = FirebaseMessaging.getInstance().send(message);
        } catch (Exception ex) {
            LOG.error("Failed to send push notification to token: {} - Msg: {}", token, ex.getMessage());
            return;
        }

        LOG.debug("Sent push notification with ID: {}", response);
    }
}
