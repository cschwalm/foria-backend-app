package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.config.BeanConfig;
import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.StripeGateway;
import com.foriatickets.foriabackend.repositories.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.internal.util.Assert;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

    @Before
    public void setUp() {

        ModelMapper modelMapper = new ModelMapper();
        for (PropertyMap map : BeanConfig.getModelMappers()) {
            //noinspection unchecked
            modelMapper.addMappings(map);
        }

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("test");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userRepository.findByAuth0Id(eq("test"))).thenReturn(mock(UserEntity.class));

        ticketService = new TicketServiceImpl(modelMapper, eventRepository, orderRepository, userRepository, ticketTypeConfigRepository, ticketRepository, stripeGateway, orderFeeEntryRepository, orderTicketEntryRepository);
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
}