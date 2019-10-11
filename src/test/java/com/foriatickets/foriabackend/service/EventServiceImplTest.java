package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.config.BeanConfig;
import com.foriatickets.foriabackend.entities.EventEntity;
import com.foriatickets.foriabackend.entities.TicketFeeConfigEntity;
import com.foriatickets.foriabackend.entities.TicketTypeConfigEntity;
import com.foriatickets.foriabackend.entities.VenueEntity;
import com.foriatickets.foriabackend.repositories.EventRepository;
import com.foriatickets.foriabackend.repositories.TicketFeeConfigRepository;
import com.foriatickets.foriabackend.repositories.TicketTypeConfigRepository;
import com.foriatickets.foriabackend.repositories.VenueRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.internal.util.Assert;
import org.openapitools.model.Event;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private EventService eventService;

    private List<EventEntity> mockEventList;

    @Before
    public void setUp() {

        mockEventList = new ArrayList<>();

        VenueEntity venueEntityMock = mock(VenueEntity.class);
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
        mockEventList.add(mockEvent1);

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

        eventService = new EventServiceImpl(calculationService, eventRepository, ticketFeeConfigRepository, ticketTypeConfigRepository, venueRepository, modelMapper, ticketService);
    }

    @Test
    public void getAllActiveEvents() {

        when(eventRepository.findAllByOrderByEventStartTimeAsc()).thenReturn(mockEventList);
        List<Event> actual = eventService.getAllActiveEvents();

        Assert.notNull(actual);
        Assert.isTrue(mockEventList.size() == actual.size());
    }

    @Test
    public void getEvent() {

        final UUID eventId = mockEventList.get(0).getId();
        when(eventRepository.findById(eq(eventId))).thenReturn(Optional.of(mockEventList.get(0)));

        Event actual = eventService.getEvent(eventId);
        Assert.notNull(actual);
    }
}