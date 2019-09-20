package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.gateway.FCMGateway;
import com.foriatickets.foriabackend.gateway.StripeGateway;
import com.foriatickets.foriabackend.repositories.*;
import com.google.firebase.messaging.Notification;
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
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.util.Arrays.asList;
import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

@Scope(scopeName = SCOPE_REQUEST)
@Service
@Transactional
public class TicketServiceImpl implements TicketService {

    private static final BigDecimal STRIPE_PERCENT_FEE = BigDecimal.valueOf(0.029);
    private static final BigDecimal STRIPE_FLAT_FEE = BigDecimal.valueOf(0.30);
    private static final int MAX_TICKETS_PER_ORDER = 10;

    private static final String RECEIVED_TICKET_TITLE = "Foria Pass Received";
    private static final String RECEIVED_TICKET_BODY = "You received a pass for {{eventName}} from {{previousName}}.";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm a");

    static class PriceCalculationInfo {

        BigDecimal ticketSubtotal;
        BigDecimal feeSubtotal;
        BigDecimal paymentFeeSubtotal;
        BigDecimal grandTotal;
        String currencyCode;
    }

    private final ModelMapper modelMapper;

    private final EventRepository eventRepository;

    private final FCMGateway fcmGateway;

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    private final OrderFeeEntryRepository orderFeeEntryRepository;

    private final OrderTicketEntryRepository orderTicketEntryRepository;

    private final OrderRepository orderRepository;

    private final UserRepository userRepository;

    private final TicketTypeConfigRepository ticketTypeConfigRepository;

    private final TicketRepository ticketRepository;

    private final TransferRequestRepository transferRequestRepository;

    private final AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway;

    private static final Logger LOG = LogManager.getLogger();

    private final StripeGateway stripeGateway;

    private UserEntity authenticatedUser;

    @Autowired
    public TicketServiceImpl(ModelMapper modelMapper, EventRepository eventRepository, OrderRepository orderRepository, UserRepository userRepository, TicketTypeConfigRepository ticketTypeConfigRepository, TicketRepository ticketRepository, StripeGateway stripeGateway, OrderFeeEntryRepository orderFeeEntryRepository, OrderTicketEntryRepository orderTicketEntryRepository, TransferRequestRepository transferRequestRepository, FCMGateway fcmGateway, AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway) {
        this.modelMapper = modelMapper;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.ticketTypeConfigRepository = ticketTypeConfigRepository;
        this.ticketRepository = ticketRepository;
        this.stripeGateway = stripeGateway;
        this.orderFeeEntryRepository = orderFeeEntryRepository;
        this.orderTicketEntryRepository = orderTicketEntryRepository;
        this.transferRequestRepository = transferRequestRepository;
        this.fcmGateway = fcmGateway;
        this.awsSimpleEmailServiceGateway = awsSimpleEmailServiceGateway;

        //Load user from Auth0 token.
        String auth0Id = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        authenticatedUser = userRepository.findByAuth0Id(auth0Id);
        if (authenticatedUser == null && !auth0Id.equalsIgnoreCase("anonymousUser") && !auth0Id.equalsIgnoreCase("auth0")) {
            LOG.warn("Attempted to create ticket service with non-mapped auth0Id: {}", auth0Id);
        }
    }

