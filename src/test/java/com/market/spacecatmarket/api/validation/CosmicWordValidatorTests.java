package com.market.spacecatmarket.api.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CosmicWordValidatorTests {
    private final CosmicWordValidator validator = new CosmicWordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Star Yarn", "GALAXY milk", "collar (Comet)", "comet-powered toy"})
    void acceptsWholeCosmicWordsIgnoringCase(String name) {
        assertThat(validator.isValid(name, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ordinary yarn", "starfish", "galaxymilk", "comets"})
    void rejectsNamesWithoutAWholeCosmicWord(String name) {
        assertThat(validator.isValid(name, null)).isFalse();
    }

    @Test
    void leavesNullValidationToNotNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
