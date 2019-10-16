package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.EventEntity;
import com.foriatickets.foriabackend.entities.TicketFeeConfigEntity;
import com.foriatickets.foriabackend.entities.TicketTypeConfigEntity;
import com.foriatickets.foriabackend.repositories.EventRepository;
import com.foriatickets.foriabackend.repositories.TicketTypeConfigRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openapitools.model.OrderTotal;
import org.openapitools.model.TicketLineItem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Transactional
public class CalculationServiceImpl implements CalculationService {

    static class PriceCalculationInfo {

        BigDecimal ticketSubtotal;
        BigDecimal feeSubtotal;
        BigDecimal paymentFeeSubtotal;
        BigDecimal grandTotal;
        String currencyCode;

        BigDecimal issuerFeeSubtotal;
        BigDecimal venueFeeSubtotal;
    }

    private static final BigDecimal STRIPE_PERCENT_FEE = BigDecimal.valueOf(0.029);
    private static final BigDecimal STRIPE_FLAT_FEE = BigDecimal.valueOf(0.30);

    private static final Logger LOG = LogManager.getLogger();

    private final EventRepository eventRepository;
    private final TicketTypeConfigRepository ticketTypeConfigRepository;

    public CalculationServiceImpl(EventRepository eventRepository, TicketTypeConfigRepository ticketTypeConfigRepository) {
        this.eventRepository = eventRepository;
        this.ticketTypeConfigRepository = ticketTypeConfigRepository;
    }

    @Override
    public PriceCalculationInfo calculateFees(final int numPaidTickets, final BigDecimal ticketSubtotal, final Set<TicketFeeConfigEntity> feeSet) {

        //Group fees by type.
        Set<TicketFeeConfigEntity> percentFeeSet = new HashSet<>();
        Set<TicketFeeConfigEntity> flatFeeSet = new HashSet<>();

        feeSet.forEach(fee -> {

            switch (fee.getMethod()) {

                case FLAT:
                    flatFeeSet.add(fee);
                    break;

                case PERCENT:
                    percentFeeSet.add(fee);
                    break;

                default:
                    LOG.warn("Unknown fee method: {} Skipping.", fee.getMethod());
            }
        });

        BigDecimal ticketFeeAmount;
        BigDecimal issuerFeeAmount;
        BigDecimal venueFeeAmount;

        BigDecimal paymentFeeAmount;
        BigDecimal grandTotal;

        //Summate percent fees and apply.
        BigDecimal feePercentToApply = BigDecimal.ZERO;
        BigDecimal feePercentIssuerToApply = BigDecimal.ZERO;
        BigDecimal feePercentVenueToApply = BigDecimal.ZERO;

        for (TicketFeeConfigEntity feeConfigEntity : percentFeeSet) {

            if (feeConfigEntity.getType() == TicketFeeConfigEntity.FeeType.ISSUER) {
                feePercentIssuerToApply = feePercentIssuerToApply.add(feeConfigEntity.getAmount());
            } else if (feeConfigEntity.getType() == TicketFeeConfigEntity.FeeType.VENUE) {
                feePercentVenueToApply = feePercentVenueToApply.add(feeConfigEntity.getAmount());
            }

            feePercentToApply = feePercentToApply.add(feeConfigEntity.getAmount());
        }

        ticketFeeAmount = ticketSubtotal.multiply(feePercentToApply);
        issuerFeeAmount = ticketSubtotal.multiply(feePercentIssuerToApply);
        venueFeeAmount = ticketSubtotal.multiply(feePercentVenueToApply);

        //Summate flat fees and apply.
        BigDecimal feeFlatToApplyPerTicket = BigDecimal.ZERO;
        BigDecimal feeFlatIssuerToApplyPerTicket = BigDecimal.ZERO;
        BigDecimal feeFlatVenueToApplyPerTicket = BigDecimal.ZERO;

        for (TicketFeeConfigEntity feeConfigEntity : flatFeeSet) {

            feeFlatToApplyPerTicket = feeFlatToApplyPerTicket.add(feeConfigEntity.getAmount());

            if (feeConfigEntity.getType() == TicketFeeConfigEntity.FeeType.ISSUER) {
                feeFlatIssuerToApplyPerTicket = feeFlatIssuerToApplyPerTicket.add(feeConfigEntity.getAmount());
            } else if (feeConfigEntity.getType() == TicketFeeConfigEntity.FeeType.VENUE) {
                feeFlatVenueToApplyPerTicket = feeFlatVenueToApplyPerTicket.add(feeConfigEntity.getAmount());
            }
        }

        //Calculate Flat Fees Per Type
        BigDecimal flatFeeAmount = feeFlatToApplyPerTicket.multiply(BigDecimal.valueOf(numPaidTickets));
        BigDecimal flatFeeIssuerAmount = feeFlatIssuerToApplyPerTicket.multiply(BigDecimal.valueOf(numPaidTickets));
        BigDecimal flatFeeVenueAmount = feeFlatVenueToApplyPerTicket.multiply(BigDecimal.valueOf(numPaidTickets));

        //Add Flat fees to previous percent fees.
        ticketFeeAmount = ticketFeeAmount.add(flatFeeAmount).setScale(2, BigDecimal.ROUND_HALF_UP);
        issuerFeeAmount = issuerFeeAmount.add(flatFeeIssuerAmount).setScale(2, BigDecimal.ROUND_HALF_UP);
        venueFeeAmount = venueFeeAmount.add(flatFeeVenueAmount).setScale(2, BigDecimal.ROUND_HALF_UP);

        //Apply payment vendor fee.
        //charge_amount = (subtotal + 0.30) / (1 - 2.90 / 100)
        BigDecimal subtotalWithFees = ticketSubtotal.add(ticketFeeAmount);
        if (subtotalWithFees.compareTo(BigDecimal.ZERO) <= 0) {
            grandTotal = BigDecimal.ZERO;
            paymentFeeAmount = BigDecimal.ZERO;
        } else {
            grandTotal = (subtotalWithFees.add(STRIPE_FLAT_FEE))
                    .divide(
                            (BigDecimal.ONE.subtract(STRIPE_PERCENT_FEE)), BigDecimal.ROUND_HALF_UP);
            paymentFeeAmount = grandTotal.subtract(subtotalWithFees);
        }

        PriceCalculationInfo result = new PriceCalculationInfo();
        result.ticketSubtotal = ticketSubtotal.setScale(2, RoundingMode.FLOOR);

        result.feeSubtotal = ticketFeeAmount.setScale(2, RoundingMode.FLOOR);
        result.issuerFeeSubtotal = issuerFeeAmount.setScale(2, RoundingMode.FLOOR);
        result.venueFeeSubtotal = venueFeeAmount.setScale(2, RoundingMode.FLOOR);

        result.grandTotal = grandTotal.setScale(2, RoundingMode.FLOOR);
        result.paymentFeeSubtotal = paymentFeeAmount.setScale(2, RoundingMode.FLOOR);
        return result;
    }

