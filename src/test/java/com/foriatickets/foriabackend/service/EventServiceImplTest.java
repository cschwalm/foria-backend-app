package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.config.BeanConfig;
import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.gateway.FCMGateway;
import com.foriatickets.foriabackend.repositories.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.internal.util.Assert;
import org.openapitools.model.Attendee;
import org.openapitools.model.Event;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static com.foriatickets.foriabackend.entities.TicketEntity.Status.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
public class EventServiceImplTest {

    @Mock
    private CalculationService calculationService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketFeeConfigRepository ticketFeeConfigRepository;

    @Mock
    private TicketTypeConfigRepository ticketTypeConfigRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private TicketService ticketService;

    @Mock
    private AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway;

    @Mock
    private FCMGateway fcmGateway;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderTicketEntryRepository orderTicketEntryRepository;

    private EventService eventService;

    private List<EventEntity> mockEventList;

    private VenueEntity venueEntityMock;

    @Before
    public void setUp() {

        mockEventList = new ArrayList<>();

        venueEntityMock = mock(VenueEntity.class);
        when(venueEntityMock.getContactStreetAddress()).thenReturn("12345 Maple Ln");
        when(venueEntityMock.getContactCity()).thenReturn("Test City");
        when(venueEntityMock.getContactState()).thenReturn("MO");
        when(venueEntityMock.getContactZip()).thenReturn("55555");
        when(venueEntityMock.getContactCity()).thenReturn("USA");
        when(venueEntityMock.getName()).thenReturn("Test");
        when(venueEntityMock.getId()).thenReturn(UUID.randomUUID());

        CalculationServiceImpl.PriceCalculationInfo priceCalculationInfo = new CalculationServiceImpl.PriceCalculationInfo();
        priceCalculationInfo.ticketSubtotal = new BigDecimal("100.00");
        priceCalculationInfo.feeSubtotal = new BigDecimal("50.00");
        priceCalculationInfo.currencyCode = "USD";
        priceCalculationInfo.paymentFeeSubtotal = new BigDecimal("123.12");
        priceCalculationInfo.grandTotal = new BigDecimal("500.00");

        EventEntity mockEvent1 = mock(EventEntity.class);
        when(mockEvent1.getId()).thenReturn(UUID.randomUUID());
        when(mockEvent1.getName()).thenReturn("Test");
        when(mockEvent1.getEventStartTime()).thenReturn(OffsetDateTime.MIN);
        when(mockEvent1.getEventEndTime()).thenReturn(OffsetDateTime.MAX);
        when(mockEvent1.getDescription()).thenReturn("Test Event");
        when(mockEvent1.getVenueEntity()).thenReturn(venueEntityMock);
        when(mockEvent1.getStatus()).thenReturn(EventEntity.Status.LIVE);
        when(mockEvent1.getVisibility()).thenReturn(EventEntity.Visibility.PUBLIC);
        mockEventList.add(mockEvent1);

        EventEntity mockEventPrivate = mock(EventEntity.class);
        when(mockEventPrivate.getId()).thenReturn(UUID.randomUUID());
        when(mockEventPrivate.getName()).thenReturn("Test");
        when(mockEventPrivate.getEventStartTime()).thenReturn(OffsetDateTime.MIN);
        when(mockEventPrivate.getEventEndTime()).thenReturn(OffsetDateTime.MAX);
        when(mockEventPrivate.getDescription()).thenReturn("Test Private Event");
        when(mockEventPrivate.getVenueEntity()).thenReturn(venueEntityMock);
        when(mockEventPrivate.getStatus()).thenReturn(EventEntity.Status.LIVE);
        when(mockEventPrivate.getVisibility()).thenReturn(EventEntity.Visibility.PRIVATE);
        mockEventList.add(mockEventPrivate);

        EventEntity mockEventCanceled = mock(EventEntity.class);
        when(mockEventCanceled.getId()).thenReturn(UUID.randomUUID());
        when(mockEventCanceled.getName()).thenReturn("Test");
        when(mockEventCanceled.getEventStartTime()).thenReturn(OffsetDateTime.MIN);
        when(mockEventCanceled.getEventEndTime()).thenReturn(OffsetDateTime.MAX);
        when(mockEventCanceled.getDescription()).thenReturn("Test Private Event");
        when(mockEventCanceled.getVenueEntity()).thenReturn(venueEntityMock);
        when(mockEventCanceled.getStatus()).thenReturn(EventEntity.Status.CANCELED);
        when(mockEventCanceled.getVisibility()).thenReturn(EventEntity.Visibility.PUBLIC);
        mockEventList.add(mockEventCanceled);

        EventEntity mockEventEnded = mock(EventEntity.class);
        when(mockEventEnded.getId()).thenReturn(UUID.randomUUID());
        when(mockEventEnded.getName()).thenReturn("Test");
        when(mockEventEnded.getEventStartTime()).thenReturn(OffsetDateTime.MIN);
        when(mockEventEnded.getEventEndTime()).thenReturn(OffsetDateTime.MIN);
        when(mockEventEnded.getDescription()).thenReturn("Test Private Event");
        when(mockEventEnded.getVenueEntity()).thenReturn(venueEntityMock);
        when(mockEventEnded.getStatus()).thenReturn(EventEntity.Status.LIVE);
        when(mockEventEnded.getVisibility()).thenReturn(EventEntity.Visibility.PUBLIC);
        mockEventList.add(mockEventEnded);

        Set<TicketTypeConfigEntity> ticketTypeConfigEntitySet = new HashSet<>();
        TicketTypeConfigEntity ticketTypeConfigMock = mock(TicketTypeConfigEntity.class);
        when(ticketTypeConfigMock.getEventEntity()).thenReturn(mockEvent1);
        when(ticketTypeConfigMock.getCurrency()).thenReturn("USD");
        when(ticketTypeConfigMock.getPrice()).thenReturn(new BigDecimal("100.00"));
        when(ticketTypeConfigMock.getAuthorizedAmount()).thenReturn(100);
        when(ticketTypeConfigMock.getId()).thenReturn(UUID.randomUUID());
        when(ticketTypeConfigMock.getName()).thenReturn("Test Type");
        when(ticketTypeConfigMock.getDescription()).thenReturn("Test Type Desc");
        ticketTypeConfigEntitySet.add(ticketTypeConfigMock);
        when(mockEvent1.getTicketTypeConfigEntity()).thenReturn(ticketTypeConfigEntitySet);

        Set<TicketFeeConfigEntity> ticketFeeConfigEntitySet = new HashSet<>();
        TicketFeeConfigEntity ticketFeeConfigEntity = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntity.getEventEntity()).thenReturn(mockEvent1);
        when(ticketFeeConfigEntity.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntity.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        when(ticketFeeConfigEntity.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntity.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(ticketFeeConfigEntity.getId()).thenReturn(UUID.randomUUID());
        when(ticketFeeConfigEntity.getName()).thenReturn("Test Type");
        when(ticketFeeConfigEntity.getDescription()).thenReturn("Test Type Desc");
        ticketFeeConfigEntitySet.add(ticketFeeConfigEntity);
        when(mockEvent1.getTicketFeeConfig()).thenReturn(ticketFeeConfigEntitySet);

        when(ticketService.countTicketsRemaining(any())).thenReturn(5);
        when(calculationService.calculateFees(eq(1), any(), eq(ticketFeeConfigEntitySet))).thenReturn(priceCalculationInfo);

        ModelMapper modelMapper = new ModelMapper();
        for (PropertyMap map : BeanConfig.getModelMappers()) {
            //noinspection unchecked
            modelMapper.addMappings(map);
        }

        UUID userId = UUID.randomUUID();
        UserEntity authenticatedUser = mock(UserEntity.class);
        when(authenticatedUser.getId()).thenReturn(userId);
        Set<VenueAccessEntity> venueAccessEntitySet = new HashSet<>();
        VenueAccessEntity venueAccessEntity = mock(VenueAccessEntity.class);
        when(venueAccessEntity.getVenueEntity()).thenReturn(venueEntityMock);
        when(venueAccessEntity.getUserEntity()).thenReturn(authenticatedUser);
        venueAccessEntitySet.add(venueAccessEntity);
        when(authenticatedUser.getVenueAccessEntities()).thenReturn(venueAccessEntitySet);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("test");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userRepository.findByAuth0Id(eq("test"))).thenReturn(authenticatedUser);

        eventService = new EventServiceImpl(calculationService, eventRepository, ticketFeeConfigRepository, ticketTypeConfigRepository, venueRepository, modelMapper, ticketService, orderTicketEntryRepository, awsSimpleEmailServiceGateway, fcmGateway, userRepository);
    }

