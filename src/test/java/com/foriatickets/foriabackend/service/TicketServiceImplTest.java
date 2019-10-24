package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.config.BeanConfig;
import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.gateway.FCMGateway;
import com.foriatickets.foriabackend.gateway.StripeGateway;
import com.foriatickets.foriabackend.repositories.*;
import com.stripe.model.Refund;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.internal.util.Assert;
import org.openapitools.model.ActivationResult;
import org.openapitools.model.RedemptionResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static com.foriatickets.foriabackend.entities.TicketEntity.Status.ACTIVE;
import static com.foriatickets.foriabackend.entities.TicketEntity.Status.ISSUED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
public class TicketServiceImplTest {

    @Mock
    private CalculationService calculationService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketTypeConfigRepository ticketTypeConfigRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TransferRequestRepository transferRequestRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderFeeEntryRepository orderFeeEntryRepository;

    @Mock
    private OrderTicketEntryRepository orderTicketEntryRepository;

    @Mock
    private StripeGateway stripeGateway;

    @Mock
    private FCMGateway fcmGateway;

    @Mock
    private AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway;

    private TicketServiceImpl ticketService;

    private UserEntity authenticatedUser;

    @Before
    public void setUp() {

        ModelMapper modelMapper = new ModelMapper();
        for (PropertyMap map : BeanConfig.getModelMappers()) {
            //noinspection unchecked
            modelMapper.addMappings(map);
        }

        this.authenticatedUser = mock(UserEntity.class);
        when(authenticatedUser.getId()).thenReturn(UUID.randomUUID());

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("test");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userRepository.findByAuth0Id(eq("test"))).thenReturn(authenticatedUser);

