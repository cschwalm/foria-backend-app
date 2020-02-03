package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.gateway.FCMGateway;
import com.foriatickets.foriabackend.gateway.StripeGateway;
import com.foriatickets.foriabackend.repositories.*;
import com.google.firebase.messaging.Notification;
import com.stripe.model.Charge;
import com.stripe.model.Refund;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.util.Arrays.asList;
import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

@Scope(scopeName = SCOPE_REQUEST)
@Service
@Transactional
public class TicketServiceImpl implements TicketService {

    private static final int MAX_TICKETS_PER_ORDER = 10;

    private static final String RECEIVED_TICKET_TITLE = "Foria Pass Received";
    private static final String RECEIVED_TICKET_BODY = "You received a pass for {{eventName}} from {{previousName}}.";
    private static final String REFUND_TITLE = "Foria Order Refunded";
    private static final String REFUND_BODY = "Your {{eventName}} order has been canceled and refunded.";
    private static final String REFUND_EMAIL = "refund_order_email";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm a");

    private final CalculationService calculationService;

    private final ModelMapper modelMapper;

    private final EventRepository eventRepository;

    private final FCMGateway fcmGateway;

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    private final OrderFeeEntryRepository orderFeeEntryRepository;

    private final OrderTicketEntryRepository orderTicketEntryRepository;

    private final OrderRepository orderRepository;

    private final PromoCodeRepository promoCodeRepository;

    private final PromoCodeRedemptionRepository promoCodeRedemptionRepository;

    private final UserRepository userRepository;

    private final TicketTypeConfigRepository ticketTypeConfigRepository;

    private final TicketRepository ticketRepository;

    private final TransferRequestRepository transferRequestRepository;

    private final AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway;

    private static final Logger LOG = LogManager.getLogger();

    private final StripeGateway stripeGateway;

    private UserEntity authenticatedUser;

