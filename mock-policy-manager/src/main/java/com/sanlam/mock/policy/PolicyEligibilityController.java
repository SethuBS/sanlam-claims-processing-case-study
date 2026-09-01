package com.sanlam.mock.policy;

import com.sanlam.mock.policy.dto.request.PolicyEligibilityRequest;
import com.sanlam.mock.policy.dto.response.PolicyEligibilityResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/claim-eligibility-checks")
public class PolicyEligibilityController
{

    @PostMapping
    public PolicyEligibilityResponse check(@RequestBody PolicyEligibilityRequest request)
    {
        boolean eligible = !request.policyNumber().equalsIgnoreCase("INACTIVE");
        boolean manualReview = request.policyNumber().equalsIgnoreCase("MANUAL");

        return new PolicyEligibilityResponse("PV-" + request.policyNumber(), eligible, manualReview, "policy-rules-v1",
                eligible ? new String[0] : new String[]{"POLICY_NOT_ELIGIBLE"});
    }

}
