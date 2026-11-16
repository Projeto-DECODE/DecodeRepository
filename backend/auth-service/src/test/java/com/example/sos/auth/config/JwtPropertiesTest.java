package com.example.sos.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static JwtProperties withUserTtl(Duration ttl) {
        return new JwtProperties("iss", "sos-api", "sos-internal", ttl, Duration.ofMinutes(5), null);
    }

    @Test
    void acceptsThirtyMinutes() {
        assertDoesNotThrow(() -> withUserTtl(Duration.ofMinutes(30)));
    }

    @Test
    void rejectsMoreThanThirtyMinutes() {
        assertThrows(IllegalStateException.class, () -> withUserTtl(Duration.ofMinutes(31)));
    }

    @Test
    void rejectsZeroAndNegative() {
        assertThrows(IllegalStateException.class, () -> withUserTtl(Duration.ZERO));
        assertThrows(IllegalStateException.class, () -> withUserTtl(Duration.ofMinutes(-1)));
    }
}
