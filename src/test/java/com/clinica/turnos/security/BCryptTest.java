package com.clinica.turnos.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BCryptTest {

    @Test
    void bcryptStrength12GeneraHashCorrecto() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String raw = "admin123";
        String hash = encoder.encode(raw);
        assertThat(hash).startsWith("$2a$12$");
        assertThat(hash).hasSizeGreaterThanOrEqualTo(59);
        assertThat(encoder.matches(raw, hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
        // No es md5 (32 chars hex)
        assertThat(hash.length()).isNotEqualTo(32);
    }

    @Test
    void hashLongitud255Suficiente() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode("testPassword123");
        assertThat(hash.length()).isLessThanOrEqualTo(255);
        assertThat(hash.length()).isGreaterThanOrEqualTo(59);
    }
}