    @Test
    public void getAllActiveEvents() {

        when(eventRepository.findAllByOrderByEventStartTimeAsc()).thenReturn(mockEventList);
        List<Event> actual = eventService.getAllActiveEvents();

        Assert.notNull(actual);
        Assert.isTrue(mockEventList.size() - 3 == actual.size()); //Test private event
    }

    @Test
    public void getEvent() {

        final UUID eventId = mockEventList.get(0).getId();
        when(eventRepository.findById(eq(eventId))).thenReturn(Optional.of(mockEventList.get(0)));

        Event actual = eventService.getEvent(eventId);
        Assert.notNull(actual);
        assertEquals(EventEntity.Visibility.PUBLIC.name(), actual.getVisibility().name());
    }

    @Test
    public void getEvent_Private() {

        final UUID eventId = mockEventList.get(1).getId();
        when(eventRepository.findById(eq(eventId))).thenReturn(Optional.of(mockEventList.get(1)));

        Event actual = eventService.getEvent(eventId);
        Assert.notNull(actual);
        assertEquals(EventEntity.Visibility.PRIVATE.name(), actual.getVisibility().name());
    }

    @Test(expected = ResponseStatusException.class)
    public void getEvent_Canceled() {

        final UUID eventId = mockEventList.get(2).getId();
        when(eventRepository.findById(eq(eventId))).thenReturn(Optional.of(mockEventList.get(2)));

        Event actual = eventService.getEvent(eventId);
        Assert.notNull(actual);
    }

