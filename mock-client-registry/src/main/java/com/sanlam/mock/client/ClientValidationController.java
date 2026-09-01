package com.sanlam.mock.client;

import com.sanlam.mock.client.dto.request.ClientValidationRequest;
import com.sanlam.mock.client.dto.response.ClientValidationResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/client-validations")
public class ClientValidationController
{

    @PostMapping
    public ClientValidationResponse validate(@RequestBody ClientValidationRequest request)
    {
        boolean valid = !request.clientId().equalsIgnoreCase("INVALID");
        return new ClientValidationResponse("CV-" + request.clientId(), valid, valid ? "ACTIVE" : "INACTIVE",
                valid ? new String[0] : new String[]{"CLIENT_NOT_ACTIVE"});
    }

}
