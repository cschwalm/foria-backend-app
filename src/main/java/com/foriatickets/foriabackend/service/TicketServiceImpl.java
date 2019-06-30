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
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class TicketServiceImpl implements TicketService {

    private static final BigDecimal STRIPE_PERCENT_FEE = BigDecimal.valueOf(0.029);
    private static final BigDecimal STRIPE_FLAT_FEE = BigDecimal.valueOf(0.30);

    private static class PriceCalculationInfo {

        Set<TicketFeeConfigEntity> feeConfigEntitySet;
        BigDecimal ticketSubtotal;
        BigDecimal feeSubtotal;
        BigDecimal paymentFeeSubtotal;
        BigDecimal grandTotal;
        String currencyCode;
    }

    private final ModelMapper modelMapper;

    private final EventRepository eventRepository;

    private final OrderRepository orderRepository;

    private final UserRepository userRepository;

    private final TicketTypeConfigRepository ticketTypeConfigRepository;

    private final TicketRepository ticketRepository;

    private static final Logger LOG = LogManager.getLogger();

    private final StripeGateway stripeGateway;

    public TicketServiceImpl(ModelMapper modelMapper, EventRepository eventRepository, OrderRepository orderRepository, UserRepository userRepository, TicketTypeConfigRepository ticketTypeConfigRepository, TicketRepository ticketRepository, StripeGateway stripeGateway) {
        this.modelMapper = modelMapper;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.ticketTypeConfigRepository = ticketTypeConfigRepository;
        this.ticketRepository = ticketRepository;
        this.stripeGateway = stripeGateway;
    }

    @Override
    public void checkoutOrder(String auth0Id, String paymentToken, UUID eventId, List<TicketLineItem> orderConfig) {

        if (StringUtils.isEmpty(auth0Id) || StringUtils.isEmpty(paymentToken) || orderConfig == null || eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout request is missing required data.");
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

        //Create easy to access map of ticket config to ticket amounts.
        HashMap<UUID, Integer> ticketAmounts = new HashMap<>();
        for (TicketLineItem ticketLineItem : orderConfig) {
            ticketAmounts.put(ticketLineItem.getTicketTypeId(), ticketLineItem.getAmount());
        }

        //Generate unique order ID.
        final UUID orderId = UUID.randomUUID();

        //Create order entry.
        OrderEntity orderEntity = new OrderEntity();

        //Validate ticket config IDs are valid and issue tickets.
        Set<OrderTicketEntryEntity> orderTicketEntryEntities = new HashSet<>();
        for (UUID ticketTypeConfigId : ticketAmounts.keySet()) {
            boolean doesExist = ticketTypeConfigRepository.existsById(ticketTypeConfigId);
            if (!doesExist) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket type config is invalid.");
            }

            TicketEntity issuedTicket = issueTicket(userEntity.getId(), eventId, ticketTypeConfigId);
            OrderTicketEntryEntity orderTicketEntryEntity = new OrderTicketEntryEntity();
            orderTicketEntryEntity.setOrderEntity(orderEntity);
            orderTicketEntryEntity.setTicketEntity(issuedTicket);
            orderTicketEntryEntities.add(orderTicketEntryEntity);
        }

        Set<OrderFeeEntryEntity> orderFeeEntryEntities = new HashSet<>();
        for (TicketFeeConfigEntity ticketFeeConfigEntity : ticketFeeConfigEntitySet) {
            OrderFeeEntryEntity orderFeeEntryEntity = new OrderFeeEntryEntity();
            orderFeeEntryEntity.setOrderEntity(orderEntity);
            orderFeeEntryEntity.setTicketFeeConfigEntity(ticketFeeConfigEntity);
            orderFeeEntryEntities.add(orderFeeEntryEntity);
        }

        //Calculate order total.
        PriceCalculationInfo priceCalculationInfo = calculateTotalPrice(eventId, orderConfig);
        orderEntity.setId(orderId);
        orderEntity.setPurchaser(userEntity);
        orderEntity.setOrderTimestamp(OffsetDateTime.now());
        orderEntity.setTotal(priceCalculationInfo.grandTotal);
        orderEntity.setCurrency(priceCalculationInfo.currencyCode);
        orderEntity.setTickets(orderTicketEntryEntities);
        orderEntity.setFees(orderFeeEntryEntities);
        orderEntity = orderRepository.save(orderEntity);

        //Charge payment method - tickets have been issued. Create Stripe user if it doesn't exist.
        String stripeCustomerId;
        if (StringUtils.isEmpty(userEntity.getStripeId())) {
            stripeCustomerId = stripeGateway.createStripeCustomer(user, paymentToken).getId();
            userEntity.setStripeId(stripeCustomerId);
            userRepository.save(userEntity);
        } else {
            stripeCustomerId = userEntity.getStripeId();
        }

        Charge chargeResult = stripeGateway.chargeCustomer(stripeCustomerId, paymentToken, orderEntity.getId(), priceCalculationInfo.grandTotal, priceCalculationInfo.currencyCode);
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

        //Create easy to access map of ticket config to ticket amounts.
        HashMap<UUID, Integer> ticketAmounts = new HashMap<>();
        for (TicketLineItem ticketLineItem : orderConfig) {

            ticketAmounts.put(ticketLineItem.getTicketTypeId(), ticketLineItem.getAmount());
        }

        //Load ticket price configs.
        //Summate over tickets to calculate sub-total.
        for (UUID ticketTypeConfigId : ticketAmounts.keySet()) {
            Optional<TicketTypeConfigEntity> ticketTypeConfigEntityOptional = ticketTypeConfigRepository.findById(ticketTypeConfigId);

            if (ticketTypeConfigEntityOptional.isPresent()) {
                TicketTypeConfigEntity ticketTypeConfigEntity = ticketTypeConfigEntityOptional.get();

                BigDecimal amountForType = new BigDecimal(ticketAmounts.get(ticketTypeConfigId));
                BigDecimal ticketPriceForType = ticketTypeConfigEntity.getPrice().multiply(amountForType);
                ticketSubtotal = ticketSubtotal.add(ticketPriceForType);
                currencyCode = ticketTypeConfigEntity.getCurrency();
            }
        }

        PriceCalculationInfo priceCalculationInfo = calculateFees(ticketSubtotal, percentFeeSet, flatFeeSet);
        priceCalculationInfo.currencyCode = currencyCode;
        priceCalculationInfo.feeConfigEntitySet = eventEntity.getTicketFeeConfig();
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
    PriceCalculationInfo calculateFees(BigDecimal ticketSubtotal, Set<TicketFeeConfigEntity> percentFeeSet, Set<TicketFeeConfigEntity> flatFeeSet) {

        BigDecimal ticketFeeAmount;
        BigDecimal paymentFeeAmount;
        BigDecimal grandTotal;

        //Summate percent fees and apply.
        BigDecimal feePercentToApply = BigDecimal.ONE;
        for (TicketFeeConfigEntity feeConfigEntity : percentFeeSet) {
            feePercentToApply = feePercentToApply.add(feeConfigEntity.getAmount());
        }
        ticketFeeAmount = ticketSubtotal.multiply(feePercentToApply);

        //Summate flat fees and apply.
        BigDecimal feeFlatToApply = BigDecimal.ZERO;
        for (TicketFeeConfigEntity feeConfigEntity : flatFeeSet) {
            feeFlatToApply = feeFlatToApply.add(feeConfigEntity.getAmount());
        }
        ticketFeeAmount = ticketFeeAmount.add(feeFlatToApply);

        //Apply payment vendor fee.
        //charge_amount = (subtotal + 0.30) / (1 - 2.90 / 100)
        BigDecimal subtotalWithFees = ticketSubtotal.add(ticketFeeAmount);
        grandTotal = ( subtotalWithFees.add(STRIPE_FLAT_FEE) )
                .divide(
                        (BigDecimal.ONE.subtract(STRIPE_PERCENT_FEE) ), BigDecimal.ROUND_FLOOR);
        paymentFeeAmount = grandTotal.subtract(subtotalWithFees);

        PriceCalculationInfo result = new PriceCalculationInfo();
        result.ticketSubtotal = ticketSubtotal;
        result.feeSubtotal = ticketFeeAmount;
        result.grandTotal = grandTotal;
        result.paymentFeeSubtotal = paymentFeeAmount;
        return result;
    }
}
