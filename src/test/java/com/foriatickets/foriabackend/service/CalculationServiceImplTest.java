package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.EventEntity;
import com.foriatickets.foriabackend.entities.TicketFeeConfigEntity;
import com.foriatickets.foriabackend.entities.TicketTypeConfigEntity;
import com.foriatickets.foriabackend.repositories.EventRepository;
import com.foriatickets.foriabackend.repositories.TicketTypeConfigRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.modelmapper.internal.util.Assert;
import org.openapitools.model.OrderTotal;
import org.openapitools.model.TicketLineItem;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class CalculationServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketTypeConfigRepository ticketTypeConfigRepository;

    private CalculationService calculationService;

    @Before
    public void setUp() {
        calculationService = new CalculationServiceImpl(eventRepository, ticketTypeConfigRepository);
    }

    @Test
    public void calculateOrderTotalTest() {

        UUID eventId = UUID.randomUUID();

        BigDecimal subtotal = new BigDecimal("100.00");
        BigDecimal grandTotalActual = new BigDecimal("116.17");

        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);

        Mockito.when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));

        TicketTypeConfigEntity ticketTypeConfigEntityMock = mock(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntityMock.getAuthorizedAmount()).thenReturn(5);
        when(ticketTypeConfigEntityMock.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntityMock.getId()).thenReturn(eventId);
        when(ticketTypeConfigEntityMock.getPrice()).thenReturn(subtotal);
        when(ticketTypeConfigEntityMock.getCurrency()).thenReturn("USD");
        when(ticketTypeConfigEntityMock.getStatus()).thenReturn(TicketTypeConfigEntity.Status.ACTIVE);

        Mockito.when(ticketTypeConfigRepository.findById(eventId)).thenReturn(Optional.of(ticketTypeConfigEntityMock));

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);

        TicketFeeConfigEntity ticketFeeInactiveConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeInactiveConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeInactiveConfigEntityFlatMock.getName()).thenReturn("FLAT TEST");
        when(ticketFeeInactiveConfigEntityFlatMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.INACTIVE);
        when(ticketFeeInactiveConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(10000.00));
        when(ticketFeeInactiveConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeInactiveConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
        when(ticketFeeConfigEntityPercentMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityPercentMock.getAmount()).thenReturn(BigDecimal.valueOf(0.11));
        when(ticketFeeConfigEntityPercentMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityPercentMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);

        Set<TicketFeeConfigEntity> ticketFeeConfigSet = new HashSet<>();
        ticketFeeConfigSet.add(ticketFeeConfigEntityFlatMock);
        ticketFeeConfigSet.add(ticketFeeInactiveConfigEntityFlatMock);
        ticketFeeConfigSet.add(ticketFeeConfigEntityPercentMock);

        when(eventEntityMock.getTicketFeeConfig()).thenReturn(ticketFeeConfigSet);

        List<TicketLineItem> ticketLineItemList = new ArrayList<>();
        TicketLineItem ticketLineItem = new TicketLineItem();
        ticketLineItem.setTicketTypeId(ticketTypeConfigEntityMock.getId());
        ticketLineItem.setAmount(1);
        ticketLineItemList.add(ticketLineItem);

        OrderTotal actual = calculationService.calculateOrderTotal(eventEntityMock.getId(), ticketLineItemList);

        Assert.notNull(actual);
        assertEquals("USD", actual.getCurrency());
        assertEquals("10000", actual.getSubtotalCents());
        assertEquals("1617", actual.getFeesCents());
        assertEquals("11617", actual.getGrandTotalCents());

        assertEquals(subtotal.toPlainString(), actual.getSubtotal());
        assertEquals("16.17", actual.getFees());
        assertEquals(grandTotalActual.toPlainString(), actual.getGrandTotal());
    }

    @Test
    public void calculateOrderTotalTest_OnePaid_OneFree() {

        UUID eventId = UUID.randomUUID();

        BigDecimal subtotal = new BigDecimal("100.00");
        BigDecimal grandTotalActual = new BigDecimal("116.17");

        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);

        Mockito.when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));

        UUID paidEventTypeId = UUID.randomUUID();
        TicketTypeConfigEntity ticketTypeConfigEntityMock = mock(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntityMock.getAuthorizedAmount()).thenReturn(5);
        when(ticketTypeConfigEntityMock.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntityMock.getId()).thenReturn(paidEventTypeId);
        when(ticketTypeConfigEntityMock.getPrice()).thenReturn(subtotal);
        when(ticketTypeConfigEntityMock.getCurrency()).thenReturn("USD");
        when(ticketTypeConfigEntityMock.getStatus()).thenReturn(TicketTypeConfigEntity.Status.ACTIVE);

        UUID freeEventTypeId = UUID.randomUUID();
        TicketTypeConfigEntity ticketTypeConfigEntityFreeMock = mock(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntityFreeMock.getAuthorizedAmount()).thenReturn(5);
        when(ticketTypeConfigEntityFreeMock.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntityFreeMock.getId()).thenReturn(freeEventTypeId);
        when(ticketTypeConfigEntityFreeMock.getPrice()).thenReturn(BigDecimal.ZERO);
        when(ticketTypeConfigEntityFreeMock.getCurrency()).thenReturn("USD");
        when(ticketTypeConfigEntityFreeMock.getStatus()).thenReturn(TicketTypeConfigEntity.Status.ACTIVE);

        Mockito.when(ticketTypeConfigRepository.findById(paidEventTypeId)).thenReturn(Optional.of(ticketTypeConfigEntityMock));
        Mockito.when(ticketTypeConfigRepository.findById(freeEventTypeId)).thenReturn(Optional.of(ticketTypeConfigEntityFreeMock));

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.VENUE);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
        when(ticketFeeConfigEntityPercentMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityPercentMock.getAmount()).thenReturn(BigDecimal.valueOf(0.11));
        when(ticketFeeConfigEntityPercentMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityPercentMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);

        Set<TicketFeeConfigEntity> ticketFeeConfigSet = new HashSet<>();
        ticketFeeConfigSet.add(ticketFeeConfigEntityFlatMock);
        ticketFeeConfigSet.add(ticketFeeConfigEntityPercentMock);

        when(eventEntityMock.getTicketFeeConfig()).thenReturn(ticketFeeConfigSet);

        List<TicketLineItem> ticketLineItemList = new ArrayList<>();
        TicketLineItem ticketLineItem = new TicketLineItem();
        ticketLineItem.setTicketTypeId(ticketTypeConfigEntityMock.getId());
        ticketLineItem.setAmount(1);
        ticketLineItemList.add(ticketLineItem);

        TicketLineItem ticketLineItemFree = new TicketLineItem();
        ticketLineItemFree.setTicketTypeId(ticketTypeConfigEntityFreeMock.getId());
        ticketLineItemFree.setAmount(1);
        ticketLineItemList.add(ticketLineItemFree);

        OrderTotal actual = calculationService.calculateOrderTotal(eventEntityMock.getId(), ticketLineItemList);

        Assert.notNull(actual);
        assertEquals("USD", actual.getCurrency());
        assertEquals("10000", actual.getSubtotalCents());
        assertEquals("1617", actual.getFeesCents());
        assertEquals("11617", actual.getGrandTotalCents());

        assertEquals(subtotal.toPlainString(), actual.getSubtotal());
        assertEquals("16.17", actual.getFees());
        assertEquals(grandTotalActual.toPlainString(), actual.getGrandTotal());
    }

    @Test
    public void calculateOrderTotalTest_TwoTicketsSameTier() {

        UUID eventId = UUID.randomUUID();

        BigDecimal subtotal = new BigDecimal("100.00");
        BigDecimal subtotalActual = new BigDecimal("200.00");
        BigDecimal grandTotalActual = new BigDecimal("220.70");

        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);

        Mockito.when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));

        TicketTypeConfigEntity ticketTypeConfigEntityMock = mock(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntityMock.getAuthorizedAmount()).thenReturn(5);
        when(ticketTypeConfigEntityMock.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntityMock.getId()).thenReturn(eventId);
        when(ticketTypeConfigEntityMock.getPrice()).thenReturn(subtotal);
        when(ticketTypeConfigEntityMock.getCurrency()).thenReturn("USD");
        when(ticketTypeConfigEntityMock.getStatus()).thenReturn(TicketTypeConfigEntity.Status.ACTIVE);

        Mockito.when(ticketTypeConfigRepository.findById(eventId)).thenReturn(Optional.of(ticketTypeConfigEntityMock));

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("VENUE FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.VENUE);

        TicketFeeConfigEntity issuerTicketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(issuerTicketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(issuerTicketFeeConfigEntityFlatMock.getName()).thenReturn("ISSUER FLAT TEST");
        when(issuerTicketFeeConfigEntityFlatMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(issuerTicketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(0.50));
        when(issuerTicketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(issuerTicketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("ISSUER PERCENT TEST");
        when(ticketFeeConfigEntityPercentMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityPercentMock.getAmount()).thenReturn(BigDecimal.valueOf(0.05));
        when(ticketFeeConfigEntityPercentMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityPercentMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);

        Set<TicketFeeConfigEntity> ticketFeeConfigSet = new HashSet<>();
        ticketFeeConfigSet.add(ticketFeeConfigEntityFlatMock);
        ticketFeeConfigSet.add(issuerTicketFeeConfigEntityFlatMock);
        ticketFeeConfigSet.add(ticketFeeConfigEntityPercentMock);

        when(eventEntityMock.getTicketFeeConfig()).thenReturn(ticketFeeConfigSet);

        List<TicketLineItem> ticketLineItemList = new ArrayList<>();
        TicketLineItem ticketLineItem = new TicketLineItem();
        ticketLineItem.setTicketTypeId(ticketTypeConfigEntityMock.getId());
        ticketLineItem.setAmount(2);
        ticketLineItemList.add(ticketLineItem);

        OrderTotal actual = calculationService.calculateOrderTotal(eventEntityMock.getId(), ticketLineItemList);

        Assert.notNull(actual);
        assertEquals("USD", actual.getCurrency());
        assertEquals("20000", actual.getSubtotalCents());
        assertEquals("2070", actual.getFeesCents());
        assertEquals("22070", actual.getGrandTotalCents());

        assertEquals(subtotalActual.toPlainString(), actual.getSubtotal());
        assertEquals("20.70", actual.getFees());
        assertEquals(grandTotalActual.toPlainString(), actual.getGrandTotal());
    }

    @Test(expected = ResponseStatusException.class)
    public void calculateOrderTotalTest_TypeDeactivated() {

        UUID eventId = UUID.randomUUID();

        BigDecimal subtotal = new BigDecimal("100.00");

        EventEntity eventEntityMock = mock(EventEntity.class);
        when(eventEntityMock.getId()).thenReturn(eventId);

        Mockito.when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventEntityMock));

        TicketTypeConfigEntity ticketTypeConfigEntityMock = mock(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntityMock.getAuthorizedAmount()).thenReturn(5);
        when(ticketTypeConfigEntityMock.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntityMock.getId()).thenReturn(eventId);
        when(ticketTypeConfigEntityMock.getPrice()).thenReturn(subtotal);
        when(ticketTypeConfigEntityMock.getCurrency()).thenReturn("USD");
        when(ticketTypeConfigEntityMock.getStatus()).thenReturn(TicketTypeConfigEntity.Status.INACTIVE);

        Mockito.when(ticketTypeConfigRepository.findById(eventId)).thenReturn(Optional.of(ticketTypeConfigEntityMock));

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("VENUE FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.VENUE);

        TicketFeeConfigEntity issuerTicketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(issuerTicketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(issuerTicketFeeConfigEntityFlatMock.getName()).thenReturn("ISSUER FLAT TEST");
        when(issuerTicketFeeConfigEntityFlatMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(issuerTicketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(0.50));
        when(issuerTicketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(issuerTicketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("ISSUER PERCENT TEST");
        when(ticketFeeConfigEntityPercentMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityPercentMock.getAmount()).thenReturn(BigDecimal.valueOf(0.05));
        when(ticketFeeConfigEntityPercentMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityPercentMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);

        Set<TicketFeeConfigEntity> ticketFeeConfigSet = new HashSet<>();
        ticketFeeConfigSet.add(ticketFeeConfigEntityFlatMock);
        ticketFeeConfigSet.add(issuerTicketFeeConfigEntityFlatMock);
        ticketFeeConfigSet.add(ticketFeeConfigEntityPercentMock);

        when(eventEntityMock.getTicketFeeConfig()).thenReturn(ticketFeeConfigSet);

        List<TicketLineItem> ticketLineItemList = new ArrayList<>();
        TicketLineItem ticketLineItem = new TicketLineItem();
        ticketLineItem.setTicketTypeId(ticketTypeConfigEntityMock.getId());
        ticketLineItem.setAmount(2);
        ticketLineItemList.add(ticketLineItem);

        calculationService.calculateOrderTotal(eventEntityMock.getId(), ticketLineItemList);
    }

    @Test
    public void calculateFeesTest() {

        BigDecimal subtotal = new BigDecimal("100.00");
        BigDecimal feeActual = new BigDecimal("12.50");
        BigDecimal stripeFeeActual = new BigDecimal("3.67");
        BigDecimal grandTotalActual = new BigDecimal("116.17");

        Set<TicketFeeConfigEntity> feeSet = new HashSet<>();

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        feeSet.add(ticketFeeConfigEntityFlatMock);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
        when(ticketFeeConfigEntityPercentMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityPercentMock.getAmount()).thenReturn(BigDecimal.valueOf(0.11));
        when(ticketFeeConfigEntityPercentMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityPercentMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.VENUE);
        feeSet.add(ticketFeeConfigEntityPercentMock);

        CalculationServiceImpl.PriceCalculationInfo actual = calculationService.calculateFees(1, subtotal, feeSet);

        assertEquals(subtotal, actual.ticketSubtotal);
        assertEquals(feeActual, actual.feeSubtotal);
        assertEquals(stripeFeeActual, actual.paymentFeeSubtotal);
        assertEquals(grandTotalActual, actual.grandTotal);
        assertEquals(actual.feeSubtotal, actual.issuerFeeSubtotal.add(actual.venueFeeSubtotal));
    }

    @Test
    public void calculateFeesTest_Rounding() {

        BigDecimal subtotal = new BigDecimal("100.33");
        BigDecimal feeActual = new BigDecimal("12.54");
        BigDecimal stripeFeeActual = new BigDecimal("3.68");
        BigDecimal grandTotalActual = new BigDecimal("116.55");

        Set<TicketFeeConfigEntity> feeSet = new HashSet<>();

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        feeSet.add(ticketFeeConfigEntityFlatMock);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
        when(ticketFeeConfigEntityPercentMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityPercentMock.getAmount()).thenReturn(BigDecimal.valueOf(0.11));
        when(ticketFeeConfigEntityPercentMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityPercentMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        feeSet.add(ticketFeeConfigEntityPercentMock);

        CalculationServiceImpl.PriceCalculationInfo actual = calculationService.calculateFees(1, subtotal, feeSet);

        assertEquals(subtotal, actual.ticketSubtotal);
        assertEquals(feeActual, actual.feeSubtotal);
        assertEquals(stripeFeeActual, actual.paymentFeeSubtotal);
        assertEquals(grandTotalActual, actual.grandTotal);
        assertEquals(actual.feeSubtotal, actual.issuerFeeSubtotal.add(actual.venueFeeSubtotal));
    }

    @Test
    public void calculateFeesTest_Multiple() {

        BigDecimal subtotal = new BigDecimal("300.00");
        BigDecimal feeActual = new BigDecimal("37.50"); //33 + 4.5
        BigDecimal stripeFeeActual = new BigDecimal("10.39");
        BigDecimal grandTotalActual = new BigDecimal("347.89");

        Set<TicketFeeConfigEntity> feeSet = new HashSet<>();

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        feeSet.add(ticketFeeConfigEntityFlatMock);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
        when(ticketFeeConfigEntityPercentMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityPercentMock.getAmount()).thenReturn(BigDecimal.valueOf(0.11));
        when(ticketFeeConfigEntityPercentMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityPercentMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        feeSet.add(ticketFeeConfigEntityPercentMock);

        CalculationServiceImpl.PriceCalculationInfo actual = calculationService.calculateFees(3, subtotal, feeSet);

        assertEquals(subtotal, actual.ticketSubtotal);
        assertEquals(feeActual, actual.feeSubtotal);
        assertEquals(stripeFeeActual, actual.paymentFeeSubtotal);
        assertEquals(grandTotalActual, actual.grandTotal);
        assertEquals(actual.feeSubtotal, actual.issuerFeeSubtotal.add(actual.venueFeeSubtotal));
    }

    @Test
    public void calculateFeesTest_FreeTicket() {

        BigDecimal subtotal = new BigDecimal("0.00");
        BigDecimal feeActual = new BigDecimal("0.00");
        BigDecimal stripeFeeActual = new BigDecimal("0.00");
        BigDecimal grandTotalActual = new BigDecimal("0.00");

        Set<TicketFeeConfigEntity> feeSet = new HashSet<>();

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        feeSet.add(ticketFeeConfigEntityFlatMock);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
        when(ticketFeeConfigEntityPercentMock.getStatus()).thenReturn(TicketFeeConfigEntity.Status.ACTIVE);
        when(ticketFeeConfigEntityPercentMock.getAmount()).thenReturn(BigDecimal.valueOf(0.11));
        when(ticketFeeConfigEntityPercentMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityPercentMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        feeSet.add(ticketFeeConfigEntityPercentMock);

        CalculationServiceImpl.PriceCalculationInfo actual = calculationService.calculateFees(0, subtotal, feeSet);

        assertEquals(subtotal, actual.ticketSubtotal);
        assertEquals(feeActual, actual.feeSubtotal);
        assertEquals(stripeFeeActual, actual.paymentFeeSubtotal);
        assertEquals(grandTotalActual, actual.grandTotal);
        assertEquals(actual.feeSubtotal, actual.issuerFeeSubtotal.add(actual.venueFeeSubtotal));
    }

}