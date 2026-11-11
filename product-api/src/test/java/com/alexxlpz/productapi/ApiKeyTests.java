package com.alexxlpz.productapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiKeyTests {

    @Test
    void withoutALongKeyItDoesNotStart() {
        assertThrows(IllegalStateException.class, () -> new ApiKeyFilter(""));
        assertThrows(IllegalStateException.class, () -> new ApiKeyFilter("corta"));
    }
}
