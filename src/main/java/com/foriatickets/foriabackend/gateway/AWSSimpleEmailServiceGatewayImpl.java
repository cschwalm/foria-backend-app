package com.foriatickets.foriabackend.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesAsyncClient;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.MessageTag;
import software.amazon.awssdk.services.ses.model.SendTemplatedEmailRequest;
import software.amazon.awssdk.services.ses.model.SendTemplatedEmailResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Profile("!mock")
public class AWSSimpleEmailServiceGatewayImpl implements AWSSimpleEmailServiceGateway {

    private static final String SOURCE_EMAIL_ADDRESS = "Foria <do-not-reply@foriatickets.com>";

    private static final Logger LOG = LogManager.getLogger();
    private SesAsyncClient sesAsyncClient;

    public AWSSimpleEmailServiceGatewayImpl() {

        sesAsyncClient = SesAsyncClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    public void sendEmailFromTemplate(String toAddress, String templateName, Map<String, String> templateData) {

        if (templateName == null || toAddress == null || templateData == null || templateData.isEmpty()) {
            LOG.error("Attempted to send email with null values.");
            return;
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
                .build();

        CompletableFuture<SendTemplatedEmailResponse> resultFuture = sesAsyncClient.sendTemplatedEmail(sendTemplatedEmailRequest);
        resultFuture.thenAccept(r -> LOG.info("SES Message accepted with messageId: {}", r.messageId()));
    }
}
