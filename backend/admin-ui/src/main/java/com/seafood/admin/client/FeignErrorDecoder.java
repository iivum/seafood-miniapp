package com.seafood.admin.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FeignErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(FeignErrorDecoder.class);

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String reason = response.reason();

        log.error("Feign client error: method={}, status={}, reason={}", methodKey, status, reason);

        return switch (status) {
            case 400 -> new BadRequestException("Invalid request: " + reason);
            case 401 -> new UnauthorizedException("Authentication required");
            case 403 -> new ForbiddenException("Access denied");
            case 404 -> new NotFoundException("Resource not found: " + methodKey);
            case 500 -> new ServerErrorException("Server error: " + reason);
            default -> new FeignClientException("Feign error: " + status + " " + reason);
        };
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) { super(message); }
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) { super(message); }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) { super(message); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class ServerErrorException extends RuntimeException {
        public ServerErrorException(String message) { super(message); }
    }

    public static class FeignClientException extends RuntimeException {
        public FeignClientException(String message) { super(message); }
    }
}
