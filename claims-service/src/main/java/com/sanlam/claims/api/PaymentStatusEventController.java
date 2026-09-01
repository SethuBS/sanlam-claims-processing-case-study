package com.sanlam.claims.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanlam.claims.application.ClaimService;
import com.sanlam.claims.dto.request.PaymentStatusEventRequest;
import com.sanlam.claims.security.PaymentCallbackAuthenticator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/internal/v1/payment-status-events")
public class PaymentStatusEventController
{

    private final ClaimService claimService;
    private final PaymentCallbackAuthenticator authenticator;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public PaymentStatusEventController(ClaimService claimService, PaymentCallbackAuthenticator authenticator,
            ObjectMapper objectMapper, Validator validator)
    {
        this.claimService = claimService;
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "X-Callback-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Callback-Signature", required = false) String signature,
            @RequestBody String payload)
    {
        authenticator.verify(timestamp, signature, payload);
        PaymentStatusEventRequest event = readAndValidate(payload);
        claimService.handlePaymentStatusEvent(event.eventId(), event.claimId(), event.paymentReference(),
                event.status());
        return ResponseEntity.accepted().build();
    }

    private PaymentStatusEventRequest readAndValidate(String payload)
    {
        try
        {
            PaymentStatusEventRequest event = objectMapper.readValue(payload, PaymentStatusEventRequest.class);
            Set<ConstraintViolation<PaymentStatusEventRequest>> violations = validator.validate(event);
            if (!violations.isEmpty())
            {
                throw new IllegalArgumentException(violations.iterator().next().getMessage());
            }
            return event;
        }
        catch (JsonProcessingException exception)
        {
            throw new IllegalArgumentException("Invalid payment callback payload", exception);
        }
    }

}
