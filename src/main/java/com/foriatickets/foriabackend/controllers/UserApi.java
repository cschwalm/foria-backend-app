package com.foriatickets.foriabackend.controllers;

import com.foriatickets.foriabackend.service.TicketService;
import com.foriatickets.foriabackend.service.UserCreationService;
import org.openapitools.model.BaseApiModel;
import org.openapitools.model.DeviceToken;
import org.openapitools.model.Ticket;
import org.openapitools.model.User;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import java.util.List;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class UserApi implements org.openapitools.api.UserApi {

    private final BeanFactory beanFactory;

    public UserApi(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    @RequestMapping(value = "/user/tickets", method = RequestMethod.GET)
    public ResponseEntity<List<Ticket>> getTickets() {

        TicketService ticketService = beanFactory.getBean(TicketService.class);
        List<Ticket> tickets = ticketService.getUsersTickets();
        return new ResponseEntity<>(tickets, HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/user", method = RequestMethod.GET)
    public ResponseEntity<User> getUser() {

        UserCreationService userCreationService = beanFactory.getBean(UserCreationService.class);
        User user = userCreationService.getUser();

        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<BaseApiModel> registerToken(@Valid DeviceToken deviceToken) {

        UserCreationService userCreationService = beanFactory.getBean(UserCreationService.class);
        userCreationService.registerDeviceToken(deviceToken.getToken());
        return new ResponseEntity<>(new BaseApiModel(), HttpStatus.OK);
    }
}
