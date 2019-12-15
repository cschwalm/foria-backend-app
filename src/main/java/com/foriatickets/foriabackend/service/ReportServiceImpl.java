package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.*;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.gateway.StripeGateway;
import com.foriatickets.foriabackend.gateway.StripeGatewayImpl;
import com.foriatickets.foriabackend.repositories.OrderRepository;
import com.foriatickets.foriabackend.service.report_templates.OrderReportRow;
import com.foriatickets.foriabackend.service.report_templates.TicketRow;
import com.opencsv.CSVWriter;
import com.opencsv.bean.*;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Payout;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
    @Scheduled(cron = "${daily-ticket-purchase-report-cron:-}")
    @SchedulerLock(name = "daily-ticket-purchase-report")
    public void generateAndSendDailyTicketPurchaseReport() {

        final ZonedDateTime nowInPST = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("America/Los_Angeles"));
        final ZonedDateTime yesterdayStart = nowInPST.minusDays(1L).withHour(0).withMinute(0).withSecond(0).withNano(0);
        final ZonedDateTime yesterdayEnd = nowInPST.withHour(0).withMinute(0).withSecond(0).withNano(0).minusSeconds(1L);

        generateAndSendTicketReport(yesterdayStart, yesterdayEnd);
    }

    @Override
    @Scheduled(cron = "${rolling-ticket-purchase-report-cron:-}")
    @SchedulerLock(name = "rolling-ticket-purchase-report")
    public void generateAndSendRollingTicketPurchaseReport() {

        final ZonedDateTime start = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("America/Los_Angeles"));
        generateAndSendTicketReport(start.minusYears(10), start);
    }

    @SuppressWarnings("Duplicates")
    @Override
    @Scheduled(cron = "${weekly-settlement-report-cron:-}")
    @SchedulerLock(name = "weekly-settlement-report")
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
        final List<BalanceTransaction> charges = settlementInfo.getChargeTransactions();
        final List<BalanceTransaction> refunds = settlementInfo.getRefundTransactions();

        final BigDecimal settlementAmount = BigDecimal.valueOf(payout.getAmount()).movePointLeft(2);
        BigDecimal totalExpectedAmount = BigDecimal.ZERO;
        BigDecimal foriaRevenueAmount = BigDecimal.ZERO;
        BigDecimal venueRevenueAmount = BigDecimal.ZERO;

        int numCharges = 0;
        int numRefunds = 0;

        final List<OrderReportRow> orderReportRows = new ArrayList<>();
        final List<OrderReportRow> refundReportRows = new ArrayList<>();

        boolean isTransactionMissing = false;
        for (BalanceTransaction refund : refunds) {

            OrderEntity orderEntity = orderRepository.findByRefundReferenceId(refund.getSource());

            if (orderEntity == null) {
                isTransactionMissing = true;
                LOG.warn("Transaction with refundRefId: {} is not found in order table.", refund.getSource());
                continue;
            }

            numRefunds++;

            if (orderEntity.getStatus() != OrderEntity.Status.CANCELED) {
                LOG.error("Stripe transaction marked as REFUND but order is not canceled. Order ID: {} - Refund ID: {}", orderEntity.getId(), refund.getSource());
            }

            //Setup Fees
            final Set<OrderFeeEntryEntity> orderFeeEntryEntities = orderEntity.getFees();
            final Set<TicketFeeConfigEntity> feeSet = new HashSet<>();
            for (OrderFeeEntryEntity orderFee : orderFeeEntryEntities) {
                feeSet.add(orderFee.getTicketFeeConfigEntity());
            }

            //Subtract confirmed refunds from settlement total.
            final BigDecimal refundAmount = BigDecimal.valueOf(refund.getAmount()).movePointLeft(2);

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
            foriaRevenueAmount = foriaRevenueAmount.add(pInfo.issuerFeeSubtotal.negate());
            venueRevenueAmount = venueRevenueAmount.add( (pInfo.venueFeeSubtotal.add(pInfo.ticketSubtotal).add(pInfo.paymentFeeSubtotal)).negate() );

            //Build refund report entry.
            final OrderReportRow refundReportRow = new OrderReportRow();
            refundReportRow.setOrderId(orderEntity.getId().toString());
            refundReportRow.setUserId(orderEntity.getPurchaser().getId().toString());
            refundReportRow.setUserEmail(orderEntity.getPurchaser().getEmail());
            refundReportRow.setOrderStatus(orderEntity.getStatus().name());
            refundReportRow.setOrderDateTime(orderEntity.getOrderTimestamp().format(DATE_FORMAT));
            refundReportRow.setChargeAmount(refundAmount.toPlainString());
            refundReportRow.setPaymentFeeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).toPlainString());
            refundReportRow.setNetAmount(refundAmount.toPlainString());
            refundReportRow.setTicketSubtotal(ticketSubtotal.negate().toPlainString());
            refundReportRow.setIssuerFeeAmount(pInfo.issuerFeeSubtotal.negate().toPlainString());
            refundReportRow.setVenueFeeAmount( (pInfo.venueFeeSubtotal.add(pInfo.paymentFeeSubtotal) ).negate().toPlainString());
            refundReportRow.setCurrency(orderEntity.getCurrency());
            refundReportRows.add(refundReportRow);
        }

        //Check that we have record for every transaction
        for (BalanceTransaction balanceTransaction : charges) {

            OrderEntity orderEntity = orderRepository.findByChargeReferenceId(balanceTransaction.getSource());
            if (orderEntity == null) {
                isTransactionMissing = true;
                LOG.warn("Transaction with chargeRefId: {} is not found in order table.", balanceTransaction.getSource());
                continue;
            }

            numCharges++;

            //Setup Fees
            final Set<OrderFeeEntryEntity> orderFeeEntryEntities = orderEntity.getFees();
            final Set<TicketFeeConfigEntity> feeSet = new HashSet<>();
            for (OrderFeeEntryEntity orderFee : orderFeeEntryEntities) {
                feeSet.add(orderFee.getTicketFeeConfigEntity());
            }

            //Checking net amount against settlement amount catches ledger mismatch.
            final BigDecimal chargeAmount = BigDecimal.valueOf(balanceTransaction.getAmount()).movePointLeft(2);
            final BigDecimal paymentFeeAmount = BigDecimal.valueOf(balanceTransaction.getFee()).movePointLeft(2);
            final BigDecimal netAmount = BigDecimal.valueOf(balanceTransaction.getNet()).movePointLeft(2);

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
            venueRevenueAmount = venueRevenueAmount.add((pInfo.venueFeeSubtotal.add(pInfo.ticketSubtotal)));

            //Build report entry.
            final OrderReportRow orderReportRow = new OrderReportRow();
            orderReportRow.setOrderId(orderEntity.getId().toString());
            orderReportRow.setUserId(orderEntity.getPurchaser().getId().toString());
            orderReportRow.setUserEmail(orderEntity.getPurchaser().getEmail());
            orderReportRow.setOrderStatus(orderEntity.getStatus().name());
            orderReportRow.setOrderDateTime(orderEntity.getOrderTimestamp().format(DATE_FORMAT));
            orderReportRow.setChargeAmount(chargeAmount.toPlainString());
            orderReportRow.setPaymentFeeAmount(paymentFeeAmount.toPlainString());
            orderReportRow.setNetAmount(netAmount.toPlainString());
            orderReportRow.setTicketSubtotal(ticketSubtotal.toPlainString());
            orderReportRow.setIssuerFeeAmount(pInfo.issuerFeeSubtotal.toPlainString());
            orderReportRow.setVenueFeeAmount(pInfo.venueFeeSubtotal.toPlainString());
            orderReportRow.setCurrency(orderEntity.getCurrency());
            orderReportRows.add(orderReportRow);
        }

        totalExpectedAmount = totalExpectedAmount.add((foriaRevenueAmount.add(venueRevenueAmount)));

        final Date settlementDate = new Date(payout.getArrivalDate() * 1000);
        final String mailDelimiter = "\r\n";

        String reportStr = String.join(
                mailDelimiter,
                "### INTERNAL FORIA REPORT ### - Weekly Settlement Report",
                "Report Generated at: " + ZonedDateTime.now().withZoneSameInstant(ZoneId.of("America/Los_Angeles")).toString(),
                mailDelimiter,
                "Settlement Date: " + settlementDate.toString(),
                "Stripe Payout Id: " + payout.getId(),
                "Number of Charges: " + numCharges,
                "Number of Refunds: " + numRefunds + mailDelimiter,
                (isTransactionMissing) ? mailDelimiter + "### WARNING ### STRIPE CONTAINS CHARGE TRANSACTION NOT IN FORIA ORDER LIST - MANUAL ADJUSTMENT REQUIRED! ### WARNING ###" : "",
                "Amount Being Deposited Today (Settlement Amount): " + settlementAmount.toPlainString() + " " + payout.getCurrency(),
                "Expected Amount For Today (Expected Settlement Amount): " + totalExpectedAmount.toPlainString() + " " + payout.getCurrency(),
                (settlementAmount.compareTo(totalExpectedAmount) != 0) ? "### WARNING ### AMOUNTS DO NOT MATCH - EITHER A CHARGE BACK OCCURRED OR MANUAL LEDGER ENTRY OCCURRED - MANUAL ADJUSTMENT REQUIRED! ### WARNING ###" : "",
                mailDelimiter,
                "Foria Revenue Amount (Foria Fees Collected On Tickets - Transfer/Keep this to Foria Operating Account): " + foriaRevenueAmount.toPlainString() + " " + payout.getCurrency(),
                "Venue Revenue Amount (Venue Ticket Rev plus Venue Fees) - Transfer this to Venue ITF Account): " + venueRevenueAmount.toPlainString() + " " + payout.getCurrency(),
                mailDelimiter,
                "Signed,",
                "Foria API Server",
                "CONFIDENTIAL - DO NOT FORWARD",
                mailDelimiter
        );

        final CharArrayWriter writer = new CharArrayWriter();
        final CustomMappingStrategy<OrderReportRow> mappingStrategy = new CustomMappingStrategy<>();
        mappingStrategy.setType(OrderReportRow.class);
        StatefulBeanToCsv<OrderReportRow> sbc = new StatefulBeanToCsvBuilder<OrderReportRow>(writer)
                .withSeparator(CSVWriter.DEFAULT_SEPARATOR)
                .withMappingStrategy(mappingStrategy)
                .build();

        final CharArrayWriter refundWriter = new CharArrayWriter();
        StatefulBeanToCsv<OrderReportRow> sbcRefund = new StatefulBeanToCsvBuilder<OrderReportRow>(refundWriter)
                .withSeparator(CSVWriter.DEFAULT_SEPARATOR)
                .withMappingStrategy(mappingStrategy)
                .build();

        try {
            sbc.write(orderReportRows);
            writer.close();

            sbcRefund.write(refundReportRows);
            refundWriter.close();

        } catch (Exception ex) {
            LOG.error("Failed to generate OrderReport. Error: {}" + ex.getMessage());
            ex.printStackTrace();
            return;
        }

        final List<AWSSimpleEmailServiceGateway.ReportAttachment> reportAttachmentList = new ArrayList<>();
        final AWSSimpleEmailServiceGateway.ReportAttachment reportAttachment = new AWSSimpleEmailServiceGateway.ReportAttachment();
        reportAttachment.reportDataArray = writer.toString().getBytes(StandardCharsets.UTF_8);
        reportAttachment.reportFilename = "OrderReport.csv";
        reportAttachmentList.add(reportAttachment);

        if (!refundReportRows.isEmpty()) {
            final AWSSimpleEmailServiceGateway.ReportAttachment refundAttachment = new AWSSimpleEmailServiceGateway.ReportAttachment();
            refundAttachment.reportDataArray = refundWriter.toString().getBytes(StandardCharsets.UTF_8);
            refundAttachment.reportFilename = "RefundReport.csv";
            reportAttachmentList.add(refundAttachment);
        }

        awsSimpleEmailServiceGateway.sendInternalReport("Weekly Settlement Report", reportStr, reportAttachmentList);
        LOG.info("Weekly Settlement Report generated and sent at: {}", DateTime.now());
    }

    /**
     * Internal method sends report for specific window. Logic split to have different time triggers.
     *
     * @param startDateTime Specified start period.
     * @param endDateTime Specified end period.
     */
    private void generateAndSendTicketReport(ZonedDateTime startDateTime, ZonedDateTime endDateTime) {

        LOG.info("Generating DailyTicketPurchaseReport with START_TIME: {} and END_TIME: {}", startDateTime, endDateTime);

        final List<OrderEntity> orders = orderRepository
                .findOrderEntitiesByOrderTimestampAfterAndOrderTimestampBeforeOrderByOrderTimestampAsc(startDateTime.toOffsetDateTime(), endDateTime.toOffsetDateTime());

        if (orders == null || orders.isEmpty()) {
            LOG.info("No orders were completed yesterday. Skipping report generation.");
            final String reportText = "Nothing to report for today.";
            awsSimpleEmailServiceGateway.sendInternalReport("DailyTicketPurchaseReport", reportText, null);
            return;
        }

        final List<TicketRow> ticketRows = new ArrayList<>();
        for (OrderEntity orderEntity : orders) {

            final Set<OrderTicketEntryEntity> tickets = orderEntity.getTickets();
            if (tickets == null || tickets.isEmpty()) {
                LOG.info("Skipping orderID: {} because it has no tickets.", orderEntity.getId());
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
                        .setTicketConfigCurrency(ticketTypeConfig.getCurrency())
                        .setTicketStatus(ticketEntity.getStatus().name());
                ticketRows.add(ticketRow);
            }
        }

        final String bodyText = "### INTERNAL FORIA REPORT ### - " +
                "TicketPurchaseReport" +
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

        final CharArrayWriter writer = new CharArrayWriter();
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
            LOG.error("Failed to generate TicketPurchaseReport. Error: {}" + ex.getMessage());
            ex.printStackTrace();
            return;
        }

        final List<AWSSimpleEmailServiceGateway.ReportAttachment> reportAttachmentList = new ArrayList<>();
        final AWSSimpleEmailServiceGateway.ReportAttachment reportAttachment = new AWSSimpleEmailServiceGateway.ReportAttachment();
        reportAttachment.reportDataArray = writer.toString().getBytes(StandardCharsets.UTF_8);
        reportAttachment.reportFilename = "TicketPurchaseReport.csv";
        reportAttachmentList.add(reportAttachment);

        awsSimpleEmailServiceGateway.sendInternalReport("Ticket Purchase Report", bodyText, reportAttachmentList);
        LOG.info("Ticket purchase report generated and sent at: {}", DateTime.now());
    }
}
