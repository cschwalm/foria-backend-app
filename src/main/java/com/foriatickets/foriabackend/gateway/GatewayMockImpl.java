package com.foriatickets.foriabackend.gateway;

import com.stripe.model.Charge;
import com.stripe.model.Customer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openapitools.model.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Profile("mock")
@Service
public class GatewayMockImpl implements GatewayMock {

    private static final Logger LOG = LogManager.getLogger();

    @Override
    public Charge chargeCustomer(String stripeCustomerId, String stripeToken, UUID orderId, BigDecimal amount, String currencyCode) {
        Charge charge = new Charge();
        charge.setAmount(amount.toBigInteger().longValueExact());
        charge.setCurrency(currencyCode);
        charge.setCustomer(stripeCustomerId);
        charge.setId("charge_mock_id_" + UUID.randomUUID());
        LOG.info("Stripe mock in use. Customer not charged.");
        return charge;
    }

    @Override
    public Customer createStripeCustomer(User user, String paymentToken) {

        Customer customer = new Customer();
        customer.setDefaultSource(paymentToken);
        customer.setId("customer_mock_id_" + UUID.randomUUID());
        LOG.info("Stripe mock in use. Customer not created.");
        return customer;
    }

    @Override
    public void updateCustomerPaymentMethod(String stripeCustomerId, String stripePaymentToken) {
        LOG.info("Stripe mock in use. Customer not updated.");
    }
}
