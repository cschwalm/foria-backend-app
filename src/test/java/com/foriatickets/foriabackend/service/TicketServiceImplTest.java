package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.config.BeanConfig;
import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.StripeGateway;
import com.foriatickets.foriabackend.repositories.*;
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
import org.openapitools.api.TicketApi;
import org.openapitools.model.ActivationResult;
import org.openapitools.model.RedemptionResult;
import org.openapitools.model.Ticket;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
public class TicketServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketTypeConfigRepository ticketTypeConfigRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderFeeEntryRepository orderFeeEntryRepository;

    @Mock
    private OrderTicketEntryRepository orderTicketEntryRepository;

    @Mock
    private StripeGateway stripeGateway;

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

        ticketService = new TicketServiceImpl(modelMapper, eventRepository, orderRepository, userRepository, ticketTypeConfigRepository, ticketRepository, stripeGateway, orderFeeEntryRepository, orderTicketEntryRepository);
    }

    @Test
    public void activateTicket() {

        UUID ticketId = UUID.randomUUID();
        TicketEntity ticketEntityMock = mock(TicketEntity.class);

        when(ticketEntityMock.getSecret()).thenReturn("SECRET");
        when(ticketEntityMock.getStatus()).thenReturn(TicketEntity.Status.ISSUED);
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
    public void issueTicket() {

        UUID purchaserId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketConfigId = UUID.randomUUID();

        UserEntity userEntityMock = mock(UserEntity.class);
        EventEntity eventEntityMock = mock(EventEntity.class);
        TicketTypeConfigEntity ticketTypeConfigEntity = mock(TicketTypeConfigEntity.class);
        ArgumentCaptor<TicketEntity> argumentCaptor = ArgumentCaptor.forClass(TicketEntity.class);

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
        assertEquals(TicketEntity.Status.ISSUED, ticketEntity.getStatus());
    }

    @Test
    public void calculateFeesTest() {

        BigDecimal subtotal = new BigDecimal("100.00");
        BigDecimal feeActual = new BigDecimal("12.50");
        BigDecimal stripeFeeActual = new BigDecimal("3.67");
        BigDecimal grandTotalActual = new BigDecimal("116.17");

        Set<TicketFeeConfigEntity> flatFeeSet = new HashSet<>();
        Set<TicketFeeConfigEntity> percentFeeSet = new HashSet<>();

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        flatFeeSet.add(ticketFeeConfigEntityFlatMock);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
        when(ticketFeeConfigEntityPercentMock.getAmount()).thenReturn(BigDecimal.valueOf(0.11));
        when(ticketFeeConfigEntityPercentMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityPercentMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        percentFeeSet.add(ticketFeeConfigEntityPercentMock);

        TicketServiceImpl.PriceCalculationInfo actual = ticketService.calculateFees(subtotal, percentFeeSet, flatFeeSet);

        assertEquals(subtotal, actual.ticketSubtotal);
        assertEquals(feeActual, actual.feeSubtotal);
        assertEquals(stripeFeeActual, actual.paymentFeeSubtotal);
        assertEquals(grandTotalActual, actual.grandTotal);
    }

    @Test
    public void calculateFeesTest_Rounding() {

        BigDecimal subtotal = new BigDecimal("100.33");
        BigDecimal feeActual = new BigDecimal("12.54");
        BigDecimal stripeFeeActual = new BigDecimal("3.68");
        BigDecimal grandTotalActual = new BigDecimal("116.55");

        Set<TicketFeeConfigEntity> flatFeeSet = new HashSet<>();
        Set<TicketFeeConfigEntity> percentFeeSet = new HashSet<>();

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        flatFeeSet.add(ticketFeeConfigEntityFlatMock);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
        when(ticketFeeConfigEntityPercentMock.getAmount()).thenReturn(BigDecimal.valueOf(0.11));
        when(ticketFeeConfigEntityPercentMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityPercentMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        percentFeeSet.add(ticketFeeConfigEntityPercentMock);

        TicketServiceImpl.PriceCalculationInfo actual = ticketService.calculateFees(subtotal, percentFeeSet, flatFeeSet);

        assertEquals(subtotal, actual.ticketSubtotal);
        assertEquals(feeActual, actual.feeSubtotal);
        assertEquals(stripeFeeActual, actual.paymentFeeSubtotal);
        assertEquals(grandTotalActual, actual.grandTotal);
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
    public void redeemTicket() {

        final GoogleAuthenticator gAuth = new GoogleAuthenticator();
        final GoogleAuthenticatorKey googleAuthenticatorKey = gAuth.createCredentials();

        UUID ticketId = UUID.randomUUID();
        TicketEntity ticketEntityMock = mock(TicketEntity.class);

        when(ticketEntityMock.getSecret()).thenReturn(googleAuthenticatorKey.getKey());
        when(ticketEntityMock.getStatus()).thenReturn(TicketEntity.Status.ACTIVE);
        when(ticketEntityMock.getOwnerEntity()).thenReturn(authenticatedUser);
        when(ticketEntityMock.getId()).thenReturn(ticketId);
        when(ticketEntityMock.getPurchaserEntity()).thenReturn(authenticatedUser);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticketEntityMock));
        when(ticketRepository.save(any())).thenReturn(ticketEntityMock);

        RedemptionResult actual = ticketService.redeemTicket(ticketId, String.valueOf(gAuth.getTotpPassword(googleAuthenticatorKey.getKey())));
        assertEquals(RedemptionResult.StatusEnum.ALLOW, actual.getStatus());
        assertNotNull(actual.getTicket());

        verify(ticketRepository, atLeastOnce()).save(ticketEntityMock);
    }

    @Test
    public void redeemTicket_BadOtp() {

        final GoogleAuthenticator gAuth = new GoogleAuthenticator();
        final GoogleAuthenticatorKey googleAuthenticatorKey = gAuth.createCredentials();

        UUID ticketId = UUID.randomUUID();
        TicketEntity ticketEntityMock = mock(TicketEntity.class);

        when(ticketEntityMock.getSecret()).thenReturn(googleAuthenticatorKey.getKey());
        when(ticketEntityMock.getStatus()).thenReturn(TicketEntity.Status.ACTIVE);
        when(ticketEntityMock.getOwnerEntity()).thenReturn(authenticatedUser);
        when(ticketEntityMock.getId()).thenReturn(ticketId);
        when(ticketEntityMock.getPurchaserEntity()).thenReturn(authenticatedUser);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticketEntityMock));
        when(ticketRepository.save(any())).thenReturn(ticketEntityMock);

        RedemptionResult actual = ticketService.redeemTicket(ticketId, "000000");
        assertEquals(RedemptionResult.StatusEnum.DENY, actual.getStatus());
        assertNotNull(actual.getTicket());

        verify(ticketRepository, times(0)).save(ticketEntityMock);
    }
}