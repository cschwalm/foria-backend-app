package com.foriatickets.foriabackend.gateway;

import com.google.firebase.messaging.Notification;

/**
 * Interface to communication with Google Firebase.
 * Primary functionality is to send push notifications to client devices.
 *
 * @author Corbin Schwalm
 */
public interface FCMGateway {

    /**
     * Async send a push notification to device.
     *
     * @param token Device token registered from device.
     * @param notification Payload
     */
    void sendPushNotification(String token, Notification notification);
}
