package com.foriatickets.foriabackend.gateway;

import java.util.List;
import java.util.Map;

/**
 * Exposes a gateway to send template email via AWS simple email service.
 *
 * @author Corbin Schwalm
 */
public interface AWSSimpleEmailServiceGateway {

    /**
     * Contains required metadata and helper methods to construct CSV reports and
     * send via email.
     */
    class ReportAttachment {

        public byte[] reportDataArray;
        public String reportFilename;
        String reportMimeType = "text/csv; charset=UTF-8";
    }

    String TRANSFEREE_PENDING_EMAIL = "transferee_pending_email";
    String TRANSFEROR_PENDING_EMAIL = "transferor_pending_email";
    String TRANSFEREE_COMPLETE_EMAIL = "transferee_complete_email";
    String TRANSFEROR_COMPLETE_EMAIL = "transferor_complete_email";
    String TICKET_PURCHASE_EMAIL = "ticket_purchase_email";
    String RESELL_TICKET_PURCHASE_EMAIL = "resell_ticket_purchase_confirmation";

    /**
     * Email template must be loaded into SES account.
     * Method does not indicate failure. Only the request ID of the call.
     *
     * @param toAddress Address to receive email.
     * @param templateName Name loaded into template-manager.
     * @param templateData Required handlebar placeholders to replace.
     */
    void sendEmailFromTemplate(String toAddress, String templateName, Map<String, String> templateData);

    /**
     * Sends report to internal email address.
     * API uses legacy javax mail API to generate RAW email payload and sends it to AWS SES.
     *
     * @param reportName Filename of the report. Should include extension.
     * @param bodyText Body to go in text part.
     * @param reports Optional list of reports to attach.
     */
    void sendInternalReport(String reportName, String bodyText, List<ReportAttachment> reports);
}