    @Override
    public ActivationResult activateTicket(UUID ticketId) {

        TicketEntity ticketEntity = verifyTicketValidity(ticketId, TicketEntity.Status.ISSUED);

        if (!ticketEntity.getOwnerEntity().getId().equals(authenticatedUser.getId())) {
            LOG.warn("User ID: {} attempted to activate not owned ticket ID: {}", authenticatedUser.getId(), ticketEntity.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket owned by another user.");
        }

        ticketEntity.setStatus(TicketEntity.Status.ACTIVE);
        ticketEntity = ticketRepository.save(ticketEntity);

        ActivationResult activationResult = new ActivationResult();
        activationResult.setTicketSecret(ticketEntity.getSecret());
        activationResult.setTicket(getTicket(ticketId, true));

        LOG.info("Ticket ID: {} activated by user ID: {}", ticketEntity.getId(), authenticatedUser.getId());
        return activationResult;
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

            //Replace Stripe customer default payment method with new one.
            stripeCustomerId = authenticatedUser.getStripeId();
            stripeGateway.updateCustomerPaymentMethod(stripeCustomerId, paymentToken);
        }

        Charge chargeResult = stripeGateway.chargeCustomer(stripeCustomerId, paymentToken, orderEntity.getId(), priceCalculationInfo.grandTotal, priceCalculationInfo.currencyCode);
        orderEntity.setChargeReferenceId(chargeResult.getId());
        orderRepository.save(orderEntity);

        //Send order confirmation email.
        final VenueEntity venueEntity = eventEntity.getVenueEntity();
        Map<String, String> map = new HashMap<>();
        map.put("eventDate", eventEntity.getEventStartTime().format(DATE_FORMATTER));
        map.put("eventTime", eventEntity.getEventStartTime().format(TIME_FORMATTER));
        map.put("eventName", eventEntity.getName());
        map.put("eventId", eventEntity.getId().toString());
        map.put("accountFirstName", authenticatedUser.getFirstName());
        map.put("orderId", orderId.toString());
        map.put("eventLocation", venueEntity.getContactStreetAddress() + ", " + venueEntity.getContactCity() + ", " + venueEntity.getContactState());

        awsSimpleEmailServiceGateway.sendEmailFromTemplate(authenticatedUser.getEmail(), AWSSimpleEmailServiceGateway.TICKET_PURCHASE_EMAIL, map);

        LOG.info("Stripe customer (ID: {}) charged: {}{} with chargeID: {}",
                stripeCustomerId, priceCalculationInfo.grandTotal, priceCalculationInfo.currencyCode, chargeResult.getId());
        return orderId;
    }

