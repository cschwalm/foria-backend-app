package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.StripeGateway;
import com.foriatickets.foriabackend.repositories.*;
import com.stripe.model.Charge;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.openapitools.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.token.Sha512DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

@Scope(scopeName = SCOPE_REQUEST)
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

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    private final OrderFeeEntryRepository orderFeeEntryRepository;

    private final OrderTicketEntryRepository orderTicketEntryRepository;

    private final OrderRepository orderRepository;

    private final UserRepository userRepository;

    private final TicketTypeConfigRepository ticketTypeConfigRepository;

    private final TicketRepository ticketRepository;

    private static final Logger LOG = LogManager.getLogger();

    private final StripeGateway stripeGateway;

    private UserEntity authenticatedUser;

    @Autowired
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

        //Load user from Auth0 token.
        String auth0Id = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        authenticatedUser = userRepository.findByAuth0Id(auth0Id);
        if (authenticatedUser == null && !auth0Id.equalsIgnoreCase("anonymousUser")) {

            LOG.error("Attempted to create ticket service with non-mapped auth0Id: {}", auth0Id);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User must be created in Foria system.");
        }
    }

    @Override
    public ActivationResult activateTicket(UUID ticketId) {

        TicketEntity ticketEntity = verifyTicketValidity(ticketId, TicketEntity.Status.ISSUED);

        if (!ticketEntity.getOwnerEntity().getId().equals(authenticatedUser.getId())) {
            LOG.warn("User ID: {} attempted to activate not owned ticket ID: {}", authenticatedUser.getId(), ticketEntity.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ticket owned by another user.");
        }

        ticketEntity.setStatus(TicketEntity.Status.ACTIVE);
        ticketEntity = ticketRepository.save(ticketEntity);

        ActivationResult activationResult = new ActivationResult();
        activationResult.setTicketSecret(ticketEntity.getSecret());
        activationResult.setTicket(getTicket(ticketId));

        LOG.info("Ticket ID: {} activated by user ID: {}", ticketEntity.getId(), authenticatedUser.getId());
        return activationResult;
    }

    @Override
    public UUID checkoutOrder(String paymentToken, UUID eventId, List<TicketLineItem> orderConfig) {

        if (StringUtils.isEmpty(paymentToken) || orderConfig == null || eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout request is missing required data.");
        }

        int totalTicketCount = 0;
        for (TicketLineItem ticketLineItem : orderConfig) {
            totalTicketCount += ticketLineItem.getAmount();
        }
        if (totalTicketCount > MAX_TICKETS_PER_ORDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Max of " + MAX_TICKETS_PER_ORDER + " tickets per order allowed.");
        }

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
        orderEntity.setPurchaser(authenticatedUser);
        orderEntity.setOrderTimestamp(OffsetDateTime.now());
        orderEntity.setTotal(priceCalculationInfo.grandTotal);
        orderEntity.setCurrency(priceCalculationInfo.currencyCode);
        orderEntity = orderRepository.save(orderEntity);

        //Validate ticket config IDs are valid and issue tickets.
        for (TicketLineItem ticketLineItem : orderConfig) {
            UUID ticketTypeConfigId = ticketLineItem.getTicketTypeId();
            Optional<TicketTypeConfigEntity> ticketTypeConfigEntityOptional = ticketTypeConfigRepository.findById(ticketTypeConfigId);
            if (!ticketTypeConfigEntityOptional.isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket type config is invalid.");
            }

            int ticketsRemaining = obtainTicketsRemainingByType(ticketTypeConfigEntityOptional.get());
            if (ticketLineItem.getAmount() > ticketsRemaining) {
                LOG.warn("Not enough tickets to complete the order. - eventId: {} - ticketConfigId: {}", eventId, ticketTypeConfigId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough tickets to complete the order.");
            }

            TicketEntity issuedTicket;
            for (int i = 0; i < ticketLineItem.getAmount(); i++) {

                issuedTicket = issueTicket(authenticatedUser.getId(), eventId, ticketTypeConfigId);

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
        if (StringUtils.isEmpty(authenticatedUser.getStripeId())) {
            User user = modelMapper.map(authenticatedUser, User.class);
            stripeCustomerId = stripeGateway.createStripeCustomer(user, paymentToken).getId();
            authenticatedUser.setStripeId(stripeCustomerId);
            authenticatedUser = userRepository.save(authenticatedUser);
        } else {
            stripeCustomerId = authenticatedUser.getStripeId();
        }

        Charge chargeResult = stripeGateway.chargeCustomer(stripeCustomerId, orderEntity.getId(), priceCalculationInfo.grandTotal, priceCalculationInfo.currencyCode);
        orderEntity.setChargeReferenceId(chargeResult.getId());
        orderRepository.save(orderEntity);
        LOG.info("Stripe customer (ID: {}) charged: {}{} with chargeID: {}",
                stripeCustomerId, priceCalculationInfo.grandTotal, priceCalculationInfo.currencyCode, chargeResult.getId());
        return orderId;
    }

    @Override
    public Ticket getTicket(UUID ticketId) {

        Optional<TicketEntity> ticketEntityOptional = ticketRepository.findById(ticketId);
        if (!ticketEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid event ID");
        }
        TicketEntity ticketEntity = ticketEntityOptional.get();
        boolean doesUserOwn = ticketEntity.getOwnerEntity().getId().equals(authenticatedUser.getId());
        if (!doesUserOwn) {
            LOG.warn("User Id: {} attempted to access non-owned ticket Id: {}", authenticatedUser.getId(), ticketEntity.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ticket not owned by user.");
        }

        Ticket ticket = modelMapper.map(ticketEntity, Ticket.class);
        ticket.setOwnerId(ticketEntity.getOwnerEntity().getId());
        ticket.setPurchaserId(ticketEntity.getPurchaserEntity().getId());
        ticket.setSecretHash(Sha512DigestUtils.shaHex(ticketEntity.getSecret()));
        ticket.setStatus(Ticket.StatusEnum.fromValue(ticketEntity.getStatus().name()));

        LOG.debug("Ticket ID: {} obtained.", ticketEntity.getId());
        return ticket;
    }

    @Override
    public List<Ticket> getUsersTickets() {

        Set<TicketEntity> userTickets = authenticatedUser.getTickets();
        List<Ticket> ticketList = new ArrayList<>();
        for (TicketEntity ticketEntity : userTickets) {
            Ticket ticket = modelMapper.map(ticketEntity, Ticket.class);
            ticket.setStatus(Ticket.StatusEnum.fromValue(ticketEntity.getStatus().name()));
            ticket.setOwnerId(ticketEntity.getOwnerEntity().getId());
            ticket.setPurchaserId(ticketEntity.getPurchaserEntity().getId());
            ticket.setSecretHash(Sha512DigestUtils.shaHex(ticketEntity.getSecret()));
            ticketList.add(ticket);
        }
        LOG.debug("Tickets returned for user ID: {}", authenticatedUser.getId());
        return ticketList;
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
        ticketEntity.setSecret(gAuth.createCredentials().getKey());
        ticketEntity.setTicketTypeConfigEntity(ticketTypeConfigEntity);
        ticketEntity.setStatus(TicketEntity.Status.ISSUED);
        ticketEntity.setIssuedDate(OffsetDateTime.now());

        ticketEntity = ticketRepository.save(ticketEntity);

        LOG.info("Issued ticket: {} for userID: {}", ticketEntity.getId(), userEntity.getId());
        return ticketEntity;
    }

    @Override
    public RedemptionResult redeemTicket(UUID ticketId, String otpCode) {

        int otpCodeInteger;
        try {
            otpCodeInteger = Integer.parseInt(otpCode);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP must be a valid integer.");
        }

        TicketEntity ticketEntity = verifyTicketValidity(ticketId, TicketEntity.Status.ACTIVE);
        final String ticketSecret = ticketEntity.getSecret();

        boolean isValid = gAuth.authorize(ticketSecret, otpCodeInteger);
        RedemptionResult redemptionResult = new RedemptionResult();
        redemptionResult.setStatus(isValid ? RedemptionResult.StatusEnum.ALLOW : RedemptionResult.StatusEnum.DENY);
        redemptionResult.setTicket(getTicket(ticketId));

        if (isValid) {

            ticketEntity.setStatus(TicketEntity.Status.REDEEMED);
            ticketRepository.save(ticketEntity);

            LOG.info("Redeemed ticket ID: {} for userID: {}", ticketId, authenticatedUser.getId());
        } else {
            LOG.warn("Failed to redeem ticket ID: {} for userID: {}", ticketId, authenticatedUser.getId());
        }

        return redemptionResult;
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

    @Override
    public int countTicketsRemaining(UUID ticketTypeConfigId) {

        Optional<TicketTypeConfigEntity> ticketTypeConfigEntityOptional = ticketTypeConfigRepository.findById(ticketTypeConfigId);

        if (!ticketTypeConfigEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket type config is invalid.");
        }

        int ticketsRemaining = obtainTicketsRemainingByType(ticketTypeConfigEntityOptional.get());
        return ticketsRemaining > MAX_TICKETS_PER_ORDER ? MAX_TICKETS_PER_ORDER : ticketsRemaining;
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

    /**
     * Returns the amount of tickets that are allowed to be issued.
     *
     * @param ticketTypeConfigEntity ticket type
     * @return A non-negative number.
     */
    int obtainTicketsRemainingByType(TicketTypeConfigEntity ticketTypeConfigEntity) {

        int ticketsIssued = ticketRepository.countActiveTicketsIssuedByType(ticketTypeConfigEntity.getId(), ticketTypeConfigEntity.getEventEntity().getId());
        return ticketTypeConfigEntity.getAuthorizedAmount() - ticketsIssued;
    }

    /**
     * Preforms the following validation checks on the specified ticket.
     *
     * Ensures that the ticket is:
     * 1) Ticket ID is not null and a ticket.
     * 2) The ticket is in the expected status.
     *
     * @param ticketId Ticket ID to verify.
     * @param expectedStatus Status to check.
     */
    private TicketEntity verifyTicketValidity(UUID ticketId, TicketEntity.Status expectedStatus) {

        if (ticketId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket ID must not be null.");
        }

        Optional<TicketEntity> ticketEntityOptional = ticketRepository.findById(ticketId);
        if (!ticketEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket ID is invalid.");
        }

        TicketEntity ticketEntity = ticketEntityOptional.get();

        if (ticketEntity.getStatus() != expectedStatus) {
            LOG.warn("User ID: {} attempted to activate ticket not having {} status. Ticket ID: {}", authenticatedUser.getId(), expectedStatus, ticketEntity.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ticket is not in " + expectedStatus + " status.");
        }

        return ticketEntity;
    }
}
