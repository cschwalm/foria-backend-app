package com.foriatickets.foriabackend.controllers;

import com.foriatickets.foriabackend.service.TicketService;
import org.openapitools.model.*;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import java.util.UUID;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class TicketApi implements org.openapitools.api.TicketApi {

    private final BeanFactory beanFactory;

    public TicketApi(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    @RequestMapping(value = "/ticket/checkout", method = RequestMethod.POST)
    public ResponseEntity<Order> checkout(@Valid Order order) {

        TicketService ticketService = beanFactory.getBean(TicketService.class);
        UUID orderId = ticketService.checkoutOrder(order.getPaymentToken(), order.getEventId(), order.getTicketLineItemList());
        order.setId(orderId);
        return new ResponseEntity<>(order, HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}/activate", method = RequestMethod.POST)
    public ResponseEntity<ActivationResult> activateTicket(@PathVariable("ticket_id") UUID ticketId) {

        TicketService ticketService = beanFactory.getBean(TicketService.class);
        ActivationResult activationResult = ticketService.activateTicket(ticketId);
        return new ResponseEntity<>(activationResult, HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}/cancelTransfer", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> cancelTransfer(@PathVariable("ticket_id") UUID ticketId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}", method = RequestMethod.GET)
    public ResponseEntity<Ticket> getTicket(@PathVariable("ticket_id") UUID ticketId) {

        TicketService ticketService = beanFactory.getBean(TicketService.class);
        Ticket ticket = ticketService.getTicket(ticketId);
        return new ResponseEntity<>(ticket, HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}/reactivate", method = RequestMethod.POST)
    public ResponseEntity<ActivationResult> reactivateTicket(@PathVariable("ticket_id") UUID ticketId) {

        TicketService ticketService = beanFactory.getBean(TicketService.class);
        ActivationResult activationResult = ticketService.reactivateTicket(ticketId);
        return new ResponseEntity<>(activationResult, HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}/redeem", method = RequestMethod.POST)
    public ResponseEntity<RedemptionResult> redeemTicket(@PathVariable("ticket_id") UUID ticketId, @Valid @RequestBody RedemptionRequest redemptionRequest) {

        TicketService ticketService = beanFactory.getBean(TicketService.class);
        RedemptionResult redemptionResult = ticketService.redeemTicket(redemptionRequest.getTicketId(), redemptionRequest.getTicketOtp());
        return new ResponseEntity<>(redemptionResult, redemptionResult.getStatus() == RedemptionResult.StatusEnum.ALLOW ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}/transfer", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> transferTicket(@PathVariable("ticket_id") UUID ticketId, @Valid @RequestBody TransferRequest transferRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
