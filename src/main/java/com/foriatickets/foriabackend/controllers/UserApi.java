package com.foriatickets.foriabackend.controllers;

import io.swagger.model.Ticket;
import io.swagger.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.constraints.Size;
import java.util.List;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class UserApi implements io.swagger.api.UserApi {

    @Override
    @RequestMapping(value = "/user/{user_id}/tickets", method = RequestMethod.GET)
    public ResponseEntity<List<Ticket>> getTickets(@Size(max = 36) @PathVariable("user_id") String userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequestMapping(value = "/user/{user_id}", method = RequestMethod.GET)
    public ResponseEntity<User> getUser(@Size(max = 36) @PathVariable("user_id") String userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
