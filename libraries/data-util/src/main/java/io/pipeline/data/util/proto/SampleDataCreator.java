package io.pipeline.data.util.proto;

import com.google.protobuf.Timestamp;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.module.ModuleProcessResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

/**
 * Creates sample data for testing purposes.
 */
@ApplicationScoped
public class SampleDataCreator {

    /**
     * Default constructor for SampleDataCreator.
     * This class only contains static utility methods, so the constructor is not used.
     */
    public SampleDataCreator() {
        // CDI fun
    }

    /**
     * Creates a sample PipeDoc with test content.
     * 
     * @param id the unique identifier for the document
     * @param title the title of the document
     * @param body the body content of the document
     * @return a new PipeDoc instance with the specified content
     */
    public PipeDoc createSamplePipeDoc(String id, String title, String body) {
        Instant now = Instant.now();

        return PipeDoc.newBuilder()
                .setId(id)
                .setTitle(title)
                .setBody(body)
                .setDocumentType("sample")
                .setSourceUri("sample://test-document/" + id)
                .setSourceMimeType("text/plain")
                .setCreationDate(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build())
                .setProcessedDate(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build())
                .putMetadata("source", "SampleDataCreator")
                .putMetadata("version", "1.0")
                .addKeywords("sample")
                .addKeywords("test")
                .addKeywords("echo")
                .build();
    }

    /**
     * Creates a default sample PipeDoc.
     * 
     * @return a new PipeDoc instance with default sample content
     */
    public PipeDoc createDefaultSamplePipeDoc() {
        return createSamplePipeDoc(
                "sample-001",
                "Sample Document for Echo Testing",
                "This is a sample document created for testing the Echo module. " +
                "It contains multiple paragraphs to demonstrate how the echo service processes text content.\n\n" +
                "The Echo module is designed to receive a document and return it unchanged. " +
                "This allows us to test the gRPC communication and ensure that documents " +
                "are properly serialized and deserialized through the pipeline.\n\n" +
                "This document includes:\n" +
                "- A meaningful title\n" +
                "- Body content with multiple paragraphs\n" +
                "- Metadata fields\n" +
                "- Keywords for searchability\n\n" +
                "The echo service should return this exact content without any modifications."
        );
    }

    /**
     * Creates a sample PipeStream containing a document.
     * 
     * @param streamId the unique identifier for the stream
     * @param document the PipeDoc to include in the stream
     * @return a new PipeStream instance containing the specified document
     */
    public PipeStream createSamplePipeStream(String streamId, PipeDoc document) {
        return PipeStream.newBuilder()
                .setStreamId(streamId)
                .setDocument(document)
                .setCurrentPipelineName("test-pipeline")
                .setTargetStepName("echo")
                .setCurrentHopNumber(1)
                .putContextParams("test", "true")
                .putContextParams("source", "SampleDataCreator")
                .build();
    }

    /**
     * Creates a sample ProcessResponse containing a document.
     * 
     * @param document the PipeDoc to include in the response, can be null
     * @param success whether the processing was successful
     * @return a new ProcessResponse instance with the specified document and success status
     */
    public ModuleProcessResponse createSampleProcessResponse(PipeDoc document, boolean success) {
        ModuleProcessResponse.Builder builder = ModuleProcessResponse.newBuilder()
                .setSuccess(success);

        if (document != null) {
            builder.setOutputDoc(document);
        }

        if (success) {
            builder.addProcessorLogs("Sample document processed successfully");
        } else {
            builder.addProcessorLogs("Sample processing failed");
        }

        return builder.build();
    }
}
