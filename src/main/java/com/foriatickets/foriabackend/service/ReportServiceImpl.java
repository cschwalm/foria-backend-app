package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.gateway.StripeGateway;
import com.foriatickets.foriabackend.gateway.StripeGatewayImpl;
import com.foriatickets.foriabackend.repositories.OrderRepository;
import com.opencsv.CSVWriter;
import com.opencsv.bean.*;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Payout;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.CharArrayWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class ReportServiceImpl implements ReportService {

    static class CustomMappingStrategy<T> extends ColumnPositionMappingStrategy<T> {
        @Override
        public String[] generateHeader(T bean) throws CsvRequiredFieldEmptyException {

            super.setColumnMapping(new String[FieldUtils.getAllFields(bean.getClass()).length]);
            final int numColumns = findMaxFieldIndex();
            if (!isAnnotationDriven() || numColumns == -1) {
                return super.generateHeader(bean);
            }

            String[] header = new String[numColumns + 1];

            BeanField<T> beanField;
            for (int i = 0; i <= numColumns; i++) {
                beanField = findField(i);
                String columnHeaderName = extractHeaderName(beanField);
                header[i] = columnHeaderName;
            }
            return header;
        }

        private String extractHeaderName(final BeanField<T> beanField) {
            if (beanField == null || beanField.getField() == null
                    || beanField.getField().getDeclaredAnnotationsByType(CsvBindByName.class).length == 0) {
                return StringUtils.EMPTY;
            }

            final CsvBindByName bindByNameAnnotation = beanField.getField()
                    .getDeclaredAnnotationsByType(CsvBindByName.class)[0];
            return bindByNameAnnotation.column();
        }
    }

    /**
     * POJO to write ticket data to CSV.
     */
    @SuppressWarnings({"unused", "WeakerAccess", "UnusedReturnValue"})
    public static class TicketRow {

        @CsvBindByPosition(position = 0)
        @CsvBindByName(column = "Ticket Id", required = true)
        private String ticketId;

        @CsvBindByPosition(position = 1)
        @CsvBindByName(column = "Event Id", required = true)
        private String eventId;

        @CsvBindByPosition(position = 2)
        @CsvBindByName(column = "Event Name", required = true)
        private String eventName;

        @CsvBindByPosition(position = 3)
        @CsvBindByName(column = "Venue Id", required = true)
        private String venueId;

        @CsvBindByPosition(position = 4)
        @CsvBindByName(column = "User Id", required = true)
        private String userId;

        @CsvBindByPosition(position = 5)
        @CsvBindByName(column = "Order Id", required = true)
        private String orderId;

        @CsvBindByPosition(position = 6)
        @CsvBindByName(column = "Issue Date", required = true)
        private String issueDateTime;

        @CsvBindByPosition(position = 7)
        @CsvBindByName(column = "Ticket Type Id", required = true)
        private String ticketConfigId;

        @CsvBindByPosition(position = 8)
        @CsvBindByName(column = "Ticket Type Name", required = true)
        private String ticketConfigName;

        @CsvBindByPosition(position = 9)
        @CsvBindByName(column = "Ticket Type Price", required = true)
        private String ticketConfigPrice;

        @CsvBindByPosition(position = 10)
        @CsvBindByName(column = "Ticket Type Currency", required = true)
        private String ticketConfigCurrency;

        public String getTicketId() {
            return ticketId;
        }

        public TicketRow setTicketId(String ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public String getEventId() {
            return eventId;
        }

        public TicketRow setEventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public String getEventName() {
            return eventName;
        }

        public TicketRow setEventName(String eventName) {
            this.eventName = eventName;
            return this;
        }

        public String getVenueId() {
            return venueId;
        }

        public TicketRow setVenueId(String venueId) {
            this.venueId = venueId;
            return this;
        }

        public String getUserId() {
            return userId;
        }

        public TicketRow setUserId(String userId) {
            this.userId = userId;
            return this;
        }

        public String getOrderId() {
            return orderId;
        }

        public TicketRow setOrderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public String getIssueDateTime() {
            return issueDateTime;
        }

        public TicketRow setIssueDateTime(String issueDateTime) {
            this.issueDateTime = issueDateTime;
            return this;
        }

        public String getTicketConfigId() {
            return ticketConfigId;
        }

        public TicketRow setTicketConfigId(String ticketConfigId) {
            this.ticketConfigId = ticketConfigId;
            return this;
        }

        public String getTicketConfigName() {
            return ticketConfigName;
        }

        public TicketRow setTicketConfigName(String ticketConfigName) {
            this.ticketConfigName = ticketConfigName;
            return this;
        }

        public String getTicketConfigPrice() {
            return ticketConfigPrice;
        }

        public TicketRow setTicketConfigPrice(String ticketConfigPrice) {
            this.ticketConfigPrice = ticketConfigPrice;
            return this;
        }

        public String getTicketConfigCurrency() {
            return ticketConfigCurrency;
        }

        public TicketRow setTicketConfigCurrency(String ticketConfigCurrency) {
            this.ticketConfigCurrency = ticketConfigCurrency;
            return this;
        }
    }

    private final AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway;

    private final OrderRepository orderRepository;

    private final StripeGateway stripeGateway;

    private final CalculationService calculationService;

    private static final Logger LOG = LogManager.getLogger();

    private static DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");

    public ReportServiceImpl(AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway, OrderRepository orderRepository, StripeGateway stripeGateway, CalculationService calculationService) {
        this.awsSimpleEmailServiceGateway = awsSimpleEmailServiceGateway;
        this.orderRepository = orderRepository;
        this.stripeGateway = stripeGateway;
        this.calculationService = calculationService;
    }

    @Override
    @Scheduled(cron = "${dailyticketpurchasereport.cron:-}")
    public void generateAndSendDailyTicketPurchaseReport() {

        final ZonedDateTime nowInPST = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("America/Los_Angeles"));
        final ZonedDateTime yesterdayStart = nowInPST.minusDays(1L).withHour(0).withMinute(0).withSecond(0).withNano(0);
        final ZonedDateTime yesterdayEnd = nowInPST.withHour(0).withMinute(0).withSecond(0).withNano(0).minusSeconds(1L);
        final CharArrayWriter writer = new CharArrayWriter();
        LOG.info("Generating DailyTicketPurchaseReport with START_TIME: {} and END_TIME: {}", yesterdayStart, yesterdayEnd);

        final List<OrderEntity> orders = orderRepository
                .findOrderEntitiesByOrderTimestampAfterAndOrderTimestampBefore(yesterdayStart.toOffsetDateTime(), yesterdayEnd.toOffsetDateTime());

        if (orders == null || orders.isEmpty()) {
            LOG.info("No orders were completed yesterday. Skipping report generation.");
            final String reportText = "Nothing to report for today.";
            awsSimpleEmailServiceGateway.sendInternalReport("DailyTicketPurchaseReport",reportText, null);
            return;
        }

        final List<TicketRow> ticketRows = new ArrayList<>();
        for (OrderEntity orderEntity : orders) {

            final Set<OrderTicketEntryEntity> tickets = orderEntity.getTickets();
            if (tickets == null || tickets.isEmpty()) {
                LOG.warn("Skipping orderID: {} because it has no tickets.", orderEntity.getId());
                continue;
            }

            for (OrderTicketEntryEntity ticket : tickets) {

                final TicketEntity ticketEntity = ticket.getTicketEntity();
                final EventEntity eventEntity = ticketEntity.getEventEntity();
                final TicketTypeConfigEntity ticketTypeConfig = ticketEntity.getTicketTypeConfigEntity();

                final ZonedDateTime issuedDate = ticketEntity.getIssuedDate().toZonedDateTime().withZoneSameInstant(ZoneId.of("America/Los_Angeles"));

                TicketRow ticketRow = new TicketRow();
                ticketRow
                        .setTicketId(ticketEntity.getId().toString())
                        .setEventId(eventEntity.getId().toString())
                        .setEventName(eventEntity.getName())
                        .setVenueId(eventEntity.getVenueEntity().getId().toString())
                        .setUserId(ticketEntity.getPurchaserEntity().getId().toString())
                        .setOrderId(orderEntity.getId().toString())
                        .setIssueDateTime(issuedDate.format(DATE_FORMAT))
                        .setTicketConfigId(ticketTypeConfig.getId().toString())
                        .setTicketConfigName(ticketTypeConfig.getName())
                        .setTicketConfigPrice(ticketTypeConfig.getPrice().toPlainString())
                        .setTicketConfigCurrency(ticketTypeConfig.getCurrency());
                ticketRows.add(ticketRow);
            }
        }

        final CustomMappingStrategy<TicketRow> mappingStrategy = new CustomMappingStrategy<>();
        mappingStrategy.setType(TicketRow.class);
        StatefulBeanToCsv<TicketRow> sbc = new StatefulBeanToCsvBuilder<TicketRow>(writer)
                .withSeparator(CSVWriter.DEFAULT_SEPARATOR)
                .withMappingStrategy(mappingStrategy)
                .build();

        try {
            sbc.write(ticketRows);
            writer.close();
        } catch (Exception ex) {
            LOG.error("Failed to generate DailyTicketPurchaseReport. Error: {}" + ex.getMessage());
            ex.printStackTrace();
            return;
        }

        final String bodyText = "### INTERNAL FORIA REPORT ### - " +
                "DailyTicketPurchaseReport" +
                "\r\n" +
                "Report Generated at: " +
                ZonedDateTime.now().withZoneSameInstant(ZoneId.of("America/Los_Angeles")).toString() +
                "\r\n" +
                "\r\n" +
                "Report is attached as a CSV that can be opened in Google Sheets / Excel." +
                "\r\n" +
                "\r\n" +
                "Signed," +
                "\r\n" +
                "Foria API Server" +
                "\r\n" +
                "\r\n" +
                "CONFIDENTIAL - DO NOT FORWARD" +
                "\r\n";

        awsSimpleEmailServiceGateway.sendInternalReport("DailyTicketPurchaseReport.csv", bodyText, writer.toString().getBytes(StandardCharsets.UTF_8));
        LOG.info("DailyTicketPurchaseReport generated and sent at: {}", DateTime.now());
    }

    @Override
    @Scheduled(cron = "${weeklySettlementReport.cron:-}")
    public void generateAndSendWeeklySettlementReport() {

        final ZonedDateTime nowInPST = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("America/Los_Angeles"));
        LOG.info("Generating WeeklySettlementReport for: {}", nowInPST);

        final StripeGatewayImpl.SettlementInfo settlementInfo;
        try {
            settlementInfo = stripeGateway.getSettlementInfo();
        } catch (Exception ex) {
            awsSimpleEmailServiceGateway.sendInternalReport("WeeklySettlementReport", "Failed to generate report. Check Logs.", null);
            LOG.info("WeeklySettlementReport failed to generate and send at: {}", DateTime.now());
            return;
        }

        final Payout payout = settlementInfo.getStripePayout();
        final List<BalanceTransaction> transactions = settlementInfo.getBalanceTransactions();

        final BigDecimal settlementAmount = BigDecimal.valueOf(payout.getAmount()).movePointLeft(2);
        BigDecimal totalNetAmount = BigDecimal.ZERO;
        BigDecimal totalExpectedAmount = BigDecimal.ZERO;
        BigDecimal foriaRevenueAmount = BigDecimal.ZERO;
        BigDecimal venueRevenueAmount = BigDecimal.ZERO;

        //Check that we have record for every transaction
        boolean isTransactionMissing = false;
        for (BalanceTransaction balanceTransaction : transactions) { //Every entry should be an order.

            OrderEntity orderEntity = orderRepository.findByChargeReferenceId(balanceTransaction.getSource());
            if (orderEntity == null) {
                isTransactionMissing = true;
                LOG.warn("Transaction with chargeRefId: {} is not found in order table.", balanceTransaction.getSource());
                continue;
            }

            //Setup Fees
            final Set<OrderFeeEntryEntity> orderFeeEntryEntities = orderEntity.getFees();
            final Set<TicketFeeConfigEntity> feeSet = new HashSet<>();
            for (OrderFeeEntryEntity orderFee : orderFeeEntryEntities) {
                feeSet.add(orderFee.getTicketFeeConfigEntity());
            }

            //Checking net amount against settlement amount catches ledger mismatch.
            BigDecimal netAmount = BigDecimal.valueOf(balanceTransaction.getNet()).movePointLeft(2);
            totalNetAmount = totalNetAmount.add(netAmount);

            BigDecimal ticketSubtotal = BigDecimal.ZERO;
            int numPaidTickets = 0;
            for (OrderTicketEntryEntity orderTicketEntryEntity : orderEntity.getTickets()) {

                TicketEntity ticket = orderTicketEntryEntity.getTicketEntity();

                if (!ticket.getTicketTypeConfigEntity().getCurrency().equalsIgnoreCase("USD")) {
                    LOG.warn("Ticket ID: {} is not in USD. Skipping calculation.", ticket.getId());
                    continue;
                }

                //Check ticket to see if it's free to skip FLAT fee apply.
                if (ticket.getTicketTypeConfigEntity().getPrice().compareTo(BigDecimal.ZERO) > 0) {
                    numPaidTickets++;
                    ticketSubtotal = ticketSubtotal.add(ticket.getTicketTypeConfigEntity().getPrice());
                }
            }

            CalculationServiceImpl.PriceCalculationInfo pInfo = calculationService.calculateFees(numPaidTickets, ticketSubtotal, feeSet);
            foriaRevenueAmount = foriaRevenueAmount.add(pInfo.issuerFeeSubtotal);
            venueRevenueAmount = venueRevenueAmount.add( (pInfo.venueFeeSubtotal.add(pInfo.ticketSubtotal)) );
        }

        totalExpectedAmount = totalExpectedAmount.add( (foriaRevenueAmount.add(venueRevenueAmount)) );

        final String mailDelimiter = "\r\n";
        String reportStr = String.join(
                mailDelimiter,
                "### INTERNAL FORIA REPORT ### - Weekly Settlement Report",
                "Report Generated at: " + ZonedDateTime.now().withZoneSameInstant(ZoneId.of("America/Los_Angeles")).toString(),
                mailDelimiter,
                (isTransactionMissing) ? "### WARNING ### STRIPE CONTAINS CHARGE TRANSACTION NOT IN FORIA ORDER LIST - MANUAL ADJUSTMENT REQUIRED! ### WARNING ###" : "",
                "Amount Being Deposited Today (Settlement Amount): " + settlementAmount.toPlainString() + " " + payout.getCurrency(),
                "Expected Amount For Today (Expected Settlement Amount): " + totalExpectedAmount.toPlainString() + " " + payout.getCurrency(),
                (settlementAmount.compareTo(totalExpectedAmount) != 0) ? "### WARNING ### AMOUNTS DO NOT MATCH - EITHER A REFUND WAS PROCESSED OR MANUAL LEDGER ENTRY OCCURRED - MANUAL ADJUSTMENT REQUIRED! ### WARNING ###" : "",
                mailDelimiter,
                "Foria Revenue Amount (Foria Fees Collected On Tickets - Transfer/Keep this to Foria Operating Account): " + foriaRevenueAmount.toPlainString() + " " + payout.getCurrency(),
                "Venue Revenue Amount (Venue Ticket Rev plus Venue Fees) - Transfer this to Venue ITF Account): " + venueRevenueAmount.toPlainString() + " " + payout.getCurrency(),
                mailDelimiter,
                "Signed,",
                "Foria API Server",
                "CONFIDENTIAL - DO NOT FORWARD"
        );

        awsSimpleEmailServiceGateway.sendInternalReport("Weekly Settlement Report", reportStr, null);
        LOG.info("DailyTicketPurchaseReport generated and sent at: {}", DateTime.now());
    }
}
