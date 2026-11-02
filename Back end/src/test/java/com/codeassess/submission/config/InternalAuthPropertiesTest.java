package com.codeassess.submission.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InternalAuthProperties: Fail-fast validation tests for INTERNAL_API_SECRET")
class InternalAuthPropertiesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Missing or blank secret fails fast with IllegalStateException")
    void missingOrBlankSecret_FailsFast(String invalidSecret) {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setApiSecret(invalidSecret);

        assertThatThrownBy(properties::validateOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_API_SECRET is missing or empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "codeassess-internal-secret-2026",
            "CODEASSESS-INTERNAL-SECRET-2026",
            "  codeassess-internal-secret-2026  "
    })
    @DisplayName("Legacy default secret is strictly rejected with IllegalStateException")
    void legacyDefaultSecret_FailsFast(String legacySecret) {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setApiSecret(legacySecret);

        assertThatThrownBy(properties::validateOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot use the insecure legacy default value");
    }

    @Test
    @DisplayName("Valid non-default secret passes startup validation")
    void validSecret_PassesValidation() {
        InternalAuthProperties properties = new InternalAuthProperties();
        String validSecret = "secure-random-256bit-internal-token-for-prod";
        properties.setApiSecret(validSecret);

        properties.validateOnStartup();

        assertThat(properties.getApiSecret()).isEqualTo(validSecret);
    }
}
