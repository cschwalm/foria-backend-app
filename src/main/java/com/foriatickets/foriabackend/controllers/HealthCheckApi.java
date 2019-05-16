package com.foriatickets.foriabackend.controllers;

import com.foriatickets.foriabackend.api_models.BaseApiModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class HealthCheckApi {

    /**
     * Simple health check that does nothing more but ensure the system is receiving API requests.
     * @return Base model.
     */
    @RequestMapping(path = "/health-check", method = RequestMethod.GET)
    public ResponseEntity<BaseApiModel> healthCheck() {

        return new ResponseEntity<>(new BaseApiModel(), HttpStatus.OK);
    }
}