    @Test(expected = ResponseStatusException.class)
    public void getEvent_Expired() {

        final UUID eventId = mockEventList.get(3).getId();
        when(eventRepository.findById(eq(eventId))).thenReturn(Optional.of(mockEventList.get(3)));

        Event actual = eventService.getEvent(eventId);
        Assert.notNull(actual);
    }

    @Test
    public void cancelEvent() {

        final UUID eventId = UUID.randomUUID();

        Set<DeviceTokenEntity> deviceTokenEntities = new HashSet<>();
        DeviceTokenEntity deviceTokenEntity = mock(DeviceTokenEntity.class);
        when(deviceTokenEntity.getTokenStatus()).thenReturn(DeviceTokenEntity.TokenStatus.ACTIVE);
        when(deviceTokenEntity.getDeviceToken()).thenReturn("fake_token");

        DeviceTokenEntity deviceTokenEntity2 = mock(DeviceTokenEntity.class);
        when(deviceTokenEntity2.getTokenStatus()).thenReturn(DeviceTokenEntity.TokenStatus.DEACTIVATED);
        when(deviceTokenEntity2.getDeviceToken()).thenReturn("fake_token2");
        deviceTokenEntities.add(deviceTokenEntity);
        deviceTokenEntities.add(deviceTokenEntity2);

        UserEntity purchaser = mock(UserEntity.class);
        when(purchaser.getId()).thenReturn(UUID.randomUUID());
        when(purchaser.getEmail()).thenReturn("test@test.com");
        when(purchaser.getDeviceTokens()).thenReturn(deviceTokenEntities);

        UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(UUID.randomUUID());
        when(owner.getEmail()).thenReturn("test2@test.com");
        when(owner.getDeviceTokens()).thenReturn(deviceTokenEntities);

        EventEntity eventEntity = mock(EventEntity.class);
        when(eventEntity.getId()).thenReturn(UUID.randomUUID());
        when(eventEntity.getEventEndTime()).thenReturn(OffsetDateTime.MAX.minusYears(1L));
        when(eventEntity.getName()).thenReturn("Test Event");

        TicketEntity ticketEntity = mock(TicketEntity.class);
        when(ticketEntity.getId()).thenReturn(UUID.randomUUID());
        when(ticketEntity.getStatus()).thenReturn(ACTIVE);
        when(ticketEntity.getIssuedDate()).thenReturn(OffsetDateTime.now());
        when(ticketEntity.getSecret()).thenReturn("secret");
        when(ticketEntity.getPurchaserEntity()).thenReturn(purchaser);
        when(ticketEntity.getOwnerEntity()).thenReturn(owner);
        when(ticketEntity.getEventEntity()).thenReturn(eventEntity);

        TicketEntity ticketEntity2 = mock(TicketEntity.class);
        when(ticketEntity2.getId()).thenReturn(UUID.randomUUID());
        when(ticketEntity2.getStatus()).thenReturn(ISSUED);
        when(ticketEntity2.getIssuedDate()).thenReturn(OffsetDateTime.now());
        when(ticketEntity2.getSecret()).thenReturn("secret");
        when(ticketEntity2.getPurchaserEntity()).thenReturn(purchaser);
        when(ticketEntity2.getOwnerEntity()).thenReturn(owner);
        when(ticketEntity2.getEventEntity()).thenReturn(eventEntity);

        Set<TicketEntity> ticketSet = new HashSet<>();
        ticketSet.add(ticketEntity);
        ticketSet.add(ticketEntity2);
        when(eventEntity.getTickets()).thenReturn(ticketSet);
        when(eventRepository.findById(eq(eventId))).thenReturn(Optional.of(eventEntity));

        OrderEntity orderEntity = mock(OrderEntity.class);
        when(orderEntity.getId()).thenReturn(UUID.randomUUID());
        when(orderEntity.getCurrency()).thenReturn("USD");
        when(orderEntity.getTotal()).thenReturn(BigDecimal.ZERO);
        when(orderEntity.getOrderTimestamp()).thenReturn(OffsetDateTime.now());
        when(orderEntity.getChargeReferenceId()).thenReturn(null);
        when(orderEntity.getStatus()).thenReturn(OrderEntity.Status.COMPLETED);

        Set<OrderTicketEntryEntity> orderTicketEntryEntities = new HashSet<>();
        OrderTicketEntryEntity orderTicketEntryEntity = mock(OrderTicketEntryEntity.class);
        when(orderTicketEntryEntity.getOrderEntity()).thenReturn(orderEntity);
        when(orderTicketEntryEntity.getId()).thenReturn(UUID.randomUUID());
        when(orderTicketEntryEntity.getTicketEntity()).thenReturn(ticketEntity);

        OrderTicketEntryEntity orderTicketEntryEntity2 = mock(OrderTicketEntryEntity.class);
        when(orderTicketEntryEntity2.getOrderEntity()).thenReturn(orderEntity);
        when(orderTicketEntryEntity2.getId()).thenReturn(UUID.randomUUID());
        when(orderTicketEntryEntity2.getTicketEntity()).thenReturn(ticketEntity2);

        orderTicketEntryEntities.add(orderTicketEntryEntity);
        orderTicketEntryEntities.add(orderTicketEntryEntity2);
        when(orderEntity.getTickets()).thenReturn(orderTicketEntryEntities);

        when(orderTicketEntryRepository.findByTicketEntity(ticketEntity)).thenReturn(orderTicketEntryEntity);
        when(orderTicketEntryRepository.findByTicketEntity(ticketEntity2)).thenReturn(orderTicketEntryEntity2);
        doNothing().when(ticketService).refundOrder(any());
        doNothing().when(awsSimpleEmailServiceGateway).sendEmailFromTemplate(any(), any(), any());
        doNothing().when(fcmGateway).sendPushNotification(any(), any());

        eventService.cancelEvent(eventId, "This is a test.");

        verify(eventRepository).save(any());
        verify(awsSimpleEmailServiceGateway, times(1)).sendEmailFromTemplate(any(), any(), any());
        verify(fcmGateway, times(1)).sendPushNotification(any(), any());
    }

