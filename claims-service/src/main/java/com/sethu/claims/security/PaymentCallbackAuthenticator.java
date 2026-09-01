package com.sethu.claims.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class PaymentCallbackAuthenticator {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Duration maxAge;
    private final Clock clock;

    public PaymentCallbackAuthenticator(
            @Value("${security.payment-callback.secret}") String secret,
            @Value("${security.payment-callback.max-age:5m}") Duration maxAge,
            Clock clock
    ) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException("Payment callback secret must contain at least 16 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.maxAge = maxAge;
        this.clock = clock;
    }

    public void verify(String timestampHeader, String signatureHeader, String payload) {
        if (timestampHeader == null || signatureHeader == null) {
            throw new PaymentCallbackAuthenticationException("Missing payment callback authentication headers");
        }

        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestampHeader);
        } catch (NumberFormatException exception) {
            throw new PaymentCallbackAuthenticationException("Invalid payment callback timestamp");
        }

        Instant signedAt = Instant.ofEpochSecond(epochSeconds);
        Duration age = Duration.between(signedAt, clock.instant()).abs();
        if (age.compareTo(maxAge) > 0) {
            throw new PaymentCallbackAuthenticationException("Payment callback timestamp is outside the replay window");
        }

        byte[] supplied = decodeSignature(signatureHeader);
        byte[] expected = sign(timestampHeader + "." + payload);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new PaymentCallbackAuthenticationException("Invalid payment callback signature");
        }
    }

    public String signature(String timestampHeader, String payload) {
        return "v1=" + HexFormat.of().formatHex(sign(timestampHeader + "." + payload));
    }

    private byte[] decodeSignature(String signatureHeader) {
        if (!signatureHeader.startsWith("v1=")) {
            throw new PaymentCallbackAuthenticationException("Unsupported payment callback signature version");
        }
        try {
            return HexFormat.of().parseHex(signatureHeader.substring(3));
        } catch (IllegalArgumentException exception) {
            throw new PaymentCallbackAuthenticationException("Invalid payment callback signature encoding");
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate payment callback signature", exception);
        }
    }
}
