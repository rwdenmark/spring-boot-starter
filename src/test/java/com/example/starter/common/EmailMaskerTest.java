package com.example.starter.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailMaskerTest {

    @Test
    void mask_keepsFirstCharacterAndDomain() {
        assertThat(EmailMasker.mask("alice@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void mask_singleCharacterLocalPart() {
        assertThat(EmailMasker.mask("a@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void mask_nullOrBlank_returnsPlaceholder() {
        assertThat(EmailMasker.mask(null)).isEqualTo("<none>");
        assertThat(EmailMasker.mask("  ")).isEqualTo("<none>");
    }

    @Test
    void mask_valueWithoutUsableLocalPart_isFullyMasked() {
        assertThat(EmailMasker.mask("not-an-email")).isEqualTo("***");
        assertThat(EmailMasker.mask("@example.com")).isEqualTo("***");
    }
}
