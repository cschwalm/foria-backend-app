package com.foriatickets.foriabackend.api_models;

import java.io.Serializable;

public class BaseApiModel implements Serializable {

    private String errorCode = "0";
    private String message = "OK";

    public String getErrorCode() {
        return errorCode;
    }

    public BaseApiModel setErrorCode(String errorCode) {
        this.errorCode = errorCode;
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
                "errorCode='" + errorCode + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
