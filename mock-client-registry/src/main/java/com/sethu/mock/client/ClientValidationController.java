package com.sethu.mock.client;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/client-validations")
public class ClientValidationController {

    @PostMapping
    public Response validate(@RequestBody Request request) {
        boolean valid = !request.clientId().equalsIgnoreCase("INVALID");
        return new Response(
                "CV-" + request.clientId(),
                valid,
                valid ? "ACTIVE" : "INACTIVE",
                valid ? new String[0] : new String[]{"CLIENT_NOT_ACTIVE"}
        );
    }

    public record Request(String clientId) {
    }

    public record Response(
            String validationReference,
            boolean valid,
            String clientStatus,
            String[] reasonCodes
    ) {
    }
}
