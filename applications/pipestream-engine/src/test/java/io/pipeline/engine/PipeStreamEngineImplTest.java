package io.pipeline.engine;

import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.model.StepType;
import io.pipeline.api.service.PipelineConfigService;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.module.MutinyPipeStepProcessorGrpc;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ModuleProcessResponse;
import io.pipeline.dynamic.grpc.client.DynamicGrpcClientFactory;
import io.pipeline.stream.engine.PipeStreamEngine;
import io.pipeline.stream.engine.PipeStreamResponse;
import io.pipeline.stream.engine.ProcessStatus;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PipeStreamEngineImpl using proper Quarkus patterns.
 * Tests the gRPC service methods with real injection.
 */
@QuarkusTest
@TestProfile(NoSchedulerTestProfile.class)
@DisplayName("PipeStreamEngine Unit Tests")
class PipeStreamEngineImplTest {

    @Inject
    @GrpcService
    PipeStreamEngine pipeStreamEngine;

    @InjectMock
    PipelineConfigService pipelineConfigService;

    @InjectMock
    DynamicGrpcClientFactory dynamicGrpcClientFactory;

    @BeforeEach
    void setUp() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo("test-service");
        PipelineStepConfig mockStep = new PipelineStepConfig("test-step", StepType.PIPELINE, processorInfo);
        Mockito.when(pipelineConfigService.getPipeStep(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Uni.createFrom().item(Optional.of(mockStep)));

        MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub mockStub = Mockito.mock(MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub.class);
        Mockito.when(dynamicGrpcClientFactory.getMutinyClientForService(Mockito.anyString()))
                .thenReturn(Uni.createFrom().item(mockStub));

        ModuleProcessResponse mockResponse = ModuleProcessResponse.newBuilder().setSuccess(true).build();
        Mockito.when(mockStub.processData(Mockito.any(ModuleProcessRequest.class))).thenReturn(Uni.createFrom().item(mockResponse));
    }

    @Test
    @DisplayName("Should increment hop count in testPipeStream")
    void shouldIncrementHopCountInTestPipeStream() {
        // Given
        PipeStream stream = PipeStream.newBuilder()
                .setStreamId("test-stream-1")
                .setCurrentPipelineName("test-pipeline")
                .setTargetStepName("test-step")
                .setCurrentHopNumber(5)
                .putContextParams("cluster", "test-cluster")
                .build();

        // When
        Uni<PipeStream> result = pipeStreamEngine.testPipeStream(stream);

        // Then
        PipeStream response = result.await().indefinitely();
        assertEquals("test-stream-1", response.getStreamId());
        assertEquals("test-pipeline", response.getCurrentPipelineName());
        assertEquals("test-step", response.getTargetStepName());
        assertEquals(6, response.getCurrentHopNumber()); // Incremented
        assertEquals("test-cluster", response.getContextParamsMap().get("cluster"));
    }

    @Test
    @DisplayName("Should return accepted status in processPipeAsync")
    void shouldReturnAcceptedStatusInProcessPipeAsync() {
        // Given
        PipeStream stream = PipeStream.newBuilder()
                .setStreamId("test-stream-2")
                .setCurrentPipelineName("test-pipeline")
                .setTargetStepName("test-step")
                .setCurrentHopNumber(0)
                .putContextParams("cluster", "test-cluster")
                .build();

        // When
        Uni<PipeStreamResponse> result = pipeStreamEngine.processPipeAsync(stream);

        // Then
        PipeStreamResponse response = result.await().indefinitely();
        assertEquals("test-stream-2", response.getStreamId());
        assertEquals(ProcessStatus.ACCEPTED, response.getStatus());
        assertEquals("Pipeline processing completed successfully", response.getMessage());
        assertNotNull(response.getRequestId());
        assertTrue(response.getTimestamp() > 0);
    }

    @Test
    @DisplayName("Should handle empty stream ID gracefully")
    void shouldHandleEmptyStreamIdGracefully() {
        // Given
        PipeStream stream = PipeStream.newBuilder()
                .setStreamId("")
                .setCurrentPipelineName("test-pipeline")
                .setTargetStepName("test-step")
                .setCurrentHopNumber(0)
                .putContextParams("cluster", "test-cluster")
                .build();

        // When
        Uni<PipeStream> result = pipeStreamEngine.testPipeStream(stream);

        // Then
        PipeStream response = result.await().indefinitely();
        assertEquals("", response.getStreamId());
        assertEquals(1, response.getCurrentHopNumber());
    }
}