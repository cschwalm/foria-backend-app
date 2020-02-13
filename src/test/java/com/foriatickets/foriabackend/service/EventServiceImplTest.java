package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.config.BeanConfig;
import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.gateway.FCMGateway;
import com.foriatickets.foriabackend.repositories.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.internal.util.Assert;
import org.openapitools.model.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static com.foriatickets.foriabackend.entities.TicketEntity.Status.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
    private PromoCodeRepository promoCodeRepository;

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

    @SuppressWarnings("rawtypes")
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
        when(ticketTypeConfigMock.getStatus()).thenReturn(TicketTypeConfigEntity.Status.ACTIVE);
        when(ticketTypeConfigMock.getType()).thenReturn(TicketTypeConfigEntity.Type.PUBLIC);

        TicketTypeConfigEntity ticketTypeConfigMockInactive = mock(TicketTypeConfigEntity.class);
        when(ticketTypeConfigMockInactive.getEventEntity()).thenReturn(mockEvent1);
        when(ticketTypeConfigMockInactive.getCurrency()).thenReturn("USD");
        when(ticketTypeConfigMockInactive.getPrice()).thenReturn(new BigDecimal("1333300.00"));
        when(ticketTypeConfigMockInactive.getAuthorizedAmount()).thenReturn(100);
        when(ticketTypeConfigMockInactive.getId()).thenReturn(UUID.randomUUID());
        when(ticketTypeConfigMockInactive.getName()).thenReturn("Test Type Inactive");
        when(ticketTypeConfigMockInactive.getDescription()).thenReturn("Test Type Desc");
        when(ticketTypeConfigMockInactive.getStatus()).thenReturn(TicketTypeConfigEntity.Status.INACTIVE);
        when(ticketTypeConfigMockInactive.getType()).thenReturn(TicketTypeConfigEntity.Type.PUBLIC);

        TicketTypeConfigEntity ticketTypeConfigMockPromo = mock(TicketTypeConfigEntity.class);
        when(ticketTypeConfigMockPromo.getEventEntity()).thenReturn(mockEvent1);
        when(ticketTypeConfigMockPromo.getCurrency()).thenReturn("USD");
        when(ticketTypeConfigMockPromo.getPrice()).thenReturn(new BigDecimal("177300.00"));
        when(ticketTypeConfigMockPromo.getAuthorizedAmount()).thenReturn(500);
        when(ticketTypeConfigMockPromo.getId()).thenReturn(UUID.randomUUID());
        when(ticketTypeConfigMockPromo.getName()).thenReturn("Test Type Promo");
        when(ticketTypeConfigMockPromo.getDescription()).thenReturn("Test Type Promo D");
        when(ticketTypeConfigMockPromo.getStatus()).thenReturn(TicketTypeConfigEntity.Status.ACTIVE);
        when(ticketTypeConfigMockPromo.getType()).thenReturn(TicketTypeConfigEntity.Type.PROMO);

        ticketTypeConfigEntitySet.add(ticketTypeConfigMock);
        ticketTypeConfigEntitySet.add(ticketTypeConfigMockInactive);
        ticketTypeConfigEntitySet.add(ticketTypeConfigMockPromo);
        when(mockEvent1.getTicketTypeConfigEntity()).thenReturn(ticketTypeConfigEntitySet);

        Set<TicketFeeConfigEntity> ticketFeeConfigEntitySet = new HashSet<>();
        TicketFeeConfigEntity ticketFeeConfigEntity = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntity.getEventEntity()).thenReturn(mockEvent1);
        when(ticketFeeConfigEntity.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntity.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        when(ticketFeeConfigEntity.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntity.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntity.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(ticketFeeConfigEntity.getId()).thenReturn(UUID.randomUUID());
        when(ticketFeeConfigEntity.getName()).thenReturn("Test Type");
        when(ticketFeeConfigEntity.getDescription()).thenReturn("Test Type Desc");

        TicketFeeConfigEntity ticketFeeConfigEntityInactive = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityInactive.getEventEntity()).thenReturn(mockEvent1);
        when(ticketFeeConfigEntityInactive.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityInactive.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        when(ticketFeeConfigEntityInactive.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityInactive.getStatus()).thenReturn(TicketFeeConfigEntity.Status.INACTIVE);
        when(ticketFeeConfigEntityInactive.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(ticketFeeConfigEntityInactive.getId()).thenReturn(UUID.randomUUID());
        when(ticketFeeConfigEntityInactive.getName()).thenReturn("Test Type Inactive");
        when(ticketFeeConfigEntityInactive.getDescription()).thenReturn("Test Type Desc");

        ticketFeeConfigEntitySet.add(ticketFeeConfigEntity);
        ticketFeeConfigEntitySet.add(ticketFeeConfigEntityInactive);
        when(mockEvent1.getTicketFeeConfig()).thenReturn(ticketFeeConfigEntitySet);

        when(ticketService.countTicketsRemaining(any())).thenReturn(5);
        when(calculationService.calculateFees(eq(1), any(), eq(ticketFeeConfigEntitySet), eq(true))).thenReturn(priceCalculationInfo);

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

        eventService = new EventServiceImpl(calculationService, eventRepository, promoCodeRepository, ticketFeeConfigRepository, ticketTypeConfigRepository, venueRepository, modelMapper, ticketService, orderTicketEntryRepository, awsSimpleEmailServiceGateway, fcmGateway, userRepository);
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

        assertEquals(1, actual.getTicketTypeConfig().size());
        assertEquals(1, actual.getTicketFeeConfig().size());
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

    @Test
    public void createTicketFeeConfig() {

        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getEventEndTime()).thenReturn(OffsetDateTime.MAX.minusYears(1L));
        when(eventEntityMock.getName()).thenReturn("Test Event");
        when(eventEntityMock.getVenueEntity()).thenReturn(venueEntityMock);

        UUID eventId = UUID.randomUUID();
        TicketFeeConfig ticketFeeConfig = new TicketFeeConfig();
        ticketFeeConfig.setCurrency("USD");
        ticketFeeConfig.setType(TicketFeeConfig.TypeEnum.VENUE);
        ticketFeeConfig.setMethod(TicketFeeConfig.MethodEnum.FLAT);
        ticketFeeConfig.setDescription("test");
        ticketFeeConfig.setName("test");
        ticketFeeConfig.setAmount("0.25");

        final ArgumentCaptor<TicketFeeConfigEntity> captor = ArgumentCaptor.forClass(TicketFeeConfigEntity.class);
        when(ticketFeeConfigRepository.save(any())).thenReturn(mock(TicketFeeConfigEntity.class));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));

        TicketFeeConfig actual = eventService.createTicketFeeConfig(eventId, ticketFeeConfig);

        assertNotNull(actual);
        verify(ticketFeeConfigRepository, times(1)).save(captor.capture());

        TicketFeeConfigEntity mock = captor.getValue();
        assertEquals(ticketFeeConfig.getCurrency(), mock.getCurrency());
        assertEquals(ticketFeeConfig.getType().name(), mock.getType().name());
        assertEquals(ticketFeeConfig.getMethod().name(), mock.getMethod().name());
        assertEquals(ticketFeeConfig.getDescription(), mock.getDescription());
        assertEquals(ticketFeeConfig.getName(), mock.getName());
        assertEquals(ticketFeeConfig.getAmount(), mock.getAmount().toPlainString());
    }

    @Test
    public void createTicketTypeConfig() {

        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getEventEndTime()).thenReturn(OffsetDateTime.MAX.minusYears(1L));
        when(eventEntityMock.getName()).thenReturn("Test Event");
        when(eventEntityMock.getVenueEntity()).thenReturn(venueEntityMock);

        UUID eventId = UUID.randomUUID();
        TicketTypeConfig ticketTypeConfig = new TicketTypeConfig();
        ticketTypeConfig.setCurrency("USD");
        ticketTypeConfig.setDescription("test");
        ticketTypeConfig.setName("test");
        ticketTypeConfig.setPrice("0.25");
        ticketTypeConfig.setAuthorizedAmount(0);

        final ArgumentCaptor<TicketTypeConfigEntity> captor = ArgumentCaptor.forClass(TicketTypeConfigEntity.class);
        when(ticketTypeConfigRepository.save(any())).thenReturn(mock(TicketTypeConfigEntity.class));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));

        TicketTypeConfig actual = eventService.createTicketTypeConfig(eventId, ticketTypeConfig);

        assertNotNull(actual);
        verify(ticketTypeConfigRepository, times(1)).save(captor.capture());

        TicketTypeConfigEntity mock = captor.getValue();
        assertEquals(ticketTypeConfig.getCurrency(), mock.getCurrency());
        assertEquals(ticketTypeConfig.getDescription(), mock.getDescription());
        assertEquals(ticketTypeConfig.getName(), mock.getName());
        assertEquals(ticketTypeConfig.getPrice(), mock.getPrice().toPlainString());
        assertEquals(ticketTypeConfig.getAuthorizedAmount().toString(), String.valueOf(mock.getAuthorizedAmount()));
    }

    @Test
    public void removeTicketFeeConfig() {

        UUID eventId = UUID.randomUUID();
        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);
        when(eventEntityMock.getEventEndTime()).thenReturn(OffsetDateTime.MAX.minusYears(1L));
        when(eventEntityMock.getName()).thenReturn("Test Event");
        when(eventEntityMock.getVenueEntity()).thenReturn(venueEntityMock);

        TicketFeeConfigEntity ticketFeeConfigEntity = spy(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntity.getEventEntity()).thenReturn(eventEntityMock);

        final UUID id = UUID.randomUUID();

        final ArgumentCaptor<TicketFeeConfigEntity> captor = ArgumentCaptor.forClass(TicketFeeConfigEntity.class);
        when(ticketFeeConfigRepository.findById(id)).thenReturn(Optional.of(ticketFeeConfigEntity));
        when(ticketFeeConfigRepository.save(any())).thenReturn(ticketFeeConfigEntity);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));

        TicketFeeConfig actual = eventService.removeTicketFeeConfig(eventId, id);

        assertNotNull(actual);
        verify(ticketFeeConfigRepository, times(1)).save(captor.capture());

        TicketFeeConfigEntity mock = captor.getValue();
        assertEquals(TicketFeeConfigEntity.Status.INACTIVE, mock.getStatus());
    }

    @Test
    public void removeTicketTypeConfig() {

        UUID eventId = UUID.randomUUID();
        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);
        when(eventEntityMock.getEventEndTime()).thenReturn(OffsetDateTime.MAX.minusYears(1L));
        when(eventEntityMock.getName()).thenReturn("Test Event");
        when(eventEntityMock.getVenueEntity()).thenReturn(venueEntityMock);

        TicketTypeConfigEntity ticketTypeConfigEntity = spy(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntity.getEventEntity()).thenReturn(eventEntityMock);

        final UUID id = UUID.randomUUID();

        final ArgumentCaptor<TicketTypeConfigEntity> captor = ArgumentCaptor.forClass(TicketTypeConfigEntity.class);
        when(ticketTypeConfigRepository.findById(id)).thenReturn(Optional.of(ticketTypeConfigEntity));
        when(ticketTypeConfigRepository.save(any())).thenReturn(mock(TicketTypeConfigEntity.class));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));

        TicketTypeConfig actual = eventService.removeTicketTypeConfig(eventId, id);

        assertNotNull(actual);
        verify(ticketTypeConfigRepository, times(1)).save(captor.capture());

        TicketTypeConfigEntity mock = captor.getValue();
        assertEquals(TicketTypeConfigEntity.Status.INACTIVE, mock.getStatus());
    }

    @Test
    public void applyPromotionCode() {

        final UUID eventId = UUID.randomUUID();
        final String promoCode = "TEST1234";

        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);
        when(eventEntityMock.getName()).thenReturn("Test Event");
        when(eventEntityMock.getVenueEntity()).thenReturn(venueEntityMock);

        TicketFeeConfigEntity ticketFeeConfigMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.VENUE);
        when(ticketFeeConfigMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigMock.getAmount()).thenReturn(new BigDecimal("0.25"));

        Set<TicketFeeConfigEntity> ticketFeeConfigEntitySet = new HashSet<>();
        ticketFeeConfigEntitySet.add(ticketFeeConfigMock);
        when(eventEntityMock.getTicketFeeConfig()).thenReturn(ticketFeeConfigEntitySet);

        TicketTypeConfigEntity ticketTypeConfigEntity = spy(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntity.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntity.getPrice()).thenReturn(BigDecimal.TEN);
        when(ticketTypeConfigEntity.getName()).thenReturn("Promo Tier");
        when(ticketTypeConfigEntity.getStatus()).thenReturn(TicketTypeConfigEntity.Status.ACTIVE);
        when(ticketTypeConfigEntity.getType()).thenReturn(TicketTypeConfigEntity.Type.PROMO);

        Set<TicketTypeConfigEntity> ticketTypeConfigEntitySet = new HashSet<>();
        ticketTypeConfigEntitySet.add(ticketTypeConfigEntity);
        when(eventEntityMock.getTicketTypeConfigEntity()).thenReturn(ticketTypeConfigEntitySet);

        PromoCodeEntity promoCodeEntityMock = mock(PromoCodeEntity.class);
        when(promoCodeEntityMock.getRedemptions()).thenReturn(new HashSet<>());
        when(promoCodeEntityMock.getCode()).thenReturn(promoCode);
        when(promoCodeEntityMock.getId()).thenReturn(UUID.randomUUID());
        when(promoCodeEntityMock.getQuantity()).thenReturn(1);
        when(promoCodeEntityMock.getTicketTypeConfigEntity()).thenReturn(ticketTypeConfigEntity);

        CalculationServiceImpl.PriceCalculationInfo priceCalculationInfo = new CalculationServiceImpl.PriceCalculationInfo();
        priceCalculationInfo.ticketSubtotal = new BigDecimal("100.00");
        priceCalculationInfo.feeSubtotal = new BigDecimal("50.00");
        priceCalculationInfo.currencyCode = "USD";
        priceCalculationInfo.paymentFeeSubtotal = new BigDecimal("123.12");
        priceCalculationInfo.grandTotal = new BigDecimal("500.00");

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));
        when(promoCodeRepository.findByTicketTypeConfigEntity_EventEntity_IdAndCode(eventId, promoCode)).thenReturn(promoCodeEntityMock);
        when(calculationService.calculateFees(eq(1), any(), eq(ticketFeeConfigEntitySet), eq(true))).thenReturn(priceCalculationInfo);

        List<TicketTypeConfig> actual = eventService.applyPromotionCode(eventId, promoCode);
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals(TicketTypeConfig.TypeEnum.PROMO, actual.get(0).getType());

        verify(promoCodeRepository).findByTicketTypeConfigEntity_EventEntity_IdAndCode(eventId, promoCode);
    }

    @Test(expected = ResponseStatusException.class)
    public void applyPromotionCode_CodeUsedTooMany() {

        final UUID eventId = UUID.randomUUID();
        final String promoCode = "TEST1234";

        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);
        when(eventEntityMock.getName()).thenReturn("Test Event");
        when(eventEntityMock.getVenueEntity()).thenReturn(venueEntityMock);

        TicketFeeConfigEntity ticketFeeConfigMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.VENUE);
        when(ticketFeeConfigMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigMock.getAmount()).thenReturn(new BigDecimal("0.25"));

        Set<TicketFeeConfigEntity> ticketFeeConfigEntitySet = new HashSet<>();
        ticketFeeConfigEntitySet.add(ticketFeeConfigMock);
        when(eventEntityMock.getTicketFeeConfig()).thenReturn(ticketFeeConfigEntitySet);

        TicketTypeConfigEntity ticketTypeConfigEntity = spy(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntity.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntity.getPrice()).thenReturn(BigDecimal.TEN);
        when(ticketTypeConfigEntity.getName()).thenReturn("Promo Tier");
        when(ticketTypeConfigEntity.getStatus()).thenReturn(TicketTypeConfigEntity.Status.ACTIVE);
        when(ticketTypeConfigEntity.getType()).thenReturn(TicketTypeConfigEntity.Type.PROMO);

        Set<TicketTypeConfigEntity> ticketTypeConfigEntitySet = new HashSet<>();
        ticketTypeConfigEntitySet.add(ticketTypeConfigEntity);
        when(eventEntityMock.getTicketTypeConfigEntity()).thenReturn(ticketTypeConfigEntitySet);

        PromoCodeEntity promoCodeEntityMock = mock(PromoCodeEntity.class);
        when(promoCodeEntityMock.getRedemptions()).thenReturn(new HashSet<>());
        when(promoCodeEntityMock.getCode()).thenReturn(promoCode);
        when(promoCodeEntityMock.getId()).thenReturn(UUID.randomUUID());
        when(promoCodeEntityMock.getQuantity()).thenReturn(0);
        when(promoCodeEntityMock.getTicketTypeConfigEntity()).thenReturn(ticketTypeConfigEntity);

        CalculationServiceImpl.PriceCalculationInfo priceCalculationInfo = new CalculationServiceImpl.PriceCalculationInfo();
        priceCalculationInfo.ticketSubtotal = new BigDecimal("100.00");
        priceCalculationInfo.feeSubtotal = new BigDecimal("50.00");
        priceCalculationInfo.currencyCode = "USD";
        priceCalculationInfo.paymentFeeSubtotal = new BigDecimal("123.12");
        priceCalculationInfo.grandTotal = new BigDecimal("500.00");

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));
        when(promoCodeRepository.findByTicketTypeConfigEntity_EventEntity_IdAndCode(eventId, promoCode)).thenReturn(promoCodeEntityMock);
        when(calculationService.calculateFees(eq(1), any(), eq(ticketFeeConfigEntitySet), eq(true))).thenReturn(priceCalculationInfo);

        eventService.applyPromotionCode(eventId, promoCode);
        verify(promoCodeRepository).findByTicketTypeConfigEntity_EventEntity_IdAndCode(eventId, promoCode);
    }

    @Test(expected = ResponseStatusException.class)
    public void applyPromotionCode_CodeNotFound() {

        final UUID eventId = UUID.randomUUID();
        final String promoCode = "TEST1234";

        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);
        when(eventEntityMock.getName()).thenReturn("Test Event");
        when(eventEntityMock.getVenueEntity()).thenReturn(venueEntityMock);

        PromoCodeEntity promoCodeEntityMock = mock(PromoCodeEntity.class);
        when(promoCodeEntityMock.getRedemptions()).thenReturn(new HashSet<>());
        when(promoCodeEntityMock.getCode()).thenReturn(promoCode);
        when(promoCodeEntityMock.getId()).thenReturn(UUID.randomUUID());
        when(promoCodeEntityMock.getQuantity()).thenReturn(0);
        when(promoCodeEntityMock.getTicketTypeConfigEntity()).thenReturn(null);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));
        when(promoCodeRepository.findByTicketTypeConfigEntity_EventEntity_IdAndCode(eventId, promoCode)).thenReturn(promoCodeEntityMock);

        eventService.applyPromotionCode(eventId, promoCode);
        verify(promoCodeRepository).findByTicketTypeConfigEntity_EventEntity_IdAndCode(eventId, promoCode);
    }

    @Test
    public void createPromotionCode() {

        final UUID eventId = UUID.randomUUID();
        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);
        when(eventEntityMock.getName()).thenReturn("Test Event");
        when(eventEntityMock.getVenueEntity()).thenReturn(venueEntityMock);

        TicketTypeConfigEntity ticketTypeConfigEntity = spy(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntity.getPrice()).thenReturn(BigDecimal.TEN);
        when(ticketTypeConfigEntity.getName()).thenReturn("Promo Tier");
        when(ticketTypeConfigEntity.getStatus()).thenReturn(TicketTypeConfigEntity.Status.ACTIVE);
        when(ticketTypeConfigEntity.getType()).thenReturn(TicketTypeConfigEntity.Type.PROMO);
        when(ticketTypeConfigEntity.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntity.getAuthorizedAmount()).thenReturn(10);
        when(ticketTypeConfigEntity.getId()).thenReturn(UUID.randomUUID());

        final String promoCode = "TEST1234";
        final PromotionCodeCreateRequest promotionCodeCreateRequest = new PromotionCodeCreateRequest();
        promotionCodeCreateRequest.setCode(promoCode);
        promotionCodeCreateRequest.setQuantity(9);
        promotionCodeCreateRequest.setDescription("Test 1");
        promotionCodeCreateRequest.setName("Test");
        promotionCodeCreateRequest.setTicketTypeConfigId(ticketTypeConfigEntity.getId());

        PromoCodeEntity promoCodeEntityMock = mock(PromoCodeEntity.class);
        when(promoCodeEntityMock.getRedemptions()).thenReturn(new HashSet<>());
        when(promoCodeEntityMock.getCode()).thenReturn(promoCode);
        when(promoCodeEntityMock.getId()).thenReturn(UUID.randomUUID());
        when(promoCodeEntityMock.getQuantity()).thenReturn(9);
        when(promoCodeEntityMock.getTicketTypeConfigEntity()).thenReturn(ticketTypeConfigEntity);

        when(ticketTypeConfigRepository.findById(ticketTypeConfigEntity.getId())).thenReturn(Optional.of(ticketTypeConfigEntity));
        when(promoCodeRepository.findByTicketTypeConfigEntity_EventEntity_IdAndCode(eventId, promoCode)).thenReturn(null);
        when(promoCodeRepository.save(any())).thenReturn(promoCodeEntityMock);

        eventService.createPromotionCode(promotionCodeCreateRequest);
        verify(promoCodeRepository).findByTicketTypeConfigEntity_EventEntity_IdAndCode(eventId, promoCode);
        verify(promoCodeRepository).save(any());
    }

    @Test(expected = ResponseStatusException.class)
    public void createPromotionCode_DupCode() {

        final UUID eventId = UUID.randomUUID();
        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);
        when(eventEntityMock.getName()).thenReturn("Test Event");
        when(eventEntityMock.getVenueEntity()).thenReturn(venueEntityMock);

        TicketTypeConfigEntity ticketTypeConfigEntity = spy(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntity.getPrice()).thenReturn(BigDecimal.TEN);
        when(ticketTypeConfigEntity.getName()).thenReturn("Promo Tier");
        when(ticketTypeConfigEntity.getStatus()).thenReturn(TicketTypeConfigEntity.Status.ACTIVE);
        when(ticketTypeConfigEntity.getType()).thenReturn(TicketTypeConfigEntity.Type.PROMO);
        when(ticketTypeConfigEntity.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntity.getAuthorizedAmount()).thenReturn(10);
        when(ticketTypeConfigEntity.getId()).thenReturn(UUID.randomUUID());

        final String promoCode = "TEST1234";
        final PromotionCodeCreateRequest promotionCodeCreateRequest = new PromotionCodeCreateRequest();
        promotionCodeCreateRequest.setCode(promoCode);
        promotionCodeCreateRequest.setQuantity(9);
        promotionCodeCreateRequest.setDescription("Test 1");
        promotionCodeCreateRequest.setName("Test");
        promotionCodeCreateRequest.setTicketTypeConfigId(ticketTypeConfigEntity.getId());

        PromoCodeEntity promoCodeEntityMock = mock(PromoCodeEntity.class);
        when(promoCodeEntityMock.getRedemptions()).thenReturn(new HashSet<>());
        when(promoCodeEntityMock.getCode()).thenReturn(promoCode);
        when(promoCodeEntityMock.getId()).thenReturn(UUID.randomUUID());
        when(promoCodeEntityMock.getQuantity()).thenReturn(9);
        when(promoCodeEntityMock.getTicketTypeConfigEntity()).thenReturn(ticketTypeConfigEntity);

        when(ticketTypeConfigRepository.findById(ticketTypeConfigEntity.getId())).thenReturn(Optional.of(ticketTypeConfigEntity));
        when(promoCodeRepository.findByTicketTypeConfigEntity_EventEntity_IdAndCode(eventId, promoCode)).thenReturn(promoCodeEntityMock);
        when(promoCodeRepository.save(any())).thenReturn(promoCodeEntityMock);

        eventService.createPromotionCode(promotionCodeCreateRequest);
        verify(promoCodeRepository, times(0)).findByTicketTypeConfigEntity_EventEntity_IdAndCode(eventId, promoCode);
        verify(promoCodeRepository, times(0)).save(any());
    }
}