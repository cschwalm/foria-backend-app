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

        Mockito.when(ticketTypeConfigRepository.findById(eventId)).thenReturn(Optional.of(ticketTypeConfigEntityMock));

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
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
    public void calculateFeesTest() {

        BigDecimal subtotal = new BigDecimal("100.00");
        BigDecimal feeActual = new BigDecimal("12.50");
        BigDecimal stripeFeeActual = new BigDecimal("3.67");
        BigDecimal grandTotalActual = new BigDecimal("116.17");

        Set<TicketFeeConfigEntity> feeSet = new HashSet<>();

        TicketFeeConfigEntity ticketFeeConfigEntityFlatMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityFlatMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.FLAT);
        when(ticketFeeConfigEntityFlatMock.getName()).thenReturn("FLAT TEST");
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        feeSet.add(ticketFeeConfigEntityFlatMock);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
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
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        feeSet.add(ticketFeeConfigEntityFlatMock);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
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
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        feeSet.add(ticketFeeConfigEntityFlatMock);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
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
        when(ticketFeeConfigEntityFlatMock.getAmount()).thenReturn(BigDecimal.valueOf(1.50));
        when(ticketFeeConfigEntityFlatMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityFlatMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);
        feeSet.add(ticketFeeConfigEntityFlatMock);

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
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