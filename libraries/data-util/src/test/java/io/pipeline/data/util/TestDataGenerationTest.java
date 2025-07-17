package io.pipeline.data.util;

import io.pipeline.data.model.ActionType;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.util.proto.TestDataBuffer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test demonstrates how to properly load protobuf classes in Quarkus.
 * It uses reflection to load the classes, which is required due to Quarkus's
 * strict classloading mechanism.
 * 
 * It also tests the TestDataBuffer class, which is a replacement for ProcessingBuffer
 * from commons:util to avoid circular dependencies.
 */
@QuarkusTest
public class TestDataGenerationTest {
    private static final Logger logger = LoggerFactory.getLogger(TestDataGenerationTest.class);

    @Inject
    @Named("outputBuffer")
    TestDataBuffer<PipeDoc> outputBuffer;

    @Inject
    @Named("inputBuffer")
    TestDataBuffer<PipeStream> inputBuffer;

    @Test
    void testProtobufClassLoading() {
        try {
            // !!!! CRITICAL - DO NOT DELETE THIS FORCE LOADING !!!!
            // Force load the classes using the current thread's context classloader
            // This is REQUIRED due to Quarkus classloading timing issues in tests
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> pipeDocClass = cl.loadClass("io.pipeline.data.model.PipeDoc");
            Class<?> pipeStreamClass = cl.loadClass("io.pipeline.data.model.PipeStream");

            // Verify that the classes were loaded successfully
            assertNotNull(pipeDocClass);
            assertNotNull(pipeStreamClass);

            // Log success
            logger.info("Successfully loaded PipeDoc and PipeStream classes using reflection");

            // This test will pass if the classes are loaded successfully
        } catch (ClassNotFoundException e) {
            logger.error("Failed to load protobuf classes", e);
            throw new RuntimeException("Failed to load protobuf classes", e);
        }
    }

    @Test
    void testDataBuffers() {
        // Verify that the buffers were injected successfully
        assertNotNull(outputBuffer);
        assertNotNull(inputBuffer);

        // Verify that the buffers are empty (since we haven't added anything)
        assertEquals(0, outputBuffer.size());
        assertEquals(0, inputBuffer.size());

        logger.info("Successfully injected and verified TestDataBuffer instances");
    }

    @Test
    void testProtobufCreationAndUsage() {
        // Create a sample PipeDoc
        PipeDoc doc = PipeDoc.newBuilder()
                .setId("test-doc-1")
                .setTitle("Test Document")
                .setBody("This is a test document body")
                .setSourceUri("http://example.com/doc1")
                .build();

        // Verify the protobuf was created correctly
        assertNotNull(doc);
        assertEquals("test-doc-1", doc.getId());
        assertEquals("Test Document", doc.getTitle());
        assertEquals("This is a test document body", doc.getBody());
        assertEquals("http://example.com/doc1", doc.getSourceUri());

        // Add it to the output buffer
        outputBuffer.add(doc);
        assertEquals(1, outputBuffer.size());

        // Create a sample PipeStream
        PipeStream stream = PipeStream.newBuilder()
                .setStreamId("test-stream-1")
                .setDocument(doc)
                .setCurrentPipelineName("test-pipeline")
                .setTargetStepName("test-step")
                .setCurrentHopNumber(1)
                .setActionType(ActionType.CREATE)
                .build();

        // Verify the protobuf was created correctly
        assertNotNull(stream);
        assertEquals("test-stream-1", stream.getStreamId());
        assertEquals(doc, stream.getDocument());
        assertEquals("test-pipeline", stream.getCurrentPipelineName());
        assertEquals("test-step", stream.getTargetStepName());
        assertEquals(1, stream.getCurrentHopNumber());
        assertEquals(ActionType.CREATE, stream.getActionType());

        // Add it to the input buffer
        inputBuffer.add(stream);
        assertEquals(1, inputBuffer.size());

        // Verify we can get snapshots
        var docSnapshot = outputBuffer.snapshot();
        assertEquals(1, docSnapshot.size());
        assertEquals("test-doc-1", docSnapshot.get(0).getId());

        var streamSnapshot = inputBuffer.snapshot();
        assertEquals(1, streamSnapshot.size());
        assertEquals("test-stream-1", streamSnapshot.get(0).getStreamId());

        logger.info("Successfully created and used PipeDoc and PipeStream protobuf objects");
    }
}
