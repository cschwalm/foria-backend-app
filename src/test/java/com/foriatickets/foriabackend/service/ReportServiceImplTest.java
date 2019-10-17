package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.gateway.StripeGateway;
import com.foriatickets.foriabackend.gateway.StripeGatewayImpl;
import com.foriatickets.foriabackend.repositories.OrderRepository;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Payout;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
public class ReportServiceImplTest {

    @Mock
    private AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StripeGateway stripeGateway;

    @Mock
    private CalculationService calculationService;

    private ReportService reportService;

    private List<OrderEntity> orders;

    @Before
    public void setUp() {

        mockOrderInfo();
        reportService = new ReportServiceImpl(awsSimpleEmailServiceGateway, orderRepository, stripeGateway, calculationService);
    }

    @Test
    public void generateAndSendDailyTicketPurchaseReport() {

        when(orderRepository.findOrderEntitiesByOrderTimestampAfterAndOrderTimestampBeforeOrderByOrderTimestampAsc(any(), any())).thenReturn(orders);
        doNothing().when(awsSimpleEmailServiceGateway).sendInternalReport(any(), any(), any());

        reportService.generateAndSendDailyTicketPurchaseReport();

        verify(orderRepository).findOrderEntitiesByOrderTimestampAfterAndOrderTimestampBeforeOrderByOrderTimestampAsc(any(), any());
        verify(awsSimpleEmailServiceGateway).sendInternalReport(any(), any(), any());
    }

    @Test
    public void generateAndSendRollingTicketPurchaseReport() {

        when(orderRepository.findOrderEntitiesByOrderTimestampAfterAndOrderTimestampBeforeOrderByOrderTimestampAsc(any(), any())).thenReturn(orders);
        doNothing().when(awsSimpleEmailServiceGateway).sendInternalReport(any(), any(), any());

        reportService.generateAndSendRollingTicketPurchaseReport();

        verify(orderRepository).findOrderEntitiesByOrderTimestampAfterAndOrderTimestampBeforeOrderByOrderTimestampAsc(any(), any());
        verify(awsSimpleEmailServiceGateway).sendInternalReport(any(), any(), any());
    }

    @Test
    public void generateAndSendDailyTicketPurchaseReport_noOrders() {

        when(orderRepository.findOrderEntitiesByOrderTimestampAfterAndOrderTimestampBeforeOrderByOrderTimestampAsc(any(), any())).thenReturn(new ArrayList<>());
        doNothing().when(awsSimpleEmailServiceGateway).sendInternalReport(any(), any(), any());

        reportService.generateAndSendDailyTicketPurchaseReport();

        verify(orderRepository).findOrderEntitiesByOrderTimestampAfterAndOrderTimestampBeforeOrderByOrderTimestampAsc(any(), any());
        verify(awsSimpleEmailServiceGateway).sendInternalReport(any(), any(), any());
    }

    @Test
    public void generateAndSendWeeklySettlementReport() {

        BalanceTransaction balanceTransactionMock = mock(BalanceTransaction.class);
        when(balanceTransactionMock.getSource()).thenReturn("ch_12345");
        when(balanceTransactionMock.getNet()).thenReturn(10L);
        when(balanceTransactionMock.getCurrency()).thenReturn("usd");
        when(balanceTransactionMock.getType()).thenReturn("charge");
        List<BalanceTransaction> transactions = new ArrayList<>();
        transactions.add(balanceTransactionMock);

        Payout payoutMock = mock(Payout.class);
        when(payoutMock.getCurrency()).thenReturn("usd");
        when(payoutMock.getAmount()).thenReturn(10L);
        when(payoutMock.getAutomatic()).thenReturn(true);
        when(payoutMock.getType()).thenReturn("charge");

        StripeGatewayImpl.SettlementInfo settlementInfo = mock(StripeGatewayImpl.SettlementInfo.class);
        when(settlementInfo.getBalanceTransactions()).thenReturn(transactions);
        when(settlementInfo.getStripePayout()).thenReturn(payoutMock);

        CalculationServiceImpl.PriceCalculationInfo priceCalculationInfo = new CalculationServiceImpl.PriceCalculationInfo();
        priceCalculationInfo.grandTotal = BigDecimal.valueOf(10L);
        priceCalculationInfo.feeSubtotal = BigDecimal.valueOf(5L);
        priceCalculationInfo.venueFeeSubtotal = BigDecimal.valueOf(2L);
        priceCalculationInfo.issuerFeeSubtotal = BigDecimal.valueOf(3L);
        priceCalculationInfo.paymentFeeSubtotal = BigDecimal.ZERO;
        priceCalculationInfo.ticketSubtotal = BigDecimal.valueOf(5L);
        priceCalculationInfo.currencyCode = "USD";

        when(calculationService.calculateFees(anyInt(), any(), any())).thenReturn(priceCalculationInfo);

        when(stripeGateway.getSettlementInfo()).thenReturn(settlementInfo);
        when(orderRepository.findByChargeReferenceId(any())).thenReturn(orders.get(0));
        doNothing().when(awsSimpleEmailServiceGateway).sendInternalReport(any(), any(), any());

        reportService.generateAndSendWeeklySettlementReport();

        verify(orderRepository).findByChargeReferenceId(any());
        verify(awsSimpleEmailServiceGateway).sendInternalReport(any(), any(), any());
    }

