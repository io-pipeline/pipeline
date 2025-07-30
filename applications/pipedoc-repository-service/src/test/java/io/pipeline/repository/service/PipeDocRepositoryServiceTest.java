package io.pipeline.repository.service;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.repository.v1.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class PipeDocRepositoryServiceTest {

    @Inject
    PipeDocRepositoryServiceImpl repositoryService;

    @Test
    public void testServiceInjection() {
        assertNotNull(repositoryService, "Repository service should be injected");
    }

    @Test
    public void testBasicOperations() {
        // This test just verifies the service starts up correctly
        // Real testing will be done via the Node.js dev tools
        assertTrue(true, "Service initialized successfully");
    }
}