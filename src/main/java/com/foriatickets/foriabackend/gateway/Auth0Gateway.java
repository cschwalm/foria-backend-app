package com.foriatickets.foriabackend.gateway;

public interface Auth0Gateway {

    /**
     * Send an email to the specified user that asks them to click a link to verify their email address.
     */
    void resendUserVerificationEmail();
}
