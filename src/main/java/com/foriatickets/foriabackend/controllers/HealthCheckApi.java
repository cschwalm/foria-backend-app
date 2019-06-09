package com.foriatickets.foriabackend.controllers;

import io.swagger.model.BaseApiModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class HealthCheckApi implements io.swagger.api.HealthCheckApi {

    @Override
    @RequestMapping(path = "/health-check", method = RequestMethod.GET)
    public ResponseEntity<BaseApiModel> healthCheck() {

        return new ResponseEntity<>(new BaseApiModel(), HttpStatus.OK);
    }
}