    @Override
    public OrderTotal calculateOrderTotal(UUID eventId, List<TicketLineItem> orderConfig) {

        if (orderConfig == null || eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calculate order total request is missing required data.");
        }

        //Check that event exists.
        Optional<EventEntity> eventEntityOptional = eventRepository.findById(eventId);
        if (!eventEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID is invalid.");
        }

        //Calculate order total.
        OrderTotal orderTotal = new OrderTotal();
        PriceCalculationInfo priceCalculationInfo = calculateTotalPrice(eventId, orderConfig);

        BigDecimal subtotal = priceCalculationInfo.ticketSubtotal;
        BigDecimal fees = priceCalculationInfo.feeSubtotal.add(priceCalculationInfo.paymentFeeSubtotal);
        BigDecimal total = priceCalculationInfo.grandTotal;

        orderTotal.setSubtotal(subtotal.toPlainString());
        orderTotal.setFees(fees.toPlainString());
        orderTotal.setGrandTotal(total.toPlainString());
        orderTotal.setCurrency(priceCalculationInfo.currencyCode);

        orderTotal.setSubtotalCents(subtotal.movePointRight(subtotal.scale()).stripTrailingZeros().toPlainString());
        orderTotal.setFeesCents(fees.movePointRight(fees.scale()).stripTrailingZeros().toPlainString());
        orderTotal.setGrandTotalCents(total.movePointRight(total.scale()).stripTrailingZeros().toPlainString());

        return orderTotal;
    }

    public PriceCalculationInfo calculateTotalPrice(UUID eventId, List<TicketLineItem> orderConfig) {

        String currencyCode = "USD";
        BigDecimal ticketSubtotal = BigDecimal.ZERO;

        //Load price config along with fees for event.
        Optional<EventEntity> eventEntityOptional = eventRepository.findById(eventId);
        if (!eventEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID is invalid.");
        }
        EventEntity eventEntity = eventEntityOptional.get();
        Set<TicketFeeConfigEntity> feeSet = eventEntity.getTicketFeeConfig();

        //Load ticket price configs.
        //Summate over tickets to calculate sub-total.
        int numPaidTickets = 0;
        for (TicketLineItem ticketLineItem : orderConfig) {
            UUID ticketTypeConfigId = ticketLineItem.getTicketTypeId();
            Optional<TicketTypeConfigEntity> ticketTypeConfigEntityOptional = ticketTypeConfigRepository.findById(ticketTypeConfigId);

            if (ticketTypeConfigEntityOptional.isPresent()) {
                TicketTypeConfigEntity ticketTypeConfigEntity = ticketTypeConfigEntityOptional.get();

                //Skip free tickets.
                if (ticketTypeConfigEntity.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                final int orderAmount = ticketLineItem.getAmount();
                BigDecimal amountForType = new BigDecimal(orderAmount);
                BigDecimal ticketPriceForType = ticketTypeConfigEntity.getPrice().multiply(amountForType);
                ticketSubtotal = ticketSubtotal.add(ticketPriceForType);
                currencyCode = ticketTypeConfigEntity.getCurrency();
                numPaidTickets += amountForType.toBigInteger().intValue();
            }
        }

        PriceCalculationInfo priceCalculationInfo = calculateFees(numPaidTickets, ticketSubtotal, feeSet);
        priceCalculationInfo.currencyCode = currencyCode;
        return priceCalculationInfo;
    }
}
