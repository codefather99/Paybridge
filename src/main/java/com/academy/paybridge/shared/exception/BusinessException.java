package com.academy.paybridge.shared.exception;
/** A rule of the business was violated (not a bug). carries a stable machine-readable code **/

public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message){
        super(message);
        this.code = code;
    }

    public String getCode(){
        return code;
    }
}
