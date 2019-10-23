package com.foriatickets.foriabackend.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesAsyncClient;
import software.amazon.awssdk.services.ses.model.*;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

@Service
@Profile("!mock")
public class AWSSimpleEmailServiceGatewayImpl implements AWSSimpleEmailServiceGateway {

    private static final String SOURCE_EMAIL_ADDRESS = "Foria <do-not-reply@foriatickets.com>";
    private static final String SOURCE_ADMIN_EMAIL_ADDRESS = "Foria Admin <admin@foriatickets.com>";

    private static final String SNS_CONFIG_SET = "Foria-Main";

    private static final Logger LOG = LogManager.getLogger();
    private SesAsyncClient sesAsyncClient;

    @Value("${report.email}")
    private String reportEmailAddress;

    public AWSSimpleEmailServiceGatewayImpl() {

        sesAsyncClient = SesAsyncClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    public void sendEmailFromTemplate(String toAddress, String templateName, Map<String, String> templateData) {

        if (templateName == null || toAddress == null) {
            LOG.error("Attempted to send email with null values.");
            return;
        }

        if (templateData == null) {
            templateData = new HashMap<>();
        }

        final ObjectMapper mapper = new ObjectMapper();
        String jsonTemplateData;
        try {
            jsonTemplateData = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(templateData);
        } catch (Exception ex) {
            LOG.error("Failed to parse template data to map for SES. Msg: {}", ex.getMessage());
            return;
        }

        final Destination destination = Destination.builder()
                .toAddresses(toAddress)
                .build();

        final MessageTag appTag = MessageTag.builder()
                .name("app")
                .value("foria")
                .build();

        final SendTemplatedEmailRequest sendTemplatedEmailRequest = SendTemplatedEmailRequest.builder()
                .destination(destination)
                .source(SOURCE_EMAIL_ADDRESS)
                .template(templateName)
                .templateData(jsonTemplateData)
                .tags(appTag)
                .configurationSetName(SNS_CONFIG_SET)
                .build();

        CompletableFuture<SendTemplatedEmailResponse> resultFuture = sesAsyncClient.sendTemplatedEmail(sendTemplatedEmailRequest);
        resultFuture.thenAccept(r -> LOG.info("SES Message accepted with messageId: {}", r.messageId()));
    }

    @Override
    public void sendInternalReport(String reportName, String bodyText, List<ReportAttachment> reports) {

        if (reportName == null) {
            LOG.error("Attempted to send email with null values.");
            return;
        }

        final String subjectText = "INTERNAL FORIA REPORT: " + reportName;

        final MessageTag appTag = MessageTag.builder()
                .name("app")
                .value("foria")
                .build();

        // Create a new MimeMessage object.
        final Session session = Session.getDefaultInstance(new Properties());
        final MimeMessage message = new MimeMessage(session);

        try {

            // Add subject, from and to lines.
            message.setSubject(subjectText, "us-ascii");
            message.setFrom(new InternetAddress(SOURCE_ADMIN_EMAIL_ADDRESS));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(reportEmailAddress));
            message.setSentDate(DateTime.now().toDate());

            // Create a multipart/alternative child container.
            MimeMultipart msg_body = new MimeMultipart("alternative");

            // Create a wrapper for the HTML and text parts.
            MimeBodyPart wrap = new MimeBodyPart();

            // Define the text part.
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(bodyText, "text/plain; charset=us-ascii");
            msg_body.addBodyPart(textPart);

            // Add the child container to the wrapper object.
            wrap.setContent(msg_body);

            // Create a multipart/mixed parent container.
            MimeMultipart msg = new MimeMultipart("mixed");

            // Add the parent container to the message.
            message.setContent(msg);

            // Add the multipart/alternative part to the message.
            msg.addBodyPart(wrap);

            // Define the attachment if exists
            if (reports != null && !reports.isEmpty()) {

                for (ReportAttachment reportAttachment : reports) {
                    MimeBodyPart att = new MimeBodyPart();
                    DataSource ds = new ByteArrayDataSource(reportAttachment.reportDataArray, reportAttachment.reportMimeType);
                    att.setDataHandler(new DataHandler(ds));
                    att.setFileName(reportAttachment.reportFilename);

                    // Add the attachment to the message.
                    msg.addBodyPart(att);
                }
            }

            // Send the email.
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            message.writeTo(outputStream);
            RawMessage rawMessage = RawMessage.builder()
                    .data(
                            SdkBytes.fromByteBuffer(ByteBuffer.wrap(outputStream.toByteArray()))
                    )
                    .build();

            final SendRawEmailRequest rawEmailRequest = SendRawEmailRequest.builder()
                    .rawMessage(rawMessage)
                    .tags(appTag)
                    .configurationSetName(SNS_CONFIG_SET)
                    .build();

            SendRawEmailResponse resultFuture = sesAsyncClient.sendRawEmail(rawEmailRequest).get();
            LOG.info("SES Message accepted with messageId: {}", resultFuture.messageId());

        } catch (Exception ex) {
            LOG.error("Failed to send internal report with name: {}", reportName);
            ex.printStackTrace();
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }
}