    private void mockOrderInfo() {

        UserEntity userEntity = mock(UserEntity.class);
        EventEntity eventEntityMock = mock(EventEntity.class);
        VenueEntity venueEntity = mock(VenueEntity.class);
        when(userEntity.getId()).thenReturn(UUID.randomUUID());
        when(userEntity.getEmail()).thenReturn("test@test.com");

        when(venueEntity.getId()).thenReturn(UUID.randomUUID());
        when(eventEntityMock.getVenueEntity()).thenReturn(venueEntity);

        UUID ticketId = UUID.randomUUID();
        TicketEntity ticketEntityMock = mock(TicketEntity.class);

        when(ticketEntityMock.getSecret()).thenReturn("SECRET");
        when(ticketEntityMock.getStatus()).thenReturn(TicketEntity.Status.CANCELED);
        when(ticketEntityMock.getOwnerEntity()).thenReturn(userEntity);
        when(ticketEntityMock.getId()).thenReturn(ticketId);
        when(ticketEntityMock.getPurchaserEntity()).thenReturn(userEntity);
        when(ticketEntityMock.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketEntityMock.getIssuedDate()).thenReturn(OffsetDateTime.now());
        when(ticketEntityMock.getStatus()).thenReturn(TicketEntity.Status.ACTIVE);

        UUID eventId = UUID.randomUUID();

        when(eventEntityMock.getId()).thenReturn(eventId);
        when(eventEntityMock.getName()).thenReturn("Test Event Name");

        TicketTypeConfigEntity ticketTypeConfigEntityMock = mock(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntityMock.getAuthorizedAmount()).thenReturn(5);
        when(ticketTypeConfigEntityMock.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntityMock.getId()).thenReturn(eventId);
        when(ticketTypeConfigEntityMock.getPrice()).thenReturn(BigDecimal.valueOf(123L));
        when(ticketTypeConfigEntityMock.getCurrency()).thenReturn("USD");
        when(ticketEntityMock.getTicketTypeConfigEntity()).thenReturn(ticketTypeConfigEntityMock);
        when(ticketTypeConfigEntityMock.getName()).thenReturn("Test Event - Special Char 丏");

        TicketFeeConfigEntity ticketFeeConfigEntityPercentMock = mock(TicketFeeConfigEntity.class);
        when(ticketFeeConfigEntityPercentMock.getMethod()).thenReturn(TicketFeeConfigEntity.FeeMethod.PERCENT);
        when(ticketFeeConfigEntityPercentMock.getName()).thenReturn("PERCENT TEST");
        when(ticketFeeConfigEntityPercentMock.getAmount()).thenReturn(BigDecimal.valueOf(0.11));
        when(ticketFeeConfigEntityPercentMock.getCurrency()).thenReturn("USD");
        when(ticketFeeConfigEntityPercentMock.getType()).thenReturn(TicketFeeConfigEntity.FeeType.ISSUER);

        Set<TicketFeeConfigEntity> ticketFeeConfigSet = new HashSet<>();
        ticketFeeConfigSet.add(ticketFeeConfigEntityPercentMock);

        when(eventEntityMock.getTicketFeeConfig()).thenReturn(ticketFeeConfigSet);

        OrderEntity orderEntity1 = mock(OrderEntity.class);

        Set<OrderFeeEntryEntity> fees = new HashSet<>();
        OrderFeeEntryEntity orderFeeEntryEntity = mock(OrderFeeEntryEntity.class);
        when(orderFeeEntryEntity.getOrderEntity()).thenReturn(orderEntity1);
        when(orderFeeEntryEntity.getId()).thenReturn(UUID.randomUUID());
        when(orderFeeEntryEntity.getTicketFeeConfigEntity()).thenReturn(ticketFeeConfigEntityPercentMock);
        fees.add(orderFeeEntryEntity);

        Set<OrderTicketEntryEntity> orderTicketEntryEntities = new HashSet<>();
        OrderTicketEntryEntity orderTicketEntryEntity = mock(OrderTicketEntryEntity.class);
        when(orderTicketEntryEntity.getTicketEntity()).thenReturn(ticketEntityMock);
        when(orderTicketEntryEntity.getId()).thenReturn(UUID.randomUUID());
        when(orderTicketEntryEntity.getOrderEntity()).thenReturn(orderEntity1);
        orderTicketEntryEntities.add(orderTicketEntryEntity);

        when(orderEntity1.getId()).thenReturn(UUID.randomUUID());
        when(orderEntity1.getChargeReferenceId()).thenReturn("charge_123456");
        when(orderEntity1.getCurrency()).thenReturn("USD");
        when(orderEntity1.getTotal()).thenReturn(BigDecimal.TEN);
        when(orderEntity1.getOrderTimestamp()).thenReturn(OffsetDateTime.now());
        when(orderEntity1.getFees()).thenReturn(fees);
        when(orderEntity1.getPurchaser()).thenReturn(userEntity);
        when(orderEntity1.getTickets()).thenReturn(orderTicketEntryEntities);

        orders = new ArrayList<>();
        orders.add(orderEntity1);
    }
}