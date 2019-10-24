package com.foriatickets.foriabackend.gateway;

import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
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
import java.util.*;

@Profile("!mock")
@Service
public class StripeGatewayImpl implements StripeGateway {

    private static final int STRIPE_RETURN_AMOUNT = 100;

    /**
     * Allows for transactions to be packaged with settlement info.
     */
    @SuppressWarnings("WeakerAccess")
    public static class SettlementInfo {

        private Payout stripePayout;
        private List<BalanceTransaction> balanceTransactions;

        public Payout getStripePayout() {
            return stripePayout;
        }

        public void setStripePayout(Payout stripePayout) {
            this.stripePayout = stripePayout;
        }

        public List<BalanceTransaction> getBalanceTransactions() {
            return balanceTransactions;
        }

        public void setBalanceTransactions(List<BalanceTransaction> balanceTransactions) {
            this.balanceTransactions = balanceTransactions;
        }
    }

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
            LOG.warn("Attempted to create Stripe user with null data!");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attempted to create Stripe user with null data!");
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

        if (StringUtils.isEmpty(stripeToken) || StringUtils.isEmpty(stripeCustomerId) || StringUtils.isEmpty(currencyCode) || amount == null) {
            LOG.warn("Attempted to charge Stripe user with null data!");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout request is missing required data.");
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

        Map<String, Object> chargeParams = new HashMap<>();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("order_id", orderId.toString());

        chargeParams.put("amount", amount.toPlainString());
        chargeParams.put("currency", currencyCode);
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

    @Override
    public SettlementInfo getSettlementInfo() {

        final SettlementInfo settlementInfo = new SettlementInfo();

        final Map<String, Object> lastPayoutParams = new HashMap<>();
        lastPayoutParams.put("limit", "1");

        PayoutCollection payoutCollection;
        try {
            payoutCollection = Payout.list(lastPayoutParams);
        } catch (Exception e) {
            LOG.error("ERROR: Failed to obtain last payout! Error message: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR: Failed to obtain last payout!");
        }

        final List<Payout> payoutList = payoutCollection.getData();
        if (payoutList == null || payoutList.isEmpty()) {
            LOG.error("ERROR: Failed to obtain last payout! Payout not found.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR: Failed to obtain last payout! Payout not found.");
        }

        final Payout lastPayout = payoutList.get(0);
        if (!lastPayout.getAutomatic()) {
            LOG.error("ERROR: Unable to generate report. Last payout is manual.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR: Unable to generate report. Last payout is manual.");
        }

        settlementInfo.setStripePayout(lastPayout);

        final Map<String, Object> balanceTransactionParams = new HashMap<>();
        balanceTransactionParams.put("payout", lastPayout.getId());
        balanceTransactionParams.put("type", "charge");
        balanceTransactionParams.put("limit", STRIPE_RETURN_AMOUNT);

        final List<BalanceTransaction> balanceTransactions = new ArrayList<>();
        try {

            Iterable<BalanceTransaction> balanceTransactionItr = BalanceTransaction.list(balanceTransactionParams).autoPagingIterable();

            for (BalanceTransaction balanceTransaction : balanceTransactionItr) {
                balanceTransactions.add(balanceTransaction);
            }

        } catch (StripeException e) {
            LOG.error("ERROR: Unable to obtain balance transactions for last payout. Error message: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR: Unable to obtain balance transactions for last payout.");
        }

        settlementInfo.setBalanceTransactions(balanceTransactions);

        LOG.info("Obtained last payout with Id for EPOCH date: {} {}", lastPayout.getId(), lastPayout.getCreated());
        return settlementInfo;
    }

    @Override
    public Refund refundStripeCharge(String chargeRefId, BigDecimal amountToRefund) {

        if (StringUtils.isEmpty(chargeRefId) || amountToRefund == null) {
            LOG.warn("Attempted to refund Stripe charge with null data!");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund order is missing required parameters.");
        }

        long refundAmount = amountToRefund.movePointRight(amountToRefund.scale()).stripTrailingZeros().longValue();

        final Charge initialCharge;
        try {
            initialCharge = Charge.retrieve(chargeRefId);
        } catch (StripeException ex) {
            LOG.error("Failed to lookup Stripe charge. Charge Ref: {} - Error Message: {}", chargeRefId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        }

        if (initialCharge.getRefunded()) {
            LOG.info("Stripe charge: {} has already been fully refunded.", chargeRefId);
            return initialCharge.getRefunds().getData().get(0);
        }

        if (initialCharge.getAmountRefunded() > 0L) {
            LOG.warn("Stripe charge: {} has already been PARTIALLY refunded. Amount: {}", chargeRefId, initialCharge.getAmountRefunded());
            refundAmount = initialCharge.getAmount() - initialCharge.getAmountRefunded();
        }

        final Map<String, Object> refundParams = new HashMap<>();
        refundParams.put("charge", chargeRefId);
        refundParams.put("amount", String.valueOf(refundAmount));
        refundParams.put("reason", "requested_by_customer");
        refundParams.put("metadata", initialCharge.getMetadata());

        Refund refund;
        try {
            refund = Refund.create(refundParams);
        } catch (StripeException ex) {
            LOG.error("Failed to refund Stripe charge. Charge Ref: {} - Error Message: {}", chargeRefId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        }

        LOG.info("Successfully refunded Stripe transaction with refund ID: {}", refund.getId());
        return refund;
    }

    @Override
    public void updateCustomerPaymentMethod(String stripeCustomerId, String stripePaymentToken) {

        if (StringUtils.isEmpty(stripeCustomerId) || StringUtils.isEmpty(stripePaymentToken)) {
            LOG.error("Attempted to update Stripe user with null data!");
            throw new RuntimeException("Attempted to update Stripe user with null data!");
        }

        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("source", stripePaymentToken);

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
    }
}

