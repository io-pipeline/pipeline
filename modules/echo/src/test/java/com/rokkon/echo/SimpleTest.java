package com.rokkon.echo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to verify basic test execution works.
 */
class SimpleTest {
    
    @Test
    void testSimple() {
        assertThat(1 + 1).isEqualTo(2);
    }
}