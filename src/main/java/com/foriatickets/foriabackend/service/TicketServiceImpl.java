package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.repositories.EventRepository;
import com.foriatickets.foriabackend.repositories.TicketRepository;
import com.foriatickets.foriabackend.repositories.TicketTypeConfigRepository;
import com.foriatickets.foriabackend.repositories.UserRepository;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.openapitools.model.Ticket;
import org.openapitools.model.TicketLineItem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class TicketServiceImpl implements TicketService {

    private final EventRepository eventRepository;

    private final UserRepository userRepository;

    private final TicketTypeConfigRepository ticketTypeConfigRepository;

    private final TicketRepository ticketRepository;

    private static final Logger LOG = LogManager.getLogger();

    private final ModelMapper modelMapper;

    public TicketServiceImpl(EventRepository eventRepository, UserRepository userRepository, TicketTypeConfigRepository ticketTypeConfigRepository, TicketRepository ticketRepository, ModelMapper modelMapper) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.ticketTypeConfigRepository = ticketTypeConfigRepository;
        this.ticketRepository = ticketRepository;
        this.modelMapper = modelMapper;
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
        orderEntity.setId(orderId);
        orderEntity.setPurchaser(userEntity);
        orderEntity.setOrderTimestamp(OffsetDateTime.now());

        //Validate ticket config IDs are valid and issue tickets.
        Set<TicketEntity> tickets = new HashSet<>();
        Set<OrderTicketEntryEntity> orderTicketEntryEntities = new HashSet<>();
        for (UUID ticketTypeConfigId : ticketAmounts.keySet()) {
            boolean doesExist = ticketTypeConfigRepository.existsById(ticketTypeConfigId);
            if (!doesExist) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket type config is invalid.");
            }

            TicketEntity issuedTicket = issueTicket(userEntity.getId(), eventId, ticketTypeConfigId);
            tickets.add(issuedTicket);
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

        orderEntity.setTickets(orderTicketEntryEntities);
        orderEntity.setFees(orderFeeEntryEntities);

        //Calculate order total.

        //Charge payment method - tickets have been issued.


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
}