    @Autowired
    public TicketServiceImpl(CalculationService calculationService,
                             ModelMapper modelMapper,
                             EventRepository eventRepository,
                             OrderRepository orderRepository,
                             PromoCodeRepository promoCodeRepository,
                             PromoCodeRedemptionRepository promoCodeRedemptionRepository,
                             UserRepository userRepository,
                             TicketTypeConfigRepository ticketTypeConfigRepository,
                             TicketRepository ticketRepository,
                             StripeGateway stripeGateway,
                             OrderFeeEntryRepository orderFeeEntryRepository,
                             OrderTicketEntryRepository orderTicketEntryRepository,
                             TransferRequestRepository transferRequestRepository,
                             FCMGateway fcmGateway,
                             AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway) {

        this.calculationService = calculationService;
        this.modelMapper = modelMapper;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.promoCodeRedemptionRepository = promoCodeRedemptionRepository;
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
    public UUID checkoutOrder(String paymentToken, UUID eventId, List<TicketLineItem> orderConfig, String promoCode) {

        if (orderConfig == null || eventId == null) {
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

        //Calculate order total.
        CalculationServiceImpl.PriceCalculationInfo priceCalculationInfo = calculationService.calculateTotalPrice(eventId, orderConfig);

        //Create order entry.
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setPurchaser(authenticatedUser);
        orderEntity.setStatus(OrderEntity.Status.COMPLETED);
        orderEntity.setOrderTimestamp(OffsetDateTime.now());
        orderEntity.setTotal(priceCalculationInfo.grandTotal);
        orderEntity.setCurrency(priceCalculationInfo.currencyCode);
        orderEntity = orderRepository.save(orderEntity);

        //Generate unique order ID.
        final UUID orderId = orderEntity.getId();

        //Validate ticket config IDs are valid and issue tickets.
        for (TicketLineItem ticketLineItem : orderConfig) {
            UUID ticketTypeConfigId = ticketLineItem.getTicketTypeId();
            Optional<TicketTypeConfigEntity> ticketTypeConfigEntityOptional = ticketTypeConfigRepository.findById(ticketTypeConfigId);
            if (!ticketTypeConfigEntityOptional.isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket type config is invalid.");
            }
            final TicketTypeConfigEntity ticketTypeConfigEntity = ticketTypeConfigEntityOptional.get();
            PromoCodeEntity promoCodeEntity = null;

            //Validate that promo code is entered for promo tier.
            if (ticketTypeConfigEntity.getType() == TicketTypeConfigEntity.Type.PROMO) {

                if (StringUtils.isEmpty(promoCode)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo code not supplied for PROMO tier.");
                }

                promoCodeEntity = promoCodeRepository.findByTicketTypeConfigEntity_IdAndCode(ticketTypeConfigEntity.getId(), promoCode.toUpperCase());

                if (promoCodeEntity == null) {
                    LOG.warn("Promo code invalid for PROMO tier: {}.", ticketTypeConfigId);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo code invalid for PROMO tier.");
                }

                //Validate code and redemptions are remaining.
                final int codeUsed = promoCodeEntity.getRedemptions().size();
                if (codeUsed + ticketLineItem.getAmount() > promoCodeEntity.getQuantity()) {
                    LOG.warn("Attempted to checkout with promo code that has been used max amount. Code: {}", promoCode);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attempted to checkout with promo code that has been used max amount.");
                }
            }

            int ticketsRemaining = obtainTicketsRemainingByType(ticketTypeConfigEntity);
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

                //Add mapping if promo code was used.
                if (promoCodeEntity != null) {

                    PromoCodeRedemptionEntity promoCodeRedemptionEntity = new PromoCodeRedemptionEntity();
                    promoCodeRedemptionEntity.setPromoCodeEntity(promoCodeEntity);
                    promoCodeRedemptionEntity.setTicketEntity(issuedTicket);
                    promoCodeRedemptionEntity.setRedemptionDate(issuedTicket.getIssuedDate());
                    promoCodeRedemptionEntity.setTicketTypeConfigEntity(issuedTicket.getTicketTypeConfigEntity());
                    promoCodeRedemptionEntity.setUserEntity(issuedTicket.getPurchaserEntity());
                    promoCodeRedemptionEntity = promoCodeRedemptionRepository.save(promoCodeRedemptionEntity);
                    LOG.info("Added redemption entry for promo code: {} with ID: {}", promoCode, promoCodeRedemptionEntity.getId());
                }
            }
        }

        for (TicketFeeConfigEntity ticketFeeConfigEntity : ticketFeeConfigEntitySet) {

            if (ticketFeeConfigEntity.getStatus() != TicketFeeConfigEntity.Status.ACTIVE) {
                continue;
            }

            OrderFeeEntryEntity orderFeeEntryEntity = new OrderFeeEntryEntity();
            orderFeeEntryEntity.setOrderEntity(orderEntity);
            orderFeeEntryEntity.setTicketFeeConfigEntity(ticketFeeConfigEntity);
            orderFeeEntryRepository.save(orderFeeEntryEntity);
        }

        //If order is not free, charge payment method - tickets have been issued. Create Stripe user if it doesn't exist.
        if (priceCalculationInfo.grandTotal.compareTo(BigDecimal.ZERO) > 0) {

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
        }
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

        LOG.info("User: (ID: {}) charged: {}{}", authenticatedUser.getId(), priceCalculationInfo.grandTotal, priceCalculationInfo.currencyCode);
        return orderId;
    }

    @Override
    public void refundOrder(UUID orderId) {

        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order cancel request contains null orderId.");
        }

        final Optional<OrderEntity> orderEntityOptional = orderRepository.findById(orderId);
        if (!orderEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order Id not found. Failed to cancel order.");
        }

        final OrderEntity orderEntity = orderEntityOptional.get();
        if (orderEntity.getStatus() == OrderEntity.Status.CANCELED) {
            LOG.info("Order ID: {} is already canceled.", orderEntity.getId());
            return;
        }

        orderEntity.setStatus(OrderEntity.Status.CANCELED);

        //Cancel each issued ticket and collect owner list.
        final Set<UserEntity> usersImpacted = new HashSet<>();
        String eventName = "Unknown Event";
        for (OrderTicketEntryEntity orderTicketEntryEntity : orderEntity.getTickets()) {

            final TicketEntity ticketEntity = orderTicketEntryEntity.getTicketEntity();
            if (OffsetDateTime.now().isAfter(ticketEntity.getEventEntity().getEventEndTime().plusDays(7L))) {
                LOG.error("Attempted to refund order ID: {} after event has already ended.", orderEntity.getId());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attempted to refund order after event has ended.");
            }

            ticketEntity.setStatus(TicketEntity.Status.CANCELED);
            ticketRepository.save(ticketEntity);

            usersImpacted.add(ticketEntity.getPurchaserEntity());
            eventName = ticketEntity.getEventEntity().getName();

            //Add both owner and purchaser to notify.
            if (!ticketEntity.getPurchaserEntity().getId().equals(ticketEntity.getOwnerEntity().getId())) {
                usersImpacted.add(ticketEntity.getOwnerEntity());
            }
        }

        LOG.info("Number of tickets cancelled in order: {} - OrderId: {}", orderEntity.getTickets().size(), orderEntity.getId());
        LOG.info("Number of users impacted: {} - OrderId: {}", usersImpacted.size(), orderEntity.getId());

        Refund refund;
        //Refunds the entire order amount if not free.
        if (!StringUtils.isEmpty(orderEntity.getChargeReferenceId())) {
            refund = stripeGateway.refundStripeCharge(orderEntity.getChargeReferenceId(), orderEntity.getTotal());
            orderEntity.setRefundReferenceId(refund.getId());
        }

        //Send push notifications and emails to impacted customers.
        for (UserEntity userEntity : usersImpacted) {

            Map<String, String> templateData = new HashMap<>();
            templateData.put("eventName", eventName);
            awsSimpleEmailServiceGateway.sendEmailFromTemplate(userEntity.getEmail(), REFUND_EMAIL, templateData);
            for (DeviceTokenEntity deviceTokenEntity : userEntity.getDeviceTokens()) {

                if (deviceTokenEntity.getTokenStatus() != DeviceTokenEntity.TokenStatus.ACTIVE) {
                    continue;
                }

                Notification notification = new Notification(REFUND_TITLE, REFUND_BODY.replace("{{eventName}}", eventName));
                fcmGateway.sendPushNotification(deviceTokenEntity.getDeviceToken(), notification);
            }
        }

        orderRepository.save(orderEntity);
        LOG.info("Order ID: {} has been successfully refunded.", orderEntity.getId());
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

        if (authenticatedUser == null) {
            LOG.warn("User is not in system. Failing ticket load.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not found.");
        }

        Set<TicketEntity> userTickets = authenticatedUser.getTickets();
        List<Ticket> ticketList = new ArrayList<>();
        for (TicketEntity ticketEntity : userTickets) {

            if (statusSet.contains(ticketEntity.getStatus())) {
                continue;
            }

            final EventEntity eventEntity = ticketEntity.getEventEntity();

            if (eventEntity.getStatus() == EventEntity.Status.CANCELED) {
                LOG.error("Event is CANCELED without ticket ID: {} CANCELED", ticketEntity.getId());
                continue;
            }

            if (OffsetDateTime.now().isAfter(eventEntity.getEventEndTime())) {
                LOG.debug("Skipping ticket ID: {} since event is expired ID: {} is ended.", ticketEntity.getId(), eventEntity.getId());
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

        if (eventEntity.getStatus() == EventEntity.Status.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event is cancelled.");
        }

        if (OffsetDateTime.now().isAfter(eventEntity.getEventEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event has already ended.");
        }

        if (ticketTypeConfigEntity.getStatus() != TicketTypeConfigEntity.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket type is disabled.");
        }

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
    public Ticket manualRedeemTicket(UUID ticketId) {

        TicketEntity ticketEntity = verifyTicketValidity(ticketId, TicketEntity.Status.ACTIVE, TicketEntity.Status.ISSUED, TicketEntity.Status.REDEEMED);

        if (ticketEntity.getStatus() == TicketEntity.Status.REDEEMED) {
            LOG.info("TicketId: {} is already redeemed.", ticketId);
            return getTicket(ticketId, false);
        }

        //Check scanner permission to redeem.
        final UUID venueId = ticketEntity.getEventEntity().getVenueEntity().getId();
        if (!VenueService.checkVenueAuthorization(ticketEntity.getEventEntity().getVenueEntity().getId(), authenticatedUser.getVenueAccessEntities())) {
            LOG.warn("User ID: {} attempted to scan for Venue ID: {} that they are not a member of.", authenticatedUser.getId(), venueId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authorized to scan this ticket.");
        }

        ticketEntity.setStatus(TicketEntity.Status.REDEEMED);
        ticketRepository.save(ticketEntity);

        LOG.info("Manually redeemed ticket ID: {} by userID: {}", ticketId, authenticatedUser.getId());
        return getTicket(ticketId, false);
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

        TicketEntity ticketEntity;
        try {
            ticketEntity = verifyTicketValidity(ticketId, TicketEntity.Status.ACTIVE);
        } catch (Exception ex) {
            redemptionResult.setStatus(RedemptionResult.StatusEnum.DENY);
            LOG.warn("Failed to redeem ticket ID: {} for userID: {}", ticketId, authenticatedUser.getId());
            return redemptionResult;
        }

        final String ticketSecret = ticketEntity.getSecret();

        //Check scanner permission to redeem.
        final UUID venueId = ticketEntity.getEventEntity().getVenueEntity().getId();
        if (!VenueService.checkVenueAuthorization(ticketEntity.getEventEntity().getVenueEntity().getId(), authenticatedUser.getVenueAccessEntities())) {
            LOG.info("User ID: {} attempted to scan for Venue ID: {} that they are not a member of.", authenticatedUser.getId(), venueId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not authorized to scan this ticket.");
        }

        boolean isValid = gAuth.authorize(ticketSecret, otpCodeInteger);
        redemptionResult.setStatus(isValid ? RedemptionResult.StatusEnum.ALLOW : RedemptionResult.StatusEnum.DENY);

        if (isValid) {

            ticketEntity.setStatus(TicketEntity.Status.REDEEMED);
            ticketRepository.save(ticketEntity);

            LOG.info("Redeemed ticket ID: {} for userID: {}", ticketId, authenticatedUser.getId());
        } else {
            LOG.warn("Failed to redeem ticket ID: {} for userID: {}", ticketId, authenticatedUser.getId());
        }

        redemptionResult.setTicket(getTicket(ticketId, false));
        return redemptionResult;
    }

    @Override
    public int countTicketsRemaining(UUID ticketTypeConfigId) {

        Optional<TicketTypeConfigEntity> ticketTypeConfigEntityOptional = ticketTypeConfigRepository.findById(ticketTypeConfigId);

        if (!ticketTypeConfigEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket type config is invalid.");
        }

        int ticketsRemaining = obtainTicketsRemainingByType(ticketTypeConfigEntityOptional.get());
        return Math.min(ticketsRemaining, MAX_TICKETS_PER_ORDER);
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
            LOG.warn("User ID: {} attempted to activate/reactivate/redeem ticket not having {} status. Ticket ID: {}", authenticatedUser.getId(), expectedStatusList, ticketEntity.getId());
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

            if (token.getTokenStatus() != DeviceTokenEntity.TokenStatus.ACTIVE) {
                continue;
            }

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
            map.put("transfereeEmail", oldUser.getEmail());
        }

        return map;
    }

    @Override
    public void checkAndConfirmPendingTicketTransfers(UserEntity newUser) {

        if (newUser == null || newUser.getEmail() == null) {
            return;
        }

        final String receiverEmail = newUser.getEmail();
        List<TransferRequestEntity> pendingTickets = transferRequestRepository.findAllByReceiverEmailAndStatus(receiverEmail, TransferRequestEntity.Status.PENDING);

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
