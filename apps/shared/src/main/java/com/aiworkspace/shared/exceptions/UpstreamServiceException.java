package com.aiworkspace.shared.exceptions;

public class UpstreamServiceException extends RuntimeException {

    private final String serviceName;

    public UpstreamServiceException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }

    public UpstreamServiceException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }

    public String serviceName() {
        return serviceName;
    }
}