        ticketService = new TicketServiceImpl(calculationService, modelMapper, eventRepository, orderRepository, userRepository, ticketTypeConfigRepository, ticketRepository, stripeGateway, orderFeeEntryRepository, orderTicketEntryRepository, transferRequestRepository, fcmGateway, awsSimpleEmailServiceGateway);
    }

    @Test
    public void activateTicket() {

        UUID ticketId = UUID.randomUUID();
        TicketEntity ticketEntityMock = mock(TicketEntity.class);

        when(ticketEntityMock.getSecret()).thenReturn("SECRET");
        when(ticketEntityMock.getStatus()).thenReturn(ISSUED);
        when(ticketEntityMock.getOwnerEntity()).thenReturn(authenticatedUser);
        when(ticketEntityMock.getId()).thenReturn(ticketId);
        when(ticketEntityMock.getPurchaserEntity()).thenReturn(authenticatedUser);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticketEntityMock));
        when(ticketRepository.save(any())).thenReturn(ticketEntityMock);

        ActivationResult actual = ticketService.activateTicket(ticketId);

        assertNotNull(actual);
        assertEquals("SECRET", actual.getTicketSecret());
        assertEquals(ticketId, actual.getTicket().getId());

        verify(ticketRepository, atLeastOnce()).save(ticketEntityMock);
    }

    @Test(expected = ResponseStatusException.class)
    public void activateTicket_BadStatus() {

        UUID ticketId = UUID.randomUUID();
        TicketEntity ticketEntityMock = mock(TicketEntity.class);

        when(ticketEntityMock.getSecret()).thenReturn("SECRET");
        when(ticketEntityMock.getStatus()).thenReturn(TicketEntity.Status.CANCELED);
        when(ticketEntityMock.getOwnerEntity()).thenReturn(authenticatedUser);
        when(ticketEntityMock.getId()).thenReturn(ticketId);
        when(ticketEntityMock.getPurchaserEntity()).thenReturn(authenticatedUser);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticketEntityMock));
        when(ticketRepository.save(any())).thenReturn(ticketEntityMock);

        ticketService.activateTicket(ticketId);
        verify(ticketRepository, times(0)).save(ticketEntityMock);
    }

    @Test
    public void refundOrder() {

        final UUID orderId = UUID.randomUUID();
        final BigDecimal refundAmount = BigDecimal.valueOf(123L);

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
        when(ticketEntity.getOwnerEntity()).thenReturn(purchaser);
        when(ticketEntity.getEventEntity()).thenReturn(eventEntity);

        TicketEntity ticketEntity2 = mock(TicketEntity.class);
        when(ticketEntity2.getId()).thenReturn(UUID.randomUUID());
        when(ticketEntity2.getStatus()).thenReturn(ISSUED);
        when(ticketEntity2.getIssuedDate()).thenReturn(OffsetDateTime.now());
        when(ticketEntity2.getSecret()).thenReturn("secret");
        when(ticketEntity2.getPurchaserEntity()).thenReturn(purchaser);
        when(ticketEntity2.getOwnerEntity()).thenReturn(owner);
        when(ticketEntity2.getEventEntity()).thenReturn(eventEntity);

        OrderEntity orderEntity = mock(OrderEntity.class);
        when(orderEntity.getId()).thenReturn(orderId);
        when(orderEntity.getCurrency()).thenReturn("USD");
        when(orderEntity.getTotal()).thenReturn(refundAmount);
        when(orderEntity.getOrderTimestamp()).thenReturn(OffsetDateTime.now());
        when(orderEntity.getChargeReferenceId()).thenReturn("fake_stripe_charge");
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

        doNothing().when(awsSimpleEmailServiceGateway).sendEmailFromTemplate(any(), any(), any());
        doNothing().when(fcmGateway).sendPushNotification(any(), any());
        when(stripeGateway.refundStripeCharge(any(), any())).thenReturn(mock(Refund.class));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderEntity));

        ticketService.refundOrder(orderId);

        verify(ticketRepository, times(2)).save(any());
        verify(orderRepository).save(any());
        verify(awsSimpleEmailServiceGateway, times(2)).sendEmailFromTemplate(any(), any(), any());
        verify(fcmGateway, times(2)).sendPushNotification(any(), any());
        verify(stripeGateway).refundStripeCharge(orderEntity.getChargeReferenceId(), orderEntity.getTotal());
    }

    @Test
    public void refundOrder_alreadyCanceled() {

        final UUID orderId = UUID.randomUUID();
        final BigDecimal refundAmount = BigDecimal.valueOf(123L);

        OrderEntity orderEntity = mock(OrderEntity.class);
        when(orderEntity.getId()).thenReturn(orderId);
        when(orderEntity.getCurrency()).thenReturn("USD");
        when(orderEntity.getTotal()).thenReturn(refundAmount);
        when(orderEntity.getOrderTimestamp()).thenReturn(OffsetDateTime.now());
        when(orderEntity.getChargeReferenceId()).thenReturn("fake_stripe_charge");
        when(orderEntity.getStatus()).thenReturn(OrderEntity.Status.CANCELED);

        doNothing().when(awsSimpleEmailServiceGateway).sendEmailFromTemplate(any(), any(), any());
        doNothing().when(fcmGateway).sendPushNotification(any(), any());
        when(stripeGateway.refundStripeCharge(any(), any())).thenReturn(mock(Refund.class));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderEntity));

        ticketService.refundOrder(orderId);

        verify(ticketRepository, times(0)).save(any());
        verify(orderRepository, times(0)).save(any());
        verify(awsSimpleEmailServiceGateway, times(0)).sendEmailFromTemplate(any(), any(), any());
        verify(fcmGateway, times(0)).sendPushNotification(any(), any());
        verify(stripeGateway, times(0)).refundStripeCharge(orderEntity.getChargeReferenceId(), orderEntity.getTotal());
    }

    @Test
    public void issueTicket() {

        UUID purchaserId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketConfigId = UUID.randomUUID();

        UserEntity userEntityMock = mock(UserEntity.class);
        EventEntity eventEntityMock = mock(EventEntity.class);
        TicketTypeConfigEntity ticketTypeConfigEntity = mock(TicketTypeConfigEntity.class);
        ArgumentCaptor<TicketEntity> argumentCaptor = ArgumentCaptor.forClass(TicketEntity.class);

        when(eventEntityMock.getStatus()).thenReturn(EventEntity.Status.LIVE);
        when(eventEntityMock.getVisibility()).thenReturn(EventEntity.Visibility.PUBLIC);
        when(eventEntityMock.getEventEndTime()).thenReturn(OffsetDateTime.MAX);

        Mockito.when(userRepository.findById(purchaserId)).thenReturn(Optional.of(userEntityMock));
        Mockito.when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));
        Mockito.when(ticketTypeConfigRepository.findById(ticketConfigId)).thenReturn(Optional.of(ticketTypeConfigEntity));
        Mockito.when(ticketRepository.save(any())).thenReturn(mock(TicketEntity.class));

        TicketEntity actual = ticketService.issueTicket(purchaserId, eventId, ticketConfigId);
        verify(ticketRepository).save(argumentCaptor.capture());

        Assert.notNull(actual);
        TicketEntity ticketEntity = argumentCaptor.getValue();
        assertEquals(userEntityMock, ticketEntity.getPurchaserEntity());
        assertEquals(userEntityMock, ticketEntity.getOwnerEntity());
        assertNotNull(ticketEntity.getSecret());
        assertEquals(ISSUED, ticketEntity.getStatus());
    }

    @Test
    public void obtainTicketsRemainingByType() {

        UUID eventId = UUID.randomUUID();
        UUID ticketTypeConfigId = UUID.randomUUID();

        int expected = 1;

        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);

        TicketTypeConfigEntity ticketTypeConfigEntityMock = mock(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntityMock.getAuthorizedAmount()).thenReturn(5);
        when(ticketTypeConfigEntityMock.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntityMock.getId()).thenReturn(ticketTypeConfigId);

        when(ticketRepository.countActiveTicketsIssuedByType(ticketTypeConfigId, eventId)).thenReturn(4);

        int actual = ticketService.obtainTicketsRemainingByType(ticketTypeConfigEntityMock);

        assertEquals(expected, actual);
        verify(ticketRepository).countActiveTicketsIssuedByType(ticketTypeConfigId, eventId);
    }

    @Test
    public void reactivateTicket() {

        UUID ticketId = UUID.randomUUID();
        TicketEntity ticketEntityMock = mock(TicketEntity.class);

        when(ticketEntityMock.getSecret()).thenReturn("SECRET");
        when(ticketEntityMock.getStatus()).thenReturn(TicketEntity.Status.ACTIVE);
        when(ticketEntityMock.getOwnerEntity()).thenReturn(authenticatedUser);
        when(ticketEntityMock.getId()).thenReturn(ticketId);
        when(ticketEntityMock.getPurchaserEntity()).thenReturn(authenticatedUser);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticketEntityMock));
        when(ticketRepository.save(any())).thenReturn(ticketEntityMock);

        ActivationResult actual = ticketService.reactivateTicket(ticketId);

        assertNotNull(actual);
        assertEquals(ticketId, actual.getTicket().getId());

        verify(ticketRepository, atLeastOnce()).save(ticketEntityMock);
        verify(ticketEntityMock).setSecret(anyString());
    }

    @Test
    public void redeemTicket() {

        final GoogleAuthenticator gAuth = new GoogleAuthenticator();
        final GoogleAuthenticatorKey googleAuthenticatorKey = gAuth.createCredentials();

        UUID venueId = UUID.randomUUID();
        EventEntity eventEntity = mock(EventEntity.class);
        VenueEntity venueEntity = mock(VenueEntity.class);

        when(eventEntity.getVenueEntity()).thenReturn(venueEntity);
        when(venueEntity.getId()).thenReturn(venueId);

        UUID ticketId = UUID.randomUUID();
        TicketEntity ticketEntityMock = mock(TicketEntity.class);

        Set<VenueAccessEntity> venueAccessEntitySet = new HashSet<>();
        VenueAccessEntity venueAccessEntity = mock(VenueAccessEntity.class);
        when(venueAccessEntity.getVenueEntity()).thenReturn(venueEntity);
        venueAccessEntitySet.add(venueAccessEntity);
        when(authenticatedUser.getVenueAccessEntities()).thenReturn(venueAccessEntitySet);

        when(ticketEntityMock.getSecret()).thenReturn(googleAuthenticatorKey.getKey());
        when(ticketEntityMock.getStatus()).thenReturn(TicketEntity.Status.ACTIVE);
        when(ticketEntityMock.getOwnerEntity()).thenReturn(authenticatedUser);
        when(ticketEntityMock.getId()).thenReturn(ticketId);
        when(ticketEntityMock.getPurchaserEntity()).thenReturn(authenticatedUser);
        when(ticketEntityMock.getEventEntity()).thenReturn(eventEntity);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticketEntityMock));
        when(ticketRepository.save(any())).thenReturn(ticketEntityMock);

        RedemptionResult actual = ticketService.redeemTicket(ticketId, String.valueOf(gAuth.getTotpPassword(googleAuthenticatorKey.getKey())));
        assertEquals(RedemptionResult.StatusEnum.ALLOW, actual.getStatus());
        assertNotNull(actual.getTicket());

        verify(ticketRepository, atLeastOnce()).save(ticketEntityMock);
    }

    @Test
    public void redeemTicket_BadOtp() {

        UUID venueId = UUID.randomUUID();
        EventEntity eventEntity = mock(EventEntity.class);
        VenueEntity venueEntity = mock(VenueEntity.class);

        when(eventEntity.getVenueEntity()).thenReturn(venueEntity);
        when(venueEntity.getId()).thenReturn(venueId);

        Set<VenueAccessEntity> venueAccessEntitySet = new HashSet<>();
        VenueAccessEntity venueAccessEntity = mock(VenueAccessEntity.class);
        when(venueAccessEntity.getVenueEntity()).thenReturn(venueEntity);
        venueAccessEntitySet.add(venueAccessEntity);
        when(authenticatedUser.getVenueAccessEntities()).thenReturn(venueAccessEntitySet);


        final GoogleAuthenticator gAuth = new GoogleAuthenticator();
        final GoogleAuthenticatorKey googleAuthenticatorKey = gAuth.createCredentials();

        UUID ticketId = UUID.randomUUID();
        TicketEntity ticketEntityMock = mock(TicketEntity.class);

        when(ticketEntityMock.getSecret()).thenReturn(googleAuthenticatorKey.getKey());
        when(ticketEntityMock.getStatus()).thenReturn(TicketEntity.Status.ACTIVE);
        when(ticketEntityMock.getOwnerEntity()).thenReturn(authenticatedUser);
        when(ticketEntityMock.getId()).thenReturn(ticketId);
        when(ticketEntityMock.getPurchaserEntity()).thenReturn(authenticatedUser);
        when(ticketEntityMock.getEventEntity()).thenReturn(eventEntity);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticketEntityMock));
        when(ticketRepository.save(any())).thenReturn(ticketEntityMock);

        RedemptionResult actual = ticketService.redeemTicket(ticketId, "000000");
        assertEquals(RedemptionResult.StatusEnum.DENY, actual.getStatus());
        assertNotNull(actual.getTicket());

        verify(ticketRepository, times(0)).save(ticketEntityMock);
    }

    @Test(expected = ResponseStatusException.class)
    public void redeemTicket_BadAccess() {

        UUID venueId = UUID.randomUUID();
        EventEntity eventEntity = mock(EventEntity.class);
        VenueEntity venueEntity = mock(VenueEntity.class);

        when(eventEntity.getVenueEntity()).thenReturn(venueEntity);
        when(venueEntity.getId()).thenReturn(venueId);

        Set<VenueAccessEntity> venueAccessEntitySet = new HashSet<>();
        when(authenticatedUser.getVenueAccessEntities()).thenReturn(venueAccessEntitySet);

        final GoogleAuthenticator gAuth = new GoogleAuthenticator();
        final GoogleAuthenticatorKey googleAuthenticatorKey = gAuth.createCredentials();

        UUID ticketId = UUID.randomUUID();
        TicketEntity ticketEntityMock = mock(TicketEntity.class);

        when(ticketEntityMock.getSecret()).thenReturn(googleAuthenticatorKey.getKey());
        when(ticketEntityMock.getStatus()).thenReturn(TicketEntity.Status.ACTIVE);
        when(ticketEntityMock.getOwnerEntity()).thenReturn(authenticatedUser);
        when(ticketEntityMock.getId()).thenReturn(ticketId);
        when(ticketEntityMock.getPurchaserEntity()).thenReturn(authenticatedUser);
        when(ticketEntityMock.getEventEntity()).thenReturn(eventEntity);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticketEntityMock));
        when(ticketRepository.save(any())).thenReturn(ticketEntityMock);

        RedemptionResult actual = ticketService.redeemTicket(ticketId, "000000");
        assertEquals(RedemptionResult.StatusEnum.DENY, actual.getStatus());
        assertNotNull(actual.getTicket());

        verify(ticketRepository, times(0)).save(ticketEntityMock);
    }

    @Test
    public void checkAndConfirmPendingTicketTransfers() {

        UserEntity userEntityMock = mock(UserEntity.class);
        when(userEntityMock.getId()).thenReturn(UUID.randomUUID());
        when(userEntityMock.getEmail()).thenReturn("test@test.com");
        when(userEntityMock.getFirstName()).thenReturn("Test");

        EventEntity eventEntity = mock(EventEntity.class);
        when(eventEntity.getEventStartTime()).thenReturn(OffsetDateTime.now());
        when(eventEntity.getId()).thenReturn(UUID.randomUUID());

        TicketEntity ticketEntityMock = mock(TicketEntity.class);
        when(ticketEntityMock.getId()).thenReturn(UUID.randomUUID());
        when(ticketEntityMock.getEventEntity()).thenReturn(mock(EventEntity.class));
        when(ticketEntityMock.getOwnerEntity()).thenReturn(userEntityMock);
        when(ticketEntityMock.getEventEntity()).thenReturn(eventEntity);
        when(ticketRepository.save(ticketEntityMock)).thenReturn(ticketEntityMock);

        TransferRequestEntity transferRequestEntityMock = mock(TransferRequestEntity.class);
        when(transferRequestEntityMock.getTicket()).thenReturn(ticketEntityMock);
        List<TransferRequestEntity> list = new ArrayList<>();
        list.add(transferRequestEntityMock);

        when(transferRequestRepository.findAllByReceiverEmailAndStatus(anyString(), eq(TransferRequestEntity.Status.PENDING))).thenReturn(list);
        when(transferRequestRepository.saveAll(list)).thenReturn(list);

        ticketService.checkAndConfirmPendingTicketTransfers(userEntityMock);

        verify(transferRequestRepository).saveAll(list);
        verify(transferRequestEntityMock).setStatus(eq(TransferRequestEntity.Status.COMPLETED));
        verify(transferRequestEntityMock).setReceiver(eq(userEntityMock));
        verify(transferRequestEntityMock).setCompletedDate(any());

        verify(ticketEntityMock).setStatus(eq(ISSUED));
        verify(ticketEntityMock).setSecret(any());
        verify(ticketEntityMock).setOwnerEntity(userEntityMock);
    }
}