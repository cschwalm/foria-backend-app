package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.TicketFeeConfigEntity;
import org.openapitools.model.OrderTotal;
import org.openapitools.model.TicketLineItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Utility service to assist with math regarding ticket calculations.
 */
public interface CalculationService {

    /**
     * Accepts subtotal, list of both flat and percent fees, and then calculates the subtotal with fees applied.
     * Payment processor fee is applied last to pass the entire amount on to the customer.
     *
     * @param numPaidTickets Number of non-free tickets. Used for FLAT fee calculation.
     * @param ticketSubtotal Subtotal to apply fees on.
     * @param feeSet List of percent fees to calculate on.
     * @param doInactiveCheck Indicates that if feeConfig is inactive that it will be skipped.
     * @return Object containing break down of fees.
     */
    CalculationServiceImpl.PriceCalculationInfo calculateFees(final int numPaidTickets, final BigDecimal ticketSubtotal, final Set<TicketFeeConfigEntity> feeSet, boolean doInactiveCheck);

    /**
     * Calculates the order total to display to the user.
     * This uses the same logic that checkout uses to ensure the price is the same.
     *
     * @param eventId eventId
     * @param orderConfig List of tickets to buy.
     */
    OrderTotal calculateOrderTotal(UUID eventId, List<TicketLineItem> orderConfig);

    /**
     * Loads event from database and calculates the order total uses the internal fee calculation methods.
     *
     * @param eventId eventId
     * @param orderConfig List of tickets to buy.
     * @return Object containing break down of fees.
     */
    CalculationServiceImpl.PriceCalculationInfo calculateTotalPrice(UUID eventId, List<TicketLineItem> orderConfig);
}
