package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGatewayImpl;
import com.foriatickets.foriabackend.repositories.OrderRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class ReportServiceImplTest {

    private AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway = new AWSSimpleEmailServiceGatewayImpl();

    @Mock
    private OrderRepository orderRepository;

    private ReportService reportService;

    private List<OrderEntity> orders;

    @Before
    public void setUp() {
        mockOrderInfo();
        reportService = new ReportServiceImpl(awsSimpleEmailServiceGateway, orderRepository);
    }

    @Test
    public void generateAndSendDailyTicketPurchaseReport() {

        when(orderRepository.findOrderEntitiesByOrderTimestampAfterAndOrderTimestampBefore(any(), any())).thenReturn(orders);
        reportService.generateAndSendDailyTicketPurchaseReport();
    }

    @Test
    public void generateAndSendWeeklySettlementReport() {
    }

    private void mockOrderInfo() {

        UserEntity userEntity = mock(UserEntity.class);
        EventEntity eventEntityMock = mock(EventEntity.class);
        VenueEntity venueEntity = mock(VenueEntity.class);
        when(userEntity.getId()).thenReturn(UUID.randomUUID());

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

        UUID eventId = UUID.randomUUID();

        when(eventEntityMock.getId()).thenReturn(eventId);

        TicketTypeConfigEntity ticketTypeConfigEntityMock = mock(TicketTypeConfigEntity.class);
        when(ticketTypeConfigEntityMock.getAuthorizedAmount()).thenReturn(5);
        when(ticketTypeConfigEntityMock.getEventEntity()).thenReturn(eventEntityMock);
        when(ticketTypeConfigEntityMock.getId()).thenReturn(eventId);
        when(ticketTypeConfigEntityMock.getPrice()).thenReturn(BigDecimal.valueOf(123L));
        when(ticketTypeConfigEntityMock.getCurrency()).thenReturn("USD");
        when(ticketEntityMock.getTicketTypeConfigEntity()).thenReturn(ticketTypeConfigEntityMock);
        when(ticketTypeConfigEntityMock.getName()).thenReturn("Test Event");

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