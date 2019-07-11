package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.StripeGateway;
import com.foriatickets.foriabackend.repositories.*;
import com.stripe.model.Charge;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.openapitools.model.TicketLineItem;
import org.openapitools.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class TicketServiceImpl implements TicketService {

    private static final BigDecimal STRIPE_PERCENT_FEE = BigDecimal.valueOf(0.029);
    private static final BigDecimal STRIPE_FLAT_FEE = BigDecimal.valueOf(0.30);
    private static final int MAX_TICKETS_PER_ORDER = 10;

    static class PriceCalculationInfo {

        BigDecimal ticketSubtotal;
        BigDecimal feeSubtotal;
        BigDecimal paymentFeeSubtotal;
        BigDecimal grandTotal;
        String currencyCode;
    }

    private final ModelMapper modelMapper;

    private final EventRepository eventRepository;

    private final OrderFeeEntryRepository orderFeeEntryRepository;

    private final OrderTicketEntryRepository orderTicketEntryRepository;

    private final OrderRepository orderRepository;

    private final UserRepository userRepository;

    private final TicketTypeConfigRepository ticketTypeConfigRepository;

    private final TicketRepository ticketRepository;

    private static final Logger LOG = LogManager.getLogger();

    private final StripeGateway stripeGateway;

    public TicketServiceImpl(ModelMapper modelMapper, EventRepository eventRepository, OrderRepository orderRepository, UserRepository userRepository, TicketTypeConfigRepository ticketTypeConfigRepository, TicketRepository ticketRepository, StripeGateway stripeGateway, OrderFeeEntryRepository orderFeeEntryRepository, OrderTicketEntryRepository orderTicketEntryRepository) {
        this.modelMapper = modelMapper;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.ticketTypeConfigRepository = ticketTypeConfigRepository;
        this.ticketRepository = ticketRepository;
        this.stripeGateway = stripeGateway;
        this.orderFeeEntryRepository = orderFeeEntryRepository;
        this.orderTicketEntryRepository = orderTicketEntryRepository;
    }

    @Override
    public void checkoutOrder(String auth0Id, String paymentToken, UUID eventId, List<TicketLineItem> orderConfig) {

        if (StringUtils.isEmpty(auth0Id) || StringUtils.isEmpty(paymentToken) || orderConfig == null || eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout request is missing required data.");
        }

        int totalTicketCount = 0;
        for (TicketLineItem ticketLineItem : orderConfig) {
            totalTicketCount += ticketLineItem.getAmount();
        }
        if (totalTicketCount > MAX_TICKETS_PER_ORDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Max of " + MAX_TICKETS_PER_ORDER + " tickets per order allowed.");
        }

        //Load user from Auth0 token.
        UserEntity userEntity = userRepository.findByAuth0Id(auth0Id);
        if (userEntity == null) {

            LOG.error("Attempted to complete checkout with non-mapped auth0Id: {}", auth0Id);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User must be created in Foria system.");
        }
        User user = modelMapper.map(userEntity, User.class);

        //Load price config along with fees for event.
        Optional<EventEntity> eventEntityOptional = eventRepository.findById(eventId);
        if (!eventEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID is invalid.");
        }
        EventEntity eventEntity = eventEntityOptional.get();
        Set<TicketFeeConfigEntity> ticketFeeConfigEntitySet = eventEntity.getTicketFeeConfig();

        //Generate unique order ID.
        final UUID orderId = UUID.randomUUID();

        //Calculate order total.
        PriceCalculationInfo priceCalculationInfo = calculateTotalPrice(eventId, orderConfig);

        //Create order entry.
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setId(orderId);
        orderEntity.setPurchaser(userEntity);
        orderEntity.setOrderTimestamp(OffsetDateTime.now());
        orderEntity.setTotal(priceCalculationInfo.grandTotal);
        orderEntity.setCurrency(priceCalculationInfo.currencyCode);
        orderEntity = orderRepository.save(orderEntity);

        //Validate ticket config IDs are valid and issue tickets.
        for (TicketLineItem ticketLineItem : orderConfig) {
            UUID ticketTypeConfigId = ticketLineItem.getTicketTypeId();
            boolean doesExist = ticketTypeConfigRepository.existsById(ticketTypeConfigId);
            if (!doesExist) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket type config is invalid.");
            }

            TicketEntity issuedTicket;
            for (int i = 0; i < ticketLineItem.getAmount(); i++) {

                issuedTicket = issueTicket(userEntity.getId(), eventId, ticketTypeConfigId);

                OrderTicketEntryEntity orderTicketEntryEntity = new OrderTicketEntryEntity();
                orderTicketEntryEntity.setOrderEntity(orderEntity);
                orderTicketEntryEntity.setTicketEntity(issuedTicket);
                orderTicketEntryRepository.save(orderTicketEntryEntity);
            }
        }

        for (TicketFeeConfigEntity ticketFeeConfigEntity : ticketFeeConfigEntitySet) {
            OrderFeeEntryEntity orderFeeEntryEntity = new OrderFeeEntryEntity();
            orderFeeEntryEntity.setOrderEntity(orderEntity);
            orderFeeEntryEntity.setTicketFeeConfigEntity(ticketFeeConfigEntity);
            orderFeeEntryRepository.save(orderFeeEntryEntity);
        }

        //Charge payment method - tickets have been issued. Create Stripe user if it doesn't exist.
        String stripeCustomerId;
        if (StringUtils.isEmpty(userEntity.getStripeId())) {
            stripeCustomerId = stripeGateway.createStripeCustomer(user, paymentToken).getId();
            userEntity.setStripeId(stripeCustomerId);
            userRepository.save(userEntity);
        } else {
            stripeCustomerId = userEntity.getStripeId();
        }

        Charge chargeResult = stripeGateway.chargeCustomer(stripeCustomerId, orderEntity.getId(), priceCalculationInfo.grandTotal, priceCalculationInfo.currencyCode);
        orderEntity.setChargeReferenceId(chargeResult.getId());
        orderRepository.save(orderEntity);
        LOG.info("Stripe customer (ID: {}) charged: {}{} with chargeID: {}",
                stripeCustomerId, priceCalculationInfo.grandTotal, priceCalculationInfo.currencyCode, chargeResult.getId());
    }

    @Override
    public TicketEntity issueTicket(UUID purchaserId, UUID eventId, UUID ticketTypeId) {

        Validate.notNull(eventId, "eventId must not be null");
        Validate.notNull(purchaserId, "purchaserId must not be null");
        Validate.notNull(ticketTypeId, "ticketTypeId must not be null");

        Optional<EventEntity> eventEntityOptional = eventRepository.findById(eventId);
        if (!eventEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid event ID");
        }

        Optional<UserEntity> userEntityOptional = userRepository.findById(purchaserId);
        if (!userEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid user ID");
        }

        Optional<TicketTypeConfigEntity> ticketTypeConfigEntityOptional = ticketTypeConfigRepository.findById(ticketTypeId);
        if (!ticketTypeConfigEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid ticket type ID");
        }

        EventEntity eventEntity = eventEntityOptional.get();
        UserEntity userEntity = userEntityOptional.get();
        TicketTypeConfigEntity ticketTypeConfigEntity = ticketTypeConfigEntityOptional.get();

        TicketEntity ticketEntity = new TicketEntity();
        ticketEntity.setEventEntity(eventEntity);
        ticketEntity.setOwnerEntity(userEntity);
        ticketEntity.setPurchaserEntity(userEntity);
        ticketEntity.setSecret(UUID.randomUUID().toString());
        ticketEntity.setTicketTypeConfigEntity(ticketTypeConfigEntity);
        ticketEntity.setStatus(TicketEntity.Status.ISSUED);
        ticketEntity.setIssuedDate(OffsetDateTime.now());

        ticketEntity = ticketRepository.save(ticketEntity);

        LOG.info("Issued ticket: {} for userID: {}", ticketEntity.getId(), userEntity.getId());
        return ticketEntity;
    }

    private PriceCalculationInfo calculateTotalPrice(UUID eventId, List<TicketLineItem> orderConfig) {

        //Group fees by type.
        Set<TicketFeeConfigEntity> percentFeeSet = new HashSet<>();
        Set<TicketFeeConfigEntity> flatFeeSet = new HashSet<>();

        String currencyCode = "USD";
        BigDecimal ticketSubtotal = BigDecimal.ZERO;

        //Load price config along with fees for event.
        Optional<EventEntity> eventEntityOptional = eventRepository.findById(eventId);
        if (!eventEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID is invalid.");
        }
        EventEntity eventEntity = eventEntityOptional.get();
        eventEntity.getTicketFeeConfig().forEach(fee -> {

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

        //Load ticket price configs.
        //Summate over tickets to calculate sub-total.
        for (TicketLineItem ticketLineItem : orderConfig) {
            UUID ticketTypeConfigId = ticketLineItem.getTicketTypeId();
            int orderAmount = ticketLineItem.getAmount();
            Optional<TicketTypeConfigEntity> ticketTypeConfigEntityOptional = ticketTypeConfigRepository.findById(ticketTypeConfigId);

            if (ticketTypeConfigEntityOptional.isPresent()) {
                TicketTypeConfigEntity ticketTypeConfigEntity = ticketTypeConfigEntityOptional.get();

                BigDecimal amountForType = new BigDecimal(orderAmount);
                BigDecimal ticketPriceForType = ticketTypeConfigEntity.getPrice().multiply(amountForType);
                ticketSubtotal = ticketSubtotal.add(ticketPriceForType);
                currencyCode = ticketTypeConfigEntity.getCurrency();
            }
        }

        PriceCalculationInfo priceCalculationInfo = calculateFees(ticketSubtotal, percentFeeSet, flatFeeSet);
        priceCalculationInfo.currencyCode = currencyCode;
        return priceCalculationInfo;
    }

    /**
     * Accepts subtotal, list of both flat and percent fees, and then calculates the subtotal with fees applied.
     * Payment processor fee is applied last to pass the entire amount on to the customer.
     *
     * @param ticketSubtotal Subtotal to apply fees on.
     * @param percentFeeSet List of percent fees to calculate on.
     * @param flatFeeSet List of flat fees.
     * @return Object containing break down of fees.
     */
    PriceCalculationInfo calculateFees(final BigDecimal ticketSubtotal, Set<TicketFeeConfigEntity> percentFeeSet, Set<TicketFeeConfigEntity> flatFeeSet) {

        BigDecimal ticketFeeAmount;
        BigDecimal paymentFeeAmount;
        BigDecimal grandTotal;

        //Summate percent fees and apply.
        BigDecimal feePercentToApply = BigDecimal.ZERO;
        for (TicketFeeConfigEntity feeConfigEntity : percentFeeSet) {
            feePercentToApply = feePercentToApply.add(feeConfigEntity.getAmount());
        }
        ticketFeeAmount = ticketSubtotal.multiply(feePercentToApply);

        //Summate flat fees and apply.
        BigDecimal feeFlatToApply = BigDecimal.ZERO;
        for (TicketFeeConfigEntity feeConfigEntity : flatFeeSet) {
            feeFlatToApply = feeFlatToApply.add(feeConfigEntity.getAmount());
        }
        ticketFeeAmount = ticketFeeAmount.add(feeFlatToApply).setScale(2, BigDecimal.ROUND_HALF_UP);

        //Apply payment vendor fee.
        //charge_amount = (subtotal + 0.30) / (1 - 2.90 / 100)
        BigDecimal subtotalWithFees = ticketSubtotal.add(ticketFeeAmount);
        grandTotal = ( subtotalWithFees.add(STRIPE_FLAT_FEE) )
                .divide(
                        (BigDecimal.ONE.subtract(STRIPE_PERCENT_FEE) ), BigDecimal.ROUND_HALF_UP);
        paymentFeeAmount = grandTotal.subtract(subtotalWithFees);

        PriceCalculationInfo result = new PriceCalculationInfo();
        result.ticketSubtotal = ticketSubtotal;
        result.feeSubtotal = ticketFeeAmount;
        result.grandTotal = grandTotal.setScale(2, RoundingMode.FLOOR);
        result.paymentFeeSubtotal = paymentFeeAmount;
        return result;
    }
}
