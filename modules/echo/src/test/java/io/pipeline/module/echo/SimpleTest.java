package io.pipeline.module.echo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;

/**
 * Simple test to verify basic test execution works.
 */
class SimpleTest {
    
    @Test
    void testSimple() {
        assertThat("Basic arithmetic should work", 1 + 1, is(equalTo(2)));
    }
}