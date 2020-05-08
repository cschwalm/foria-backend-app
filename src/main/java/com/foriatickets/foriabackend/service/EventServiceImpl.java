package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.gateway.FCMGateway;
import com.foriatickets.foriabackend.repositories.*;
import com.google.firebase.messaging.Notification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.openapitools.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

@Scope(scopeName = SCOPE_REQUEST)
@Service
@Transactional
public class EventServiceImpl implements EventService {

    private static Comparator<TicketTypeConfig> ticketTypeConfigComparator = (tt1, tt2) -> {

        if (tt1 == null || tt2 == null) {
            throw new NullPointerException();
        }

        if (tt1.equals(tt2)) {
            return 0;
        }

        final BigDecimal tt1Price = new BigDecimal(tt1.getPrice());
        final BigDecimal tt2Price = new BigDecimal(tt2.getPrice());

        final int priceCompare = tt1Price.compareTo(tt2Price);

        if (priceCompare == 0) {
            return tt1.getName().compareTo(tt2.getName());
        }

        return priceCompare;
    };

    private static final String CANCEL_TITLE = "Foria Event Canceled";
    private static final String CANCEL_BODY = "{{eventName}} has been canceled! Please check your email for more info.";
    private static final String CANCEL_EMAIL = "event_canceled_email";

    private static final Logger LOG = LogManager.getLogger();

    private final CalculationService calculationService;
    private final ModelMapper modelMapper;
    private final EventRepository eventRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final TicketFeeConfigRepository ticketFeeConfigRepository;
    private final TicketTypeConfigRepository ticketTypeConfigRepository;
    private final VenueRepository venueRepository;
    private final TicketService ticketService;
    private final OrderTicketEntryRepository orderTicketEntryRepository;
    private final AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway;
    private final FCMGateway fcmGateway;

    private final UserEntity authenticatedUser;

    @Autowired
    public EventServiceImpl(CalculationService calculationService,
                            EventRepository eventRepository,
                            PromoCodeRepository promoCodeRepository,
                            TicketFeeConfigRepository ticketFeeConfigRepository,
                            TicketTypeConfigRepository ticketTypeConfigRepository,
                            VenueRepository venueRepository, ModelMapper modelMapper,
                            TicketService ticketService,
                            OrderTicketEntryRepository orderTicketEntryRepository,
                            AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway,
                            FCMGateway fcmGateway,
                            UserRepository userRepository) {

        this.calculationService = calculationService;
        this.eventRepository = eventRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.ticketFeeConfigRepository = ticketFeeConfigRepository;
        this.ticketTypeConfigRepository = ticketTypeConfigRepository;
        this.venueRepository = venueRepository;
        this.modelMapper = modelMapper;
        this.ticketService = ticketService;
        this.orderTicketEntryRepository = orderTicketEntryRepository;
        this.awsSimpleEmailServiceGateway = awsSimpleEmailServiceGateway;
        this.fcmGateway = fcmGateway;

        //Load user from Auth0 token.
        String auth0Id = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        authenticatedUser = userRepository.findByAuth0Id(auth0Id);
        if (authenticatedUser == null && !auth0Id.equalsIgnoreCase("anonymousUser") && !auth0Id.equalsIgnoreCase("auth0")) {
            LOG.warn("Attempted to create event service with non-mapped auth0Id: {}", auth0Id);
        }
    }

    @Override
    public List<TicketTypeConfig> applyPromotionCode(UUID eventId, String promoCode) {

        if (eventId == null || StringUtils.isEmpty(promoCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID is null or code is empty.");
        }

        final EventEntity eventEntity = loadAndValidateEventEntity(eventId);
        final Set<TicketTypeConfigEntity> ticketTypeSet = eventEntity.getTicketTypeConfigEntity();

        final PromoCodeEntity promoCodeEntity = promoCodeRepository.findByTicketTypeConfigEntity_EventEntity_IdAndCode(eventId, promoCode);
        if (promoCodeEntity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion code is not valid.");
        }

        //Validate that codes are remaining.
        final int numCodesRedeemed = promoCodeEntity.getRedemptions().size();
        final int codesRemaining = promoCodeEntity.getQuantity() - numCodesRedeemed;
        if (codesRemaining <= 0) {
            LOG.info("Promotion code: {} has reached is max limit of redemptions.", promoCode);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion code used maximum number of times.");
        }

        ticketTypeSet.add(promoCodeEntity.getTicketTypeConfigEntity());
        final List<TicketTypeConfig> resultList = new ArrayList<>();
        for (TicketTypeConfigEntity ticketTypeConfigEntity : ticketTypeSet) {

            if (ticketTypeConfigEntity.getStatus() != TicketTypeConfigEntity.Status.ACTIVE) {
                continue;
            }

            TicketTypeConfig ticketTypeConfig = modelMapper.map(ticketTypeConfigEntity, TicketTypeConfig.class);
            populateExtraTicketTypeConfigInfo(ticketTypeConfig, eventEntity.getTicketFeeConfig());
            ticketTypeConfig.setAmountRemaining(Math.min(ticketTypeConfig.getAmountRemaining(), codesRemaining));
            resultList.add(ticketTypeConfig);
        }

        resultList.sort(ticketTypeConfigComparator);
        LOG.debug("Returned ticket type config set with promo code added.");
        return resultList;
    }

    @Override
    public void cancelEvent(UUID eventId, String reason) {

        if (eventId == null || StringUtils.isEmpty(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID is null or reason is empty.");
        }

        final Optional<EventEntity> eventEntityOptional = eventRepository.findById(eventId);
        if (!eventEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event ID does not exist.");
        }

        final EventEntity eventEntity = eventEntityOptional.get();
        final String eventName = eventEntity.getName();

        if (eventEntity.getStatus() == EventEntity.Status.CANCELED) {
            LOG.info("Event ID: {} is already canceled.", eventId);
            return;
        }

        if (OffsetDateTime.now().isAfter(eventEntity.getEventEndTime())) {
            LOG.warn("Event ID: {} is already ended. Failed to cancel event.", eventId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event has already ended. Failed to cancel.");
        }

        //Obtain all orders for event and cancel each.
        final Set<UserEntity> usersImpacted = new HashSet<>();
        final Set<OrderEntity> orderEntityList = new HashSet<>();
        for (TicketEntity ticketEntity : eventEntity.getTickets()) {
            final OrderEntity orderEntity = orderTicketEntryRepository.findByTicketEntity(ticketEntity).getOrderEntity();
            orderEntityList.add(orderEntity);
            usersImpacted.add(ticketEntity.getOwnerEntity());
        }

        LOG.info("Number of orders to cancel for ending Event Id: {} is: {}", eventId, orderEntityList.size());
        for (OrderEntity orderEntity : orderEntityList) {
            try {
                ticketService.refundOrder(orderEntity.getId());
            } catch (Exception ex) {
                LOG.error("FAILED to cancel order ID: {} while canceling event Id: {}. " + "Manual process is required.", orderEntity.getId(), eventId);
            }
        }

        //Send push notifications and emails to impacted customers.
        for (UserEntity userEntity : usersImpacted) {

            Map<String, String> templateData = new HashMap<>();
            templateData.put("eventName", eventName);
            templateData.put("cancelationReason", reason);

            awsSimpleEmailServiceGateway.sendEmailFromTemplate(userEntity.getEmail(), CANCEL_EMAIL, templateData);
            for (DeviceTokenEntity deviceTokenEntity : userEntity.getDeviceTokens()) {

                if (deviceTokenEntity.getTokenStatus() != DeviceTokenEntity.TokenStatus.ACTIVE) {
                    continue;
                }

                Notification notification = new Notification(CANCEL_TITLE, CANCEL_BODY.replace("{{eventName}}", eventName));
                fcmGateway.sendPushNotification(deviceTokenEntity.getDeviceToken(), notification);
            }
        }

        eventEntity.setStatus(EventEntity.Status.CANCELED);
        eventRepository.save(eventEntity);
        LOG.info("Event ID: {} has been successfully canceled.", eventId);
    }

    @Override
    public Event createEvent(Event event) {

        EventEntity eventEntity = modelMapper.map(event, EventEntity.class);

        if (eventEntity.getVenueEntity() == null || !venueRepository.existsById(event.getVenueId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue does not exist with ID.");
        }

        if (event.getId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID must not be set when creating an event.");
        }

        if (event.getTicketFeeConfig() == null || event.getTicketTypeConfig() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket config must be set.");
        }

        validateEventInfo(event);

        eventEntity.setStatus(EventEntity.Status.LIVE);
        eventEntity.setType(EventEntity.Type.valueOf(event.getType().name()));
        eventEntity = eventRepository.save(eventEntity);
        event.setId(eventEntity.getId());
        populateEventModelWithAddress(event, eventEntity.getVenueEntity());

        for (TicketFeeConfig ticketFeeConfig : event.getTicketFeeConfig()) {
            TicketFeeConfigEntity ticketFeeConfigEntity = modelMapper.map(ticketFeeConfig, TicketFeeConfigEntity.class);
            ticketFeeConfigEntity.setId(UUID.randomUUID());
            ticketFeeConfigEntity.setEventEntity(eventEntity);
            ticketFeeConfigEntity.setStatus(TicketFeeConfigEntity.Status.ACTIVE);
            ticketFeeConfigEntity = ticketFeeConfigRepository.save(ticketFeeConfigEntity);
            ticketFeeConfig.setId(ticketFeeConfigEntity.getId());
        }

        for (TicketTypeConfig ticketTypeConfig : event.getTicketTypeConfig()) {
            TicketTypeConfigEntity ticketTypeConfigEntity = modelMapper.map(ticketTypeConfig, TicketTypeConfigEntity.class);
            ticketTypeConfigEntity.setId(UUID.randomUUID());
            ticketTypeConfigEntity.setEventEntity(eventEntity);
            ticketTypeConfigEntity.setStatus(TicketTypeConfigEntity.Status.ACTIVE);
            ticketTypeConfigEntity.setType(TicketTypeConfigEntity.Type.PUBLIC);
            ticketTypeConfigEntity = ticketTypeConfigRepository.save(ticketTypeConfigEntity);
            ticketTypeConfig.setId(ticketTypeConfigEntity.getId());
        }

        LOG.info("Created event entry with ID: {}", eventEntity.getId());
        return event;
    }

    @Override
    public void createPromotionCode(PromotionCodeCreateRequest promotionCodeCreateRequest) {

        if (promotionCodeCreateRequest == null || StringUtils.isEmpty(promotionCodeCreateRequest.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request payload is empty.");
        }

        final Optional<TicketTypeConfigEntity> ticketTypeConfigEntity = ticketTypeConfigRepository.findById(promotionCodeCreateRequest.getTicketTypeConfigId());
        if (!ticketTypeConfigEntity.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket Type Config ID does not exist.");
        }
        final TicketTypeConfigEntity ticketTypeConfig = ticketTypeConfigEntity.get();

        final PromoCodeEntity promoCodeEntityLoad = promoCodeRepository.findByTicketTypeConfigEntity_EventEntity_IdAndCode(ticketTypeConfig.getEventEntity().getId(), promotionCodeCreateRequest.getCode());
        if (promoCodeEntityLoad != null) {
            LOG.info("Attempted to create promotional code: {} that already exists for ticketTypeConfigId: {}", promotionCodeCreateRequest.getCode(), ticketTypeConfig.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotional code already exists for ticket type config.");
        }

        if (ticketTypeConfig.getType() != TicketTypeConfigEntity.Type.PROMO) {
            LOG.info("Attempted to create promotional code: {} for tier that is not type PROMO for ticketTypeConfigId: {}", promotionCodeCreateRequest.getCode(), ticketTypeConfig.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attempted to create promotional code for tier that is not type PROMO.");
        }

        if (promotionCodeCreateRequest.getQuantity() > ticketTypeConfig.getAuthorizedAmount()) {
            LOG.info("Attempted to create promotional code: {} that has higher quantity than for ticketTypeConfigId: {}", promotionCodeCreateRequest.getCode(), ticketTypeConfig.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attempted to create promotional code that has higher quantity than tier.");
        }

        PromoCodeEntity promoCodeEntity = new PromoCodeEntity();
        promoCodeEntity.setCode(promotionCodeCreateRequest.getCode().toUpperCase().trim());
        promoCodeEntity.setCreatedDate(OffsetDateTime.now());
        promoCodeEntity.setName(promotionCodeCreateRequest.getName());
        promoCodeEntity.setDescription(promotionCodeCreateRequest.getDescription());
        promoCodeEntity.setQuantity(promotionCodeCreateRequest.getQuantity());
        promoCodeEntity.setTicketTypeConfigEntity(ticketTypeConfig);
        promoCodeEntity = promoCodeRepository.save(promoCodeEntity);

        LOG.info("Created promo code: {} for ticketTypeConfigId: {} with ID: {}", promotionCodeCreateRequest.getCode(), ticketTypeConfig.getId(), promoCodeEntity.getId());
    }

    @Override
    public List<Event> getAllActiveEvents() {

        List<Event> eventList = new ArrayList<>();
        List<EventEntity> eventEntities = eventRepository.findAllByOrderByEventStartTimeAsc();
        if (eventEntities == null || eventEntities.isEmpty()) {
            LOG.info("No events loaded in database.");
            return eventList;
        }

        Collections.sort(eventEntities);
        final OffsetDateTime now = OffsetDateTime.now();
        for (EventEntity eventEntity : eventEntities) {

            if (eventEntity.getStatus() == EventEntity.Status.CANCELED) {
                continue;
            }

            if (eventEntity.getVisibility() == EventEntity.Visibility.PRIVATE) {
                continue;
            }

            if (now.isAfter(eventEntity.getEventEndTime())) {
                continue;
            }

            final Event event = populateExtraTicketInfo(eventEntity);
            eventList.add(event);
        }

        LOG.debug("Returned {} events.", eventList.size());
        return eventList;
    }

    @Override
    public List<Attendee> getAttendees(UUID eventId) {

        if (eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID is null");
        }

        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue account is not authenticated / null.");
        }

        final Optional<EventEntity> eventEntityOptional = eventRepository.findById(eventId);
        if (!eventEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event ID does not exist.");
        }

        final EventEntity eventEntity = eventEntityOptional.get();
        if (!VenueService.checkVenueAuthorization(eventEntity.getVenueEntity().getId(), authenticatedUser.getVenueAccessEntities())) {
            LOG.warn("User ID: {} attempted to obtain attendees for eventId: {} that they are not authorized.", authenticatedUser.getId(), eventEntity.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user does not have access to venue.");
        }

        List<Attendee> attendeeList = new ArrayList<>();

        final Set<TicketEntity> ticketSet = eventEntity.getTickets();
        for (TicketEntity ticketEntity : ticketSet) {

            if (ticketEntity.getStatus() == TicketEntity.Status.CANCELED || ticketEntity.getStatus() == TicketEntity.Status.CANCELED_FRAUD) {
                continue;
            }

            final UserEntity ownerEntity = ticketEntity.getOwnerEntity();

            Attendee attendee = new Attendee();
            attendee.setTicketId(ticketEntity.getId());
            attendee.setTicket(modelMapper.map(ticketEntity, Ticket.class));
            attendee.setUserId(ownerEntity.getId());
            attendee.setFirstName(ownerEntity.getFirstName());
            attendee.setLastName(ownerEntity.getLastName());
            attendeeList.add(attendee);
        }

        LOG.info("Attendee list queried for eventID: {}", eventId);
        return attendeeList;
    }

    /**
     * Configures additional field for event that can't be simply mapped from entity.
     *
     * @param eventEntity Event to build.
     * @return Completed data.
     */
    private Event populateExtraTicketInfo(EventEntity eventEntity) {

        //Remove non-active price tiers.
        eventEntity.getTicketTypeConfigEntity().removeIf(ticketTypeConfigEntity -> {

            if (ticketTypeConfigEntity.getStatus() != TicketTypeConfigEntity.Status.ACTIVE) {
                LOG.debug("Skipping ticketTypeID: {} because it is INACTIVE.", ticketTypeConfigEntity.getId());
                return true;
            }

            return false;
        });

        //Remove promotional price tiers.
        eventEntity.getTicketTypeConfigEntity().removeIf(ticketTypeConfigEntity -> {

            if (ticketTypeConfigEntity.getType() != TicketTypeConfigEntity.Type.PUBLIC) {
                LOG.debug("Skipping ticketTypeID: {} because it is PROMO.", ticketTypeConfigEntity.getId());
                return true;
            }

            return false;
        });

        //Remove non-active fee tiers.
        eventEntity.getTicketFeeConfig().removeIf(ticketFeeConfigEntity -> {

            if (ticketFeeConfigEntity.getStatus() != TicketFeeConfigEntity.Status.ACTIVE) {
                LOG.debug("Skipping ticketFeeID: {} because it is INACTIVE.", ticketFeeConfigEntity.getId());
                return true;
            }

            return false;
        });

        Event event = modelMapper.map(eventEntity, Event.class);
        populateEventModelWithAddress(event, eventEntity.getVenueEntity());

        event.getTicketTypeConfig().sort(ticketTypeConfigComparator);
        for (TicketTypeConfig ticketTypeConfig : event.getTicketTypeConfig()) {
            populateExtraTicketTypeConfigInfo(ticketTypeConfig, eventEntity.getTicketFeeConfig());
        }

        return event;
    }

    /**
     * Populates fields that must be calculated.
     *
     * @param ticketTypeConfig Object to modify.
     */
    private void populateExtraTicketTypeConfigInfo(TicketTypeConfig ticketTypeConfig, Set<TicketFeeConfigEntity> feeSet) {

        int ticketsRemaining = ticketService.countTicketsRemaining(ticketTypeConfig.getId());
        ticketTypeConfig.setAmountRemaining(ticketsRemaining);

        //Add calculated fee to assist front ends.
        final int numPaidTickets = new BigDecimal(ticketTypeConfig.getPrice()).compareTo(BigDecimal.ZERO) <= 0 ? 0 : 1;
        CalculationServiceImpl.PriceCalculationInfo calc = calculationService.calculateFees(numPaidTickets, new BigDecimal(ticketTypeConfig.getPrice()), feeSet, true);
        BigDecimal feeSubtotal = calc.feeSubtotal.add(calc.paymentFeeSubtotal);
        ticketTypeConfig.setCalculatedFee(feeSubtotal.toPlainString());
    }

    @Override
    public Event getEvent(UUID eventId) {

        EventEntity eventEntity = loadAndValidateEventEntity(eventId);

        if (eventEntity.getStatus() == EventEntity.Status.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event is canceled. Please contact venue for more information.");
        }

        if (OffsetDateTime.now().isAfter(eventEntity.getEventEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event has already ended.");
        }

        return populateExtraTicketInfo(eventEntity);
    }

    @Override
    public Event updateEvent(UUID eventId, Event updatedEvent) {

        EventEntity eventEntity = loadAndValidateEventEntity(eventId);

        if (eventEntity.getStatus() == EventEntity.Status.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event is canceled. Edits are not allowed.");
        }

        LOG.info("Old event: {}", eventEntity);

        validateEventInfo(updatedEvent);

        eventEntity.setName(updatedEvent.getName());
        eventEntity.setTagLine(updatedEvent.getTagLine());
        eventEntity.setDescription(updatedEvent.getDescription());
        eventEntity.setImageUrl(updatedEvent.getImageUrl());
        eventEntity.setVisibility(EventEntity.Visibility.valueOf(updatedEvent.getVisibility().name()));
        eventEntity.setEventStartTime(updatedEvent.getStartTime());
        eventEntity.setEventEndTime(updatedEvent.getEndTime());
        eventEntity.setType(EventEntity.Type.valueOf(updatedEvent.getType().name()));
        eventEntity = eventRepository.save(eventEntity);

        LOG.info("Event ID: {} updated. \n New event: {}", eventEntity.getId(), eventEntity);
        return getEvent(eventId);
    }

    @Override
    public TicketFeeConfig createTicketFeeConfig(UUID eventId, TicketFeeConfig ticketFeeConfig) {

        EventEntity eventEntity = loadAndValidateEventEntity(eventId);

        if (!VenueService.checkVenueAuthorization(eventEntity.getVenueEntity().getId(), authenticatedUser.getVenueAccessEntities())) {
            LOG.warn("User ID: {} attempted to create ticket fee for eventId: {} that they are not authorized.", authenticatedUser.getId(), eventEntity.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user does not have access to venue.");
        }

        if (StringUtils.isEmpty(ticketFeeConfig.getName()) || StringUtils.isEmpty(ticketFeeConfig.getDescription())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Strings must not be empty.");
        }

        final BigDecimal amount = new BigDecimal(ticketFeeConfig.getAmount());

        if (ticketFeeConfig.getAmount() != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must not not be negative.");
        }

        TicketFeeConfigEntity ticketFeeConfigEntity = new TicketFeeConfigEntity();
        ticketFeeConfigEntity.setName(ticketFeeConfig.getName());
        ticketFeeConfigEntity.setDescription(ticketFeeConfig.getDescription());
        ticketFeeConfigEntity.setMethod(TicketFeeConfigEntity.FeeMethod.valueOf(ticketFeeConfig.getMethod().name()));
        ticketFeeConfigEntity.setType(TicketFeeConfigEntity.FeeType.valueOf(ticketFeeConfig.getType().name()));
        ticketFeeConfigEntity.setStatus(TicketFeeConfigEntity.Status.ACTIVE);
        ticketFeeConfigEntity.setAmount(amount);
        ticketFeeConfigEntity.setCurrency(ticketFeeConfig.getCurrency().toUpperCase());
        ticketFeeConfigEntity.setEventEntity(eventEntity);

        ticketFeeConfigEntity = ticketFeeConfigRepository.save(ticketFeeConfigEntity);
        ticketFeeConfig.setId(ticketFeeConfigEntity.getId());

        LOG.info("UserID: {} created a new feeConfig: {} for eventId: {}", authenticatedUser.getId(), ticketFeeConfig.getId(), eventEntity.getId());
        return ticketFeeConfig;
    }

    @Override
    public TicketTypeConfig createTicketTypeConfig(UUID eventId, TicketTypeConfig ticketTypeConfig) {

        EventEntity eventEntity = loadAndValidateEventEntity(eventId);

        if (!VenueService.checkVenueAuthorization(eventEntity.getVenueEntity().getId(), authenticatedUser.getVenueAccessEntities())) {
            LOG.warn("User ID: {} attempted to create ticket type for eventId: {} that they are not authorized.", authenticatedUser.getId(), eventEntity.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user does not have access to venue.");
        }

        if (ticketTypeConfig.getAuthorizedAmount() != null && ticketTypeConfig.getAuthorizedAmount() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authorized amount must be zero or greater.");
        }

        final BigDecimal price = new BigDecimal(ticketTypeConfig.getPrice());

        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must not be negative.");
        }

        if (StringUtils.isEmpty(ticketTypeConfig.getName()) || StringUtils.isEmpty(ticketTypeConfig.getDescription())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Strings must not be empty.");
        }

        TicketTypeConfigEntity ticketTypeConfigEntity = new TicketTypeConfigEntity();
        ticketTypeConfigEntity.setName(ticketTypeConfig.getName());
        ticketTypeConfigEntity.setDescription(ticketTypeConfig.getDescription());
        ticketTypeConfigEntity.setAuthorizedAmount(ticketTypeConfig.getAuthorizedAmount());
        ticketTypeConfigEntity.setStatus(TicketTypeConfigEntity.Status.ACTIVE);
        ticketTypeConfigEntity.setType(TicketTypeConfigEntity.Type.valueOf(ticketTypeConfig.getType().name()));
        ticketTypeConfigEntity.setPrice(price);
        ticketTypeConfigEntity.setCurrency(ticketTypeConfig.getCurrency().toUpperCase());
        ticketTypeConfigEntity.setEventEntity(eventEntity);

        ticketTypeConfigEntity = ticketTypeConfigRepository.save(ticketTypeConfigEntity);
        ticketTypeConfig.setId(ticketTypeConfigEntity.getId());

        LOG.info("UserID: {} created a new price tier: {} for eventId: {}", authenticatedUser.getId(), ticketTypeConfigEntity.getId(), eventEntity.getId());
        return ticketTypeConfig;
    }

    @Override
    public TicketFeeConfig removeTicketFeeConfig(UUID eventId, UUID ticketFeeConfigId) {

        EventEntity eventEntity = loadAndValidateEventEntity(eventId);

        if (!VenueService.checkVenueAuthorization(eventEntity.getVenueEntity().getId(), authenticatedUser.getVenueAccessEntities())) {
            LOG.warn("User ID: {} attempted to remove ticket fee for eventId: {} that they are not authorized.", authenticatedUser.getId(), eventEntity.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user does not have access to venue.");
        }

        Optional<TicketFeeConfigEntity> ticketFeeConfigEntityOptional = ticketFeeConfigRepository.findById(ticketFeeConfigId);

        if (!ticketFeeConfigEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TicketFeeConfigId does not exist.");
        }

        TicketFeeConfigEntity ticketFeeConfigEntity = ticketFeeConfigEntityOptional.get();

        if (ticketFeeConfigEntity.getEventEntity().getId() != eventId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TicketFeeConfigId does not belong to specified event.");
        }

        ticketFeeConfigEntity.setStatus(TicketFeeConfigEntity.Status.INACTIVE);
        ticketFeeConfigEntity = ticketFeeConfigRepository.save(ticketFeeConfigEntity);

        LOG.info("UserID: {} inactivated ticketFeeConfig: {} for eventId: {}", authenticatedUser.getId(), ticketFeeConfigEntity.getId(), eventEntity.getId());
        return modelMapper.map(ticketFeeConfigEntity, TicketFeeConfig.class);
    }

    @Override
    public TicketTypeConfig removeTicketTypeConfig(UUID eventId, UUID ticketTypeConfigId) {

        EventEntity eventEntity = loadAndValidateEventEntity(eventId);

        if (!VenueService.checkVenueAuthorization(eventEntity.getVenueEntity().getId(), authenticatedUser.getVenueAccessEntities())) {
            LOG.warn("User ID: {} attempted to remove ticket type for eventId: {} that they are not authorized.", authenticatedUser.getId(), eventEntity.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user does not have access to venue.");
        }

        Optional<TicketTypeConfigEntity> ticketTypeConfigEntityOptional = ticketTypeConfigRepository.findById(ticketTypeConfigId);

        if (!ticketTypeConfigEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ticketTypeConfigEntity does not exist.");
        }

        TicketTypeConfigEntity ticketTypeConfigEntity = ticketTypeConfigEntityOptional.get();

        if (ticketTypeConfigEntity.getEventEntity().getId() != eventId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TicketTypeConfigId does not belong to specified event.");
        }

        ticketTypeConfigEntity.setStatus(TicketTypeConfigEntity.Status.INACTIVE);
        ticketTypeConfigEntity = ticketTypeConfigRepository.save(ticketTypeConfigEntity);

        LOG.info("UserID: {} inactivated ticketTypeConfig: {} for eventId: {}", authenticatedUser.getId(), ticketTypeConfigEntity.getId(), eventEntity.getId());
        return modelMapper.map(ticketTypeConfigEntity, TicketTypeConfig.class);
    }

    /**
     * Transforms venue address and name to set inside of an Event API model.
     *
     * @param event Model to set.
     * @param venueEntity Venue info.
     */
    static void populateEventModelWithAddress(final Event event, final VenueEntity venueEntity) {

        EventAddress addr = new EventAddress();
        addr.setVenueName(venueEntity.getName());
        addr.setStreetAddress(venueEntity.getContactStreetAddress());
        addr.setCity(venueEntity.getContactCity());
        addr.setState(venueEntity.getContactState());
        addr.setZip(venueEntity.getContactZip());
        addr.setCountry(venueEntity.getContactCountry());

        event.setAddress(addr);
    }

    /**
     * Throws a REST friendly expection if the event data is malformed.
     * @param event Event to check.
     */
    private void validateEventInfo(Event event) {

        //Check start/end time is not less now
        final OffsetDateTime updatedStartTime = event.getStartTime();
        final OffsetDateTime updatedEndTime = event.getEndTime();
        if (updatedStartTime.isAfter(updatedEndTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time is after end time.");
        }

        if (event.getEndTime().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after now.");
        }

        if (StringUtils.isEmpty(event.getName()) || StringUtils.isEmpty(event.getTagLine())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event name/tagline is empty.");
        }

        if (StringUtils.isEmpty(event.getDescription())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event description is empty.");
        }

        if (StringUtils.isEmpty(event.getImageUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL is empty");
        }

        if (!event.getType().name().equals(EventEntity.Type.PRIMARY.name()) && !event.getType().name().equals(EventEntity.Type.RESELL.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event type is invalid.");
        }
    }

    /**
     * Loads the event entity from database with the specified ID.
     * @param eventId ID to load.
     * @return EventEntity. Not null.
     */
    private EventEntity loadAndValidateEventEntity(UUID eventId) {

        if (eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID is null.");
        }

        final Optional<EventEntity> eventEntityOptional = eventRepository.findById(eventId);
        if (!eventEntityOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID does not exist.");
        }

        return eventEntityOptional.get();
    }
}
