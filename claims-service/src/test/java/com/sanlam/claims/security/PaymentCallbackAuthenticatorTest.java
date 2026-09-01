package com.sanlam.claims.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentCallbackAuthenticatorTest
{

    private static final Instant NOW = Instant.parse("2026-09-01T08:00:00Z");
    private static final String SECRET = "test-callback-secret-12345";
    private final PaymentCallbackAuthenticator authenticator = new PaymentCallbackAuthenticator(SECRET,
            Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsAnAuthenticRecentPayload()
    {
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String payload = "{\"eventId\":\"event-1\"}";

        assertThatCode(() -> authenticator.verify(timestamp, authenticator.signature(timestamp, payload), payload))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTamperingAndStaleReplayAttempts()
    {
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String signature = authenticator.signature(timestamp, "original");

        assertThatThrownBy(() -> authenticator.verify(timestamp, signature, "tampered"))
                .isInstanceOf(PaymentCallbackAuthenticationException.class).hasMessageContaining("signature");

        String stale = String.valueOf(NOW.minus(Duration.ofMinutes(6)).getEpochSecond());
        assertThatThrownBy(() -> authenticator.verify(stale, authenticator.signature(stale, "original"), "original"))
                .isInstanceOf(PaymentCallbackAuthenticationException.class).hasMessageContaining("replay window");
    }
}
