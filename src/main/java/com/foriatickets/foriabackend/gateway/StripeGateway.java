package com.foriatickets.foriabackend.gateway;

import com.stripe.model.Charge;
import com.stripe.model.Customer;
import org.openapitools.model.User;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Secure interface to communicate with Stripe. This gateway takes care of loading Stripe secret for the correct
 * environment.
 *
 * See: https://stripe.com/docs/api
 *
 * @author Corbin Schwalm
 */
public interface StripeGateway {

    /**
     * Charges the customer with the specified amount and currency.
     * Throws a RuntimeException if the supplied card data is invalid.
     *
     * @param stripeCustomerId id
     * @param stripeToken The token from customer to charge.
     * @param orderId Order id.
     * @param amount Rounded to two decimal places and set to floor.
     * @param currencyCode Currency to bill in.
     * @return A successful charge object.
     */
    Charge chargeCustomer(String stripeCustomerId, String stripeToken, UUID orderId, BigDecimal amount, String currencyCode);

    /**
     * Attempts to create a customer in Stripe. This should only be called once per system user.
     * Throws a RuntimeException if the supplied card data is invalid.
     *
     * @param user The user info used to populate Stripe customer.
     * @param paymentToken Token obtained from client.
     * @return A created Stripe customer.
     */
    Customer createStripeCustomer(User user, String paymentToken);
}
