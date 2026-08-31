package com.sethu.mock.policy;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/claim-eligibility-checks")
public class PolicyEligibilityController {

    @PostMapping
    public Response check(@RequestBody Request request) {
        boolean eligible = !request.policyNumber().equalsIgnoreCase("INACTIVE");
        boolean manualReview = request.policyNumber().equalsIgnoreCase("MANUAL");

        return new Response(
                "PV-" + request.policyNumber(),
                eligible,
                manualReview,
                "policy-rules-v1",
                eligible ? new String[0] : new String[]{"POLICY_NOT_ELIGIBLE"}
        );
    }

    public record Request(String policyNumber, BigDecimal amount) {
    }

    public record Response(
            String validationReference,
            boolean eligible,
            boolean manualReviewRequired,
            String ruleVersion,
            String[] reasonCodes
    ) {
    }
}
