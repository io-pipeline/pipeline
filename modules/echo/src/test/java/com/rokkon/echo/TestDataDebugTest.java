package com.rokkon.echo;

import com.rokkon.search.util.ProtobufTestDataHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TestDataDebugTest {

    @Inject
    ProtobufTestDataHelper helper;

    @Test
    void debugTestDataLoading() {
        System.out.println("=== Debugging Test Data Loading ===");

        // Try to load with ProtobufTestDataHelper
        System.out.println("\nTrying to load data with ProtobufTestDataHelper:");
        System.out.println("Tika documents: " + helper.getTikaPipeDocuments().size());
        System.out.println("Sample documents: " + helper.getSamplePipeDocuments().size());
        System.out.println("Chunker documents: " + helper.getChunkerPipeDocuments().size());
    }
}
