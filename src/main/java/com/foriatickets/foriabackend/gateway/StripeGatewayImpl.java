package com.foriatickets.foriabackend.gateway;

import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openapitools.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Profile("!mock")
@Service
public class StripeGatewayImpl implements StripeGateway {

    private static final String DESCRIPTION = "Tickets purchased via Foria Technologies, Inc.";
    private static final String DESCRIPTOR = "TICKETS";

    private static final Logger LOG = LogManager.getLogger();

    public StripeGatewayImpl(@Value("${stripeApiKey:corp_foria_stripe_secret_key}") String stripeApiKeyId, @Autowired AWSSecretsManagerGateway awsSecretsManagerGateway) {

        Optional<String> stripeApiKeyOptional = awsSecretsManagerGateway.getSecretString(stripeApiKeyId);
        if (!stripeApiKeyOptional.isPresent()) {
            LOG.error("Stripe API key not found in AWS Secrets Manager! Key ID: {}", stripeApiKeyId);
            throw new RuntimeException("Stripe API key not found in AWS Secrets Manager!");
        }

        LOG.info("Loaded Stripe API key");
        Stripe.apiKey = stripeApiKeyOptional.get();
    }

    @Override
    public Customer createStripeCustomer(User user, String paymentToken) {

        if (user == null || StringUtils.isEmpty(paymentToken)) {
            LOG.error("Attempted to create Stripe user with null data!");
            throw new RuntimeException("Attempted to create Stripe user with null data!");
        }

        final String userId = user.getId().toString();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("foria_id", userId);
        metadata.put("auth0_id", user.getAuth0Id());

        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("description", "Customer created via Foria ticketing application. Foria ID: " + userId);
        customerParams.put("name", user.getFirstName() + " " + user.getLastName());
        customerParams.put("email", user.getEmail());
        customerParams.put("metadata", metadata);

        customerParams.put("source", paymentToken);

        Customer customer;
        try {
            customer = Customer.create(customerParams);
        } catch (CardException e) {

            LOG.info("Failed to charge payment method. Decline Code: {} - Error Message: {}", e.getDeclineCode(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOG.error("ERROR: Creating Stripe customer! Error message: {} | userID: {}", e.getMessage(), userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to charge payment method. Please contact support.");
        }

        LOG.info("Created stripe customer with Id: {}", customer.getId());
        return customer;
    }

    @Override
    public Charge chargeCustomer(String stripeCustomerId, String stripeToken, UUID orderId, BigDecimal amount, String currencyCode) {

        if (StringUtils.isEmpty(stripeCustomerId) || StringUtils.isEmpty(currencyCode) || amount == null) {
            LOG.error("Attempted to charge Stripe user with null data!");
            throw new RuntimeException("Attempted to charge Stripe user with null data!");
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            LOG.error("Amount charged must be greater than 0!");
            throw new RuntimeException("Amount charged must be greater than 0!");
        }

        //Stripe requires the value to not contain a decimal.
        if (amount.scale() > 0) {
            amount = amount.movePointRight(amount.scale()).stripTrailingZeros();
        }

        LOG.debug("Stripe amount to charge is: {}{}", amount, currencyCode);

        //Update customer default source to new type.
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("source", stripeToken);

        Customer customer;
        try {
            customer = Customer.retrieve(stripeCustomerId);
            customer.update(customerParams);
        } catch (CardException e) {

            LOG.info("Failed to save payment method to customer. Decline Code: {} - Error Message: {}", e.getDeclineCode(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOG.error("ERROR: Setting default payment method! Error message: {} | userID: {}", e.getMessage(), stripeCustomerId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to update default payment method. Please contact support.");
        }

        Map<String, Object> chargeParams = new HashMap<>();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("order_id", orderId.toString());

        chargeParams.put("amount", amount);
        chargeParams.put("currency", currencyCode);
        chargeParams.put("source", stripeToken);
        chargeParams.put("customer", stripeCustomerId);
        chargeParams.put("metadata", metadata);
        chargeParams.put("description", DESCRIPTION);
        chargeParams.put("statement_descriptor", DESCRIPTOR);

        Charge charge;

        try {
            charge = Charge.create(chargeParams);
        } catch (CardException e) {
            LOG.info("Failed to charge payment method. Decline Code: {} - Error Message: {}", e.getDeclineCode(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOG.error("ERROR: Error charging Stripe customer! Error message: {} | customerID: {}", e.getMessage(), stripeCustomerId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to charge payment method. Please contact support.");
        }

        LOG.info("Charged customer with amount: {} {} - stripeId: {} ", amount, currencyCode, stripeCustomerId);
        return charge;
    }
}

