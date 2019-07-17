package com.foriatickets.foriabackend.controllers;

import org.openapitools.model.Ticket;
import org.openapitools.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class UserApi implements org.openapitools.api.UserApi {

    @Override
    @RequestMapping(value = "/user/tickets", method = RequestMethod.GET)
    public ResponseEntity<List<Ticket>> getTickets() {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequestMapping(value = "/user", method = RequestMethod.GET)
    public ResponseEntity<User> getUser() {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
