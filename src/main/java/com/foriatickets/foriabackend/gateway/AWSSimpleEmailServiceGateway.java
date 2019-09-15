package com.foriatickets.foriabackend.gateway;

import java.util.Map;

/**
 * Exposes a gateway to send template email via AWS simple email service.
 *
 * @author Corbin Schwalm
 */
public interface AWSSimpleEmailServiceGateway {

    String EMAIL_TRANSFER_PENDING_TEMPLATE = "email_transfer_pending";
    String TICKETS_PURCHASED_TEMPLATE = "ticket_purchase_email";
    String TRANSFERER_EMAIL_TRANSFER_COMPLETE = "transferer_email_transfer_complete";

    /**
     * Email template must be loaded into SES account.
     * Method does not indicate failure. Only the request ID of the call.
     *
     * @param toAddress Address to receive email.
     * @param templateName Name loaded into template-manager.
     * @param templateData Required handlebar placeholders to replace.
     */
    void sendEmailFromTemplate(String toAddress, String templateName, Map<String, String> templateData);
}
