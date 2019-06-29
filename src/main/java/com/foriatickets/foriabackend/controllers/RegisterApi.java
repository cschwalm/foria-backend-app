package com.foriatickets.foriabackend.controllers;

import com.foriatickets.foriabackend.service.UserCreationService;
import org.openapitools.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class RegisterApi implements org.openapitools.api.RegisterApi {

    private UserCreationService userCreationService;

    public RegisterApi(@Autowired UserCreationService userCreationService) {

        assert userCreationService != null;
        this.userCreationService = userCreationService;
    }

    @Override
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public ResponseEntity<User> registerUser(@Valid @RequestBody User body) {

        User newUser = userCreationService.createUser(body);
        return new ResponseEntity<>(newUser, HttpStatus.OK);
    }
}