    @Test
    public void getAttendeeList() {

        final UUID eventId = UUID.randomUUID();

        UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(UUID.randomUUID());
        when(owner.getEmail()).thenReturn("test2@test.com");

        EventEntity eventEntity = mock(EventEntity.class);
        when(eventEntity.getId()).thenReturn(UUID.randomUUID());
        when(eventEntity.getEventEndTime()).thenReturn(OffsetDateTime.MAX.minusYears(1L));
        when(eventEntity.getName()).thenReturn("Test Event");
        when(eventEntity.getVenueEntity()).thenReturn(venueEntityMock);

        TicketEntity ticketEntity = mock(TicketEntity.class);
        when(ticketEntity.getId()).thenReturn(UUID.randomUUID());
        when(ticketEntity.getStatus()).thenReturn(ACTIVE);
        when(ticketEntity.getIssuedDate()).thenReturn(OffsetDateTime.now());
        when(ticketEntity.getSecret()).thenReturn("secret");
        when(ticketEntity.getOwnerEntity()).thenReturn(owner);
        when(ticketEntity.getEventEntity()).thenReturn(eventEntity);

        TicketEntity ticketEntity2 = mock(TicketEntity.class);
        when(ticketEntity2.getId()).thenReturn(UUID.randomUUID());
        when(ticketEntity2.getStatus()).thenReturn(ISSUED);
        when(ticketEntity2.getIssuedDate()).thenReturn(OffsetDateTime.now());
        when(ticketEntity2.getSecret()).thenReturn("secret");
        when(ticketEntity2.getOwnerEntity()).thenReturn(owner);
        when(ticketEntity2.getEventEntity()).thenReturn(eventEntity);

        TicketEntity ticketEntity3 = mock(TicketEntity.class);
        when(ticketEntity3.getId()).thenReturn(UUID.randomUUID());
        when(ticketEntity3.getStatus()).thenReturn(CANCELED);
        when(ticketEntity3.getIssuedDate()).thenReturn(OffsetDateTime.now());
        when(ticketEntity3.getSecret()).thenReturn("secret");
        when(ticketEntity3.getOwnerEntity()).thenReturn(owner);
        when(ticketEntity3.getEventEntity()).thenReturn(eventEntity);

        Set<TicketEntity> ticketSet = new HashSet<>();
        ticketSet.add(ticketEntity);
        ticketSet.add(ticketEntity2);
        ticketSet.add(ticketEntity3);
        when(eventEntity.getTickets()).thenReturn(ticketSet);
        when(eventRepository.findById(eq(eventId))).thenReturn(Optional.of(eventEntity));

        List<Attendee> actual = eventService.getAttendees(eventId);
        assertEquals(2, actual.size());
    }
}