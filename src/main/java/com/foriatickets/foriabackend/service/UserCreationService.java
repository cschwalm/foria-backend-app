package com.foriatickets.foriabackend.service;

import org.openapitools.model.User;

/**
 * Separate service designed to preform actions on users that have not been fully created keeping the main User service
 * clean.
 *
 * @author Corbin Schwalm
 */
public interface UserCreationService {

    /**
     * Endpoint should be called from Auth0 only. This keeps Auth0 and our system in sync.
     *
     * @param newUser Object to store.
     * @return Formed object with ID.
     */
    User createUser(User newUser);

    /**
     * Obtains info for currently logged in user.
     * @return user
     */
    User getUser();

    /**
     * Registers the token obtained for FCM. Allows for targeted device messaging.
     * @param deviceToken Obtained from FCM.
     */
    void registerDeviceToken(String deviceToken);
}
