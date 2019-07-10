package com.foriatickets.foriabackend.controllers;

import org.openapitools.model.Ticket;
import org.openapitools.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class UserApi implements org.openapitools.api.UserApi {

    @Override
    @RequestMapping(value = "/user/{user_id}/tickets", method = RequestMethod.GET)
    public ResponseEntity<List<Ticket>> getTickets(@PathVariable("user_id") UUID userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequestMapping(value = "/user/{user_id}", method = RequestMethod.GET)
    public ResponseEntity<User> getUser(@PathVariable("user_id") UUID userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