    @Override
    public Ticket getTicket(UUID ticketId, boolean doOwnerCheck) {

        Optional<TicketEntity> ticketEntityOptional = ticketRepository.findById(ticketId);
        if (!ticketEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid event ID");
        }
        TicketEntity ticketEntity = ticketEntityOptional.get();
        boolean doesUserOwn = ticketEntity.getOwnerEntity().getId().equals(authenticatedUser.getId());
        if (doOwnerCheck && !doesUserOwn) {
            LOG.warn("User Id: {} attempted to access non-owned ticket Id: {}", authenticatedUser.getId(), ticketEntity.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket not owned by user.");
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

        final Set<TicketEntity.Status> statusSet = new HashSet<>(asList(
                        TicketEntity.Status.REDEEMED,
                        TicketEntity.Status.CANCELED,
                        TicketEntity.Status.CANCELED_FRAUD)
        );

        Set<TicketEntity> userTickets = authenticatedUser.getTickets();
        List<Ticket> ticketList = new ArrayList<>();
        for (TicketEntity ticketEntity : userTickets) {

            if (statusSet.contains(ticketEntity.getStatus())) {
                continue;
            }

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
    public ActivationResult reactivateTicket(UUID ticketId) {

        TicketEntity ticketEntity = verifyTicketValidity(ticketId, TicketEntity.Status.ACTIVE, TicketEntity.Status.TRANSFER_PENDING);

        if (!ticketEntity.getOwnerEntity().getId().equals(authenticatedUser.getId())) {
            LOG.warn("User ID: {} attempted to activate not owned ticket ID: {}", authenticatedUser.getId(), ticketEntity.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket owned by another user.");
        }

        ticketEntity.setSecret(gAuth.createCredentials().getKey());
        ticketEntity = ticketRepository.save(ticketEntity);

        ActivationResult activationResult = new ActivationResult();
        activationResult.setTicketSecret(ticketEntity.getSecret());
        activationResult.setTicket(getTicket(ticketId, true));

        LOG.info("Ticket ID: {} reactivated by user ID: {}", ticketEntity.getId(), authenticatedUser.getId());
        return activationResult;
    }

    @Override
    public RedemptionResult redeemTicket(UUID ticketId, String otpCode) {

        int otpCodeInteger;
        try {
            otpCodeInteger = Integer.parseInt(otpCode);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP must be a valid integer.");
        }

        RedemptionResult redemptionResult = new RedemptionResult();
        redemptionResult.setTicket(getTicket(ticketId, false));

        TicketEntity ticketEntity;
        try {
            ticketEntity = verifyTicketValidity(ticketId, TicketEntity.Status.ACTIVE);
        } catch (Exception ex) {
            redemptionResult.setStatus(RedemptionResult.StatusEnum.DENY);
            LOG.warn("Failed to redeem ticket ID: {} for userID: {}", ticketId, authenticatedUser.getId());
            return redemptionResult;
        }

        final String ticketSecret = ticketEntity.getSecret();
        boolean isValid = gAuth.authorize(ticketSecret, otpCodeInteger);
        redemptionResult.setStatus(isValid ? RedemptionResult.StatusEnum.ALLOW : RedemptionResult.StatusEnum.DENY);

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
        int numTickets = 0;
        for (TicketLineItem ticketLineItem : orderConfig) {
            UUID ticketTypeConfigId = ticketLineItem.getTicketTypeId();
            int orderAmount = ticketLineItem.getAmount();
            numTickets += orderAmount;
            Optional<TicketTypeConfigEntity> ticketTypeConfigEntityOptional = ticketTypeConfigRepository.findById(ticketTypeConfigId);

            if (ticketTypeConfigEntityOptional.isPresent()) {
                TicketTypeConfigEntity ticketTypeConfigEntity = ticketTypeConfigEntityOptional.get();

                BigDecimal amountForType = new BigDecimal(orderAmount);
                BigDecimal ticketPriceForType = ticketTypeConfigEntity.getPrice().multiply(amountForType);
                ticketSubtotal = ticketSubtotal.add(ticketPriceForType);
                currencyCode = ticketTypeConfigEntity.getCurrency();
            }
        }

        PriceCalculationInfo priceCalculationInfo = calculateFees(numTickets, ticketSubtotal, feeSet);
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

    public PriceCalculationInfo calculateFees(final int numTickets, final BigDecimal ticketSubtotal, final Set<TicketFeeConfigEntity> feeSet) {

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
        BigDecimal paymentFeeAmount;
        BigDecimal grandTotal;

        //Summate percent fees and apply.
        BigDecimal feePercentToApply = BigDecimal.ZERO;
        for (TicketFeeConfigEntity feeConfigEntity : percentFeeSet) {
            feePercentToApply = feePercentToApply.add(feeConfigEntity.getAmount());
        }
        ticketFeeAmount = ticketSubtotal.multiply(feePercentToApply);

        //Summate flat fees and apply.
        BigDecimal feeFlatToApplyPerTicket = BigDecimal.ZERO;
        for (TicketFeeConfigEntity feeConfigEntity : flatFeeSet) {
            feeFlatToApplyPerTicket = feeFlatToApplyPerTicket.add(feeConfigEntity.getAmount());
        }
        BigDecimal flatFeeAmount = feeFlatToApplyPerTicket.multiply(BigDecimal.valueOf(numTickets));
        ticketFeeAmount = ticketFeeAmount.add(flatFeeAmount).setScale(2, BigDecimal.ROUND_HALF_UP);

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
     * @param expectedStatuses Status to check.
     */
    private TicketEntity verifyTicketValidity(UUID ticketId, TicketEntity.Status... expectedStatuses) {

        if (ticketId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket ID must not be null.");
        }

        Optional<TicketEntity> ticketEntityOptional = ticketRepository.findById(ticketId);
        if (!ticketEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket ID is invalid.");
        }

        List<TicketEntity.Status> expectedStatusList = null;
        if (expectedStatuses != null) {
            expectedStatusList = Arrays.asList(expectedStatuses);
        }
        TicketEntity ticketEntity = ticketEntityOptional.get();

        if (expectedStatusList != null && !expectedStatusList.contains(ticketEntity.getStatus())) {
            LOG.warn("User ID: {} attempted to activate/reactivate ticket not having {} status. Ticket ID: {}", authenticatedUser.getId(), expectedStatusList, ticketEntity.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket is not in " + expectedStatusList + " status.");
        }

        return ticketEntity;
    }

    @Override
    public void cancelTransferTicket(UUID ticketId) {

        TicketEntity ticketEntity = verifyTicketValidity(ticketId, TicketEntity.Status.TRANSFER_PENDING);
        if (!ticketEntity.getOwnerEntity().equals(authenticatedUser)) {
            LOG.warn("User ID: {} attempted to transfer ticket ID: {} they dont own.", authenticatedUser.getId(), ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unauthorized");
        }

        TransferRequestEntity transferRequestEntity = transferRequestRepository.findFirstByTicketAndStatus(ticketEntity, TransferRequestEntity.Status.PENDING);
        if (transferRequestEntity == null) {
            LOG.warn("User ID: {} attempted to cancel non-pending transfer. Ticket ID: {}", authenticatedUser.getId(), ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attempted to cancel non-pending transfer");
        }

        ticketEntity.setStatus(TicketEntity.Status.ACTIVE);
        ticketRepository.save(ticketEntity);

        transferRequestEntity.setCompletedDate(OffsetDateTime.now());
        transferRequestEntity.setStatus(TransferRequestEntity.Status.CANCELED);
        transferRequestEntity = transferRequestRepository.save(transferRequestEntity);

        LOG.info("User ID: {} canceled transfer request ID: {}", authenticatedUser.getId(), transferRequestEntity.getId());
    }

    @Override
    public Ticket transferTicket(UUID ticketId, TransferRequest transferRequest) {

        TicketEntity ticketEntity = verifyTicketValidity(ticketId, TicketEntity.Status.ACTIVE);
        if (!ticketEntity.getOwnerEntity().equals(authenticatedUser)) {
            LOG.warn("User ID: {} attempted to transfer ticket ID: {} they dont own.", authenticatedUser.getId(), ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unauthorized");
        }

        final String receiverEmail = transferRequest.getReceiverEmail();

        //Attempt to locate user with requested email in transfer request.
        TransferRequestEntity transferRequestEntity = new TransferRequestEntity();
        transferRequestEntity.setTicket(ticketEntity);
        transferRequestEntity.setCreatedDate(OffsetDateTime.now());
        transferRequestEntity.setReceiverEmail(receiverEmail);
        transferRequestEntity.setTransferor(authenticatedUser);

        UserEntity receiver = userRepository.findFirstByEmail(receiverEmail);
        if (receiver != null) { //Complete transfer ASAP.

            transferRequestEntity.setReceiver(receiver);
            transferRequestEntity.setCompletedDate(OffsetDateTime.now());
            transferRequestEntity.setStatus(TransferRequestEntity.Status.COMPLETED);
            transferRequestRepository.save(transferRequestEntity);

            changeTicketOwner(ticketEntity, receiver);
            return null;

        } else {

            transferRequestEntity.setStatus(TransferRequestEntity.Status.PENDING);
            ticketEntity.setStatus(TicketEntity.Status.TRANSFER_PENDING);
            ticketRepository.save(ticketEntity);

            transferRequestRepository.save(transferRequestEntity);

            final Map<String, String> templateData = buildTransferTemplateDataPayload(ticketEntity, null, ticketEntity.getOwnerEntity());
            templateData.put("emailTransferee", receiverEmail);
            awsSimpleEmailServiceGateway.sendEmailFromTemplate(ticketEntity.getOwnerEntity().getEmail(),
                    AWSSimpleEmailServiceGateway.TRANSFEROR_PENDING_EMAIL, templateData);
            awsSimpleEmailServiceGateway.sendEmailFromTemplate(receiverEmail,
                    AWSSimpleEmailServiceGateway.TRANSFEREE_PENDING_EMAIL, templateData);

            return getTicket(ticketId, false);
        }
    }

    /**
     * Changes the owner of the ticket Id to a new user.
     * This method ensures that the ticket secret is rotated and that the status is set back to ISSUED.
     *
     * @param ticketEntity Ticket to transfer.
     * @param newOwner New owner.
     */
    private void changeTicketOwner(TicketEntity ticketEntity, UserEntity newOwner) {

        //Send push to all of users logged in devices.
        for (DeviceTokenEntity token : newOwner.getDeviceTokens()) {

            final String eventName = ticketEntity.getEventEntity().getName();
            final String message = RECEIVED_TICKET_BODY
                    .replace("{{eventName}}", eventName)
                    .replace("{{previousName}}", ticketEntity.getOwnerEntity().getFirstName());
            final Notification notification = new Notification(RECEIVED_TICKET_TITLE, message);

            fcmGateway.sendPushNotification(token.getDeviceToken(), notification);
        }

        //Send email to both new and old owner.
        final Map<String, String> templateData = buildTransferTemplateDataPayload(ticketEntity, newOwner, ticketEntity.getOwnerEntity());
        templateData.put("emailTransferee", newOwner.getEmail());

        awsSimpleEmailServiceGateway.sendEmailFromTemplate(newOwner.getEmail(),
                AWSSimpleEmailServiceGateway.TRANSFEREE_COMPLETE_EMAIL, templateData);
        awsSimpleEmailServiceGateway.sendEmailFromTemplate(ticketEntity.getOwnerEntity().getEmail(),
                AWSSimpleEmailServiceGateway.TRANSFEROR_COMPLETE_EMAIL, templateData);

        ticketEntity.setOwnerEntity(newOwner);
        ticketEntity.setStatus(TicketEntity.Status.ISSUED);
        ticketEntity.setSecret(gAuth.createCredentials().getKey());
        ticketRepository.save(ticketEntity);

        LOG.info("Ticket ID: {} transferred to new owner Id: {}", ticketEntity.getId(), newOwner.getId());
    }

    /**
     * Builds payload map used for template data.
     *
     * @param ticketEntity Event to load info for.
     * @param newUser New owner.
     * @param oldUser Previous owner.
     * @return A map to send to SES.
     */
    private Map<String, String> buildTransferTemplateDataPayload(TicketEntity ticketEntity, UserEntity newUser, UserEntity oldUser) {

        Map<String, String> map = new HashMap<>();
        map.put("eventDate", ticketEntity.getEventEntity().getEventStartTime().format(DATE_FORMATTER));
        map.put("eventTime", ticketEntity.getEventEntity().getEventStartTime().format(TIME_FORMATTER));
        map.put("eventName", ticketEntity.getEventEntity().getName());
        map.put("eventId", ticketEntity.getEventEntity().getId().toString());
        map.put("nameTransferor", oldUser.getFirstName());

        if (newUser != null) {
            map.put("nameTransferee", newUser.getFirstName());
        }

        return map;
    }

    @Override
    public void checkAndConfirmPendingTicketTransfers(UserEntity newUser) {

        if (newUser == null || newUser.getEmail() == null) {
            return;
        }

        final String receiverEmail = newUser.getEmail();
        List<TransferRequestEntity> pendingTickets = transferRequestRepository.findAllByReceiverEmail(receiverEmail);

        if (pendingTickets == null || pendingTickets.isEmpty()) {
            LOG.info("No pending tickets to confirm for userId: {}", newUser.getId());
            return;
        }

        for (TransferRequestEntity ticketRequest : pendingTickets) {

            ticketRequest.setReceiver(newUser);
            ticketRequest.setCompletedDate(OffsetDateTime.now());
            ticketRequest.setStatus(TransferRequestEntity.Status.COMPLETED);

            changeTicketOwner(ticketRequest.getTicket(), newUser);
        }

        transferRequestRepository.saveAll(pendingTickets);
        LOG.info("{} tickets confirmed and transferred for userId: {}", pendingTickets.size(), newUser.getId());
    }
}
