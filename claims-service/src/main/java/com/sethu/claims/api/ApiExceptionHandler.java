package com.sethu.claims.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Invalid request" : exception.getMessage();
        HttpStatus status;
        String code;

        if (message.startsWith("Claim not found:")) {
            status = HttpStatus.NOT_FOUND;
            code = "CLAIM_NOT_FOUND";
        } else if (message.startsWith("Idempotency key")) {
            status = HttpStatus.CONFLICT;
            code = "CLAIM_IDEMPOTENCY_CONFLICT";
        } else {
            status = HttpStatus.BAD_REQUEST;
            code = "INVALID_REQUEST";
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
