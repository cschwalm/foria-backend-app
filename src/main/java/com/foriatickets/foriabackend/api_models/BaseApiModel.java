package com.foriatickets.foriabackend.api_models;

import java.io.Serializable;

public class BaseApiModel implements Serializable {

    private String statusCode = "0";
    private String message = "OK";

    public String getStatusCode() {
        return statusCode;
    }

    public BaseApiModel setStatusCode(String statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public BaseApiModel setMessage(String message) {
        this.message = message;
        return this;
    }

    @Override
    public String toString() {
        return "BaseApiModel{" +
                "statusCode='" + statusCode + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
