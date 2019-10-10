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

    /**
     * Obtains the last settlement (pending or completed) and its transaction list.
     * Exception will be thrown if that last settlement was manual. This should be disabled on the Stripe account.
     *
     * @return Metadata plus transaction list.
     */
    StripeGatewayImpl.SettlementInfo getSettlementInfo();

    /**
     * Update customer default source to new type.
     * Tokens may only be used once. Use it here and then charge the customer directly.
     *
     * @param stripeCustomerId Customer to update.
     * @param stripePaymentToken Token to use for payment device.
     */
    void updateCustomerPaymentMethod(String stripeCustomerId, String stripePaymentToken);
}
