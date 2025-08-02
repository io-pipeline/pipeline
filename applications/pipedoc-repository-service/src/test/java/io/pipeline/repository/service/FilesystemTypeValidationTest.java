package io.pipeline.repository.service;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Duration;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.model.SemanticChunk;
import io.pipeline.data.model.SemanticProcessingResult;
import io.pipeline.data.model.ChunkEmbedding;
import io.pipeline.data.model.Blob;
import io.pipeline.data.model.Embedding;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.pipeline.repository.filesystem.*;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ServiceMetadata;
import io.pipeline.data.module.ProcessConfiguration;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests specifically focused on type validation and ensuring correct types are returned.
 */
@QuarkusTest
public class FilesystemTypeValidationTest extends IsolatedRedisTest {
    
    @GrpcClient("test-filesystem")
    MutinyFilesystemServiceGrpc.MutinyFilesystemServiceStub filesystemService;
    
    @Inject
    ProtobufTestDataHelper testDataHelper;
    
    @Inject
    GenericRepositoryService payloadRepository;
    
    @BeforeEach
    void setUp(TestInfo testInfo) {
        super.setupTestIsolation(testInfo);
    }
    
    private Node getNodeWithPayload(String nodeId) {
        return filesystemService.getNode(
                GetNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setId(nodeId)
                    .build()
            )
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
    }
    
    @Test
    void testPipeDocTypePreservation() {
        // Create a PipeDoc with all fields populated
        PipeDoc originalDoc = PipeDoc.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle("Test Document")
            .setBody("This is test content")
            .setCreationDate(Timestamp.newBuilder()
                .setSeconds(Instant.now().getEpochSecond())
                .build())
            .setLastModifiedDate(Timestamp.newBuilder()
                .setSeconds(Instant.now().getEpochSecond())
                .build())
            .addKeywords("test")
            .addKeywords("validation")
            .build();
        
        // Create node with PipeDoc payload
        CreateNodeRequest request = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("pipedoc_test.pb")
            .setType(Node.NodeType.FILE)
            .setPayload(Any.pack(originalDoc))
            .putMetadata("originalId", originalDoc.getId())
            .build();
        
        Node node = filesystemService.createNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify the node has correct type information
        assertThat(node.getPayloadType(), equalTo("type.googleapis.com/io.pipeline.search.model.PipeDoc"));
        assertThat(node.getSize(), equalTo((long) Any.pack(originalDoc).getSerializedSize()));
        assertThat(node.getMetadataMap().get("originalId"), equalTo(originalDoc.getId()));
        
        // Retrieve the full node with payload
        Node retrievedNode = filesystemService.getNode(
                GetNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setId(node.getId())
                    .build()
            )
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(retrievedNode.hasPayload(), is(true));
        Any retrievedPayload = retrievedNode.getPayload();
        assertThat(retrievedPayload.getTypeUrl(), equalTo("type.googleapis.com/io.pipeline.search.model.PipeDoc"));
        
        // Unpack and verify content
        PipeDoc retrievedDoc;
        try {
            retrievedDoc = retrievedPayload.unpack(PipeDoc.class);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        assertThat(retrievedDoc.getId(), equalTo(originalDoc.getId()));
        assertThat(retrievedDoc.getTitle(), equalTo(originalDoc.getTitle()));
        assertThat(retrievedDoc.getBody(), equalTo(originalDoc.getBody()));
        assertThat(retrievedDoc.getKeywordsList(), containsInAnyOrder("test", "validation"));
    }
    
    @Test
    void testPipeStreamTypePreservation() {
        // Create a PipeStream with embedded PipeDoc
        PipeDoc embeddedDoc = PipeDoc.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle("Embedded Document")
            .setBody("Content in stream")
            .build();
        
        PipeStream originalStream = PipeStream.newBuilder()
            .setStreamId(UUID.randomUUID().toString())
            .setDocument(embeddedDoc)
            .setCurrentPipelineName("test-pipeline")
            .setTargetStepName("test-step")
            .build();
        
        // Create node with PipeStream payload
        CreateNodeRequest request = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("pipestream_test.pb")
            .setType(Node.NodeType.FILE)
            .setPayload(Any.pack(originalStream))
            .build();
        
        Node node = filesystemService.createNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify type
        assertThat(node.getPayloadType(), equalTo("type.googleapis.com/io.pipeline.search.model.PipeStream"));
        
        // Retrieve the full node with payload
        Node retrievedNode = getNodeWithPayload(node.getId());
        assertThat(retrievedNode.hasPayload(), is(true));
        Any retrievedPayload = retrievedNode.getPayload();
        
        PipeStream retrievedStream;
        try {
            retrievedStream = retrievedPayload.unpack(PipeStream.class);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        assertThat(retrievedStream.getStreamId(), equalTo(originalStream.getStreamId()));
        assertThat(retrievedStream.getCurrentPipelineName(), equalTo("test-pipeline"));
        assertThat(retrievedStream.getDocument().getId(), equalTo(embeddedDoc.getId()));
    }
    
    @Test
    void testModuleProcessRequestType() {
        // Create ModuleProcessRequest
        PipeDoc doc = PipeDoc.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle("Test Doc")
            .build();
            
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
            .putConfigParams("param1", "value1")
            .putConfigParams("param2", "value2")
            .build();
            
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
            .setPipelineName("test-pipeline")
            .setPipeStepName("test-step")
            .setStreamId(UUID.randomUUID().toString())
            .build();
        
        ModuleProcessRequest originalRequest = ModuleProcessRequest.newBuilder()
            .setDocument(doc)
            .setConfig(config)
            .setMetadata(metadata)
            .build();
        
        CreateNodeRequest request = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("module_request.pb")
            .setType(Node.NodeType.FILE)
            .setPayload(Any.pack(originalRequest))
            .build();
        
        Node node = filesystemService.createNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify type
        assertThat(node.getPayloadType(), equalTo("type.googleapis.com/io.pipeline.search.model.ModuleProcessRequest"));
        
        // Retrieve the full node with payload
        Node retrievedNode = getNodeWithPayload(node.getId());
        assertThat(retrievedNode.hasPayload(), is(true));
        Any retrievedPayload = retrievedNode.getPayload();
        
        ModuleProcessRequest retrievedRequest;
        try {
            retrievedRequest = retrievedPayload.unpack(ModuleProcessRequest.class);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        assertThat(retrievedRequest.getDocument().getId(), equalTo(doc.getId()));
        assertThat(retrievedRequest.getConfig().getConfigParamsMap(), hasEntry("param1", "value1"));
        assertThat(retrievedRequest.getConfig().getConfigParamsMap(), hasEntry("param2", "value2"));
    }
    
    @Test
    void testGoogleProtobufTypes() {
        // Test various Google protobuf types
        Map<String, Any> testPayloads = new HashMap<>();
        
        // StringValue
        testPayloads.put("string_value.pb", Any.pack(StringValue.of("Hello, World!")));
        
        // Timestamp
        testPayloads.put("timestamp.pb", Any.pack(Timestamp.newBuilder()
            .setSeconds(1234567890)
            .setNanos(123456789)
            .build()));
        
        // Duration
        testPayloads.put("duration.pb", Any.pack(Duration.newBuilder()
            .setSeconds(3600)
            .setNanos(500000000)
            .build()));
        
        // Empty
        testPayloads.put("empty.pb", Any.pack(Empty.getDefaultInstance()));
        
        // Struct
        Struct.Builder structBuilder = Struct.newBuilder();
        structBuilder.putFields("name", Value.newBuilder().setStringValue("test").build());
        structBuilder.putFields("count", Value.newBuilder().setNumberValue(42).build());
        structBuilder.putFields("active", Value.newBuilder().setBoolValue(true).build());
        testPayloads.put("struct.pb", Any.pack(structBuilder.build()));
        
        // ListValue
        ListValue listValue = ListValue.newBuilder()
            .addValues(Value.newBuilder().setStringValue("item1").build())
            .addValues(Value.newBuilder().setNumberValue(2).build())
            .addValues(Value.newBuilder().setBoolValue(false).build())
            .build();
        testPayloads.put("list.pb", Any.pack(listValue));
        
        // Create nodes for each type
        Map<String, Node> createdNodes = new HashMap<>();
        for (Map.Entry<String, Any> entry : testPayloads.entrySet()) {
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName(entry.getKey())
                .setType(Node.NodeType.FILE)
                .setPayload(entry.getValue())
                .build();
            
            Node node = filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
            
            createdNodes.put(entry.getKey(), node);
        }
        
        // Verify types
        assertThat(createdNodes.get("string_value.pb").getPayloadType(),
            equalTo("type.googleapis.com/google.protobuf.StringValue"));
        assertThat(createdNodes.get("timestamp.pb").getPayloadType(),
            equalTo("type.googleapis.com/google.protobuf.Timestamp"));
        assertThat(createdNodes.get("duration.pb").getPayloadType(),
            equalTo("type.googleapis.com/google.protobuf.Duration"));
        assertThat(createdNodes.get("empty.pb").getPayloadType(),
            equalTo("type.googleapis.com/google.protobuf.Empty"));
        assertThat(createdNodes.get("struct.pb").getPayloadType(),
            equalTo("type.googleapis.com/google.protobuf.Struct"));
        assertThat(createdNodes.get("list.pb").getPayloadType(),
            equalTo("type.googleapis.com/google.protobuf.ListValue"));
        
        // Retrieve and validate each type
        Node stringValueNode = getNodeWithPayload(createdNodes.get("string_value.pb").getId());
        assertThat(stringValueNode.hasPayload(), is(true));
        Any retrievedStringValue = stringValueNode.getPayload();
        try {
            assertThat(retrievedStringValue.unpack(StringValue.class).getValue(), equalTo("Hello, World!"));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        
        Node timestampNode = getNodeWithPayload(createdNodes.get("timestamp.pb").getId());
        assertThat(timestampNode.hasPayload(), is(true));
        Any retrievedTimestamp = timestampNode.getPayload();
        Timestamp ts;
        try {
            ts = retrievedTimestamp.unpack(Timestamp.class);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        assertThat(ts.getSeconds(), equalTo(1234567890L));
        assertThat(ts.getNanos(), equalTo(123456789));
        
        Node structNode = getNodeWithPayload(createdNodes.get("struct.pb").getId());
        assertThat(structNode.hasPayload(), is(true));
        Any retrievedStruct = structNode.getPayload();
        Struct struct;
        try {
            struct = retrievedStruct.unpack(Struct.class);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        assertThat(struct.getFieldsMap().get("name").getStringValue(), equalTo("test"));
        assertThat(struct.getFieldsMap().get("count").getNumberValue(), equalTo(42.0));
        assertThat(struct.getFieldsMap().get("active").getBoolValue(), is(true));
    }
    
    @Test
    void testFormatByTypeAccuracy() {
        // Create a mix of different types
        Map<String, Integer> expectedCounts = new HashMap<>();
        
        // Create PipeDocs
        for (int i = 0; i < 5; i++) {
            PipeDoc doc = PipeDoc.newBuilder()
                .setId("doc-" + i)
                .setTitle("Document " + i)
                .build();
            
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("doc_" + i + ".pb")
                .setType(Node.NodeType.FILE)
                .setPayload(Any.pack(doc))
                .build();
            
            filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        }
        expectedCounts.put("type.googleapis.com/io.pipeline.search.model.PipeDoc", 5);
        
        // Create PipeStreams
        for (int i = 0; i < 3; i++) {
            PipeStream stream = PipeStream.newBuilder()
                .setStreamId("stream-" + i)
                .setDocument(PipeDoc.newBuilder().setId("doc-in-stream-" + i).build())
                .setCurrentPipelineName("pipeline-" + i)
                .setTargetStepName("step-" + i)
                .build();
            
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("stream_" + i + ".pb")
                .setType(Node.NodeType.FILE)
                .setPayload(Any.pack(stream))
                .build();
            
            filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        }
        expectedCounts.put("type.googleapis.com/io.pipeline.search.model.PipeStream", 3);
        
        // Create ModuleProcessRequests
        for (int i = 0; i < 7; i++) {
            ModuleProcessRequest mpr = ModuleProcessRequest.newBuilder()
                .setDocument(PipeDoc.newBuilder().setId("req-doc-" + i).build())
                .build();
            
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("request_" + i + ".pb")
                .setType(Node.NodeType.FILE)
                .setPayload(Any.pack(mpr))
                .build();
            
            filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        }
        expectedCounts.put("type.googleapis.com/io.pipeline.search.model.ModuleProcessRequest", 7);
        
        // Create StringValues
        for (int i = 0; i < 2; i++) {
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("string_" + i + ".pb")
                .setType(Node.NodeType.FILE)
                .setPayload(Any.pack(StringValue.of("value-" + i)))
                .build();
            
            filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        }
        expectedCounts.put("type.googleapis.com/google.protobuf.StringValue", 2);
        
        // Create folders (should not be counted in type counts)
        for (int i = 0; i < 4; i++) {
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("folder_" + i)
                .setType(Node.NodeType.FOLDER)
                .build();
            
            filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        }
        
        // Test dry run with full count
        FormatFilesystemResponse dryRunAll = filesystemService.formatFilesystem(
            FormatFilesystemRequest.newBuilder()
                .setDrive(getTestDrive())
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .setDryRun(true)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(dryRunAll.getNodesDeleted(), equalTo(17)); // 5+3+7+2
        assertThat(dryRunAll.getFoldersDeleted(), equalTo(4));
        assertThat(dryRunAll.getDeletedByTypeMap(), equalTo(expectedCounts));
        
        // Test filtering specific types
        FormatFilesystemResponse filterPipeDocs = filesystemService.formatFilesystem(
            FormatFilesystemRequest.newBuilder()
                .setDrive(getTestDrive())
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .addTypeUrls("type.googleapis.com/io.pipeline.search.model.PipeDoc")
                .setDryRun(true)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(filterPipeDocs.getNodesDeleted(), equalTo(5));
        assertThat(filterPipeDocs.getFoldersDeleted(), equalTo(0)); // No folders when filtering by type
        assertThat(filterPipeDocs.getDeletedByTypeMap().size(), equalTo(1));
        assertThat(filterPipeDocs.getDeletedByTypeMap(), hasEntry("type.googleapis.com/io.pipeline.search.model.PipeDoc", 5));
        
        // Test filtering multiple types
        FormatFilesystemResponse filterMultiple = filesystemService.formatFilesystem(
            FormatFilesystemRequest.newBuilder()
                .setDrive(getTestDrive())
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .addTypeUrls("type.googleapis.com/io.pipeline.search.model.PipeStream")
                .addTypeUrls("type.googleapis.com/google.protobuf.StringValue")
                .setDryRun(true)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(filterMultiple.getNodesDeleted(), equalTo(5)); // 3+2
        assertThat(filterMultiple.getDeletedByTypeMap().size(), equalTo(2));
        assertThat(filterMultiple.getDeletedByTypeMap(), hasEntry("type.googleapis.com/io.pipeline.search.model.PipeStream", 3));
        assertThat(filterMultiple.getDeletedByTypeMap(), hasEntry("type.googleapis.com/google.protobuf.StringValue", 2));
    }
    
    @Test
    void testComplexNestedTypes() {
        // Test with documents containing nested protobuf types
        Collection<PipeDoc> chunkerDocs = testDataHelper.getChunkerPipeDocuments();
        PipeDoc docWithChunks = chunkerDocs.stream()
            .filter(doc -> doc.getSemanticResultsCount() > 0)
            .findFirst()
            .orElse(null);
        
        if (docWithChunks != null) {
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("doc_with_chunks.pb")
                .setType(Node.NodeType.FILE)
                .setPayload(Any.pack(docWithChunks))
                .build();
            
            Node node = filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
            
            // Verify type is PipeDoc, not the nested chunk type
            assertThat(node.getPayloadType(), equalTo("type.googleapis.com/io.pipeline.search.model.PipeDoc"));
            
            // Retrieve the full node with payload
            Node retrievedNode = getNodeWithPayload(node.getId());
            assertThat(retrievedNode.hasPayload(), is(true));
            Any retrievedPayload = retrievedNode.getPayload();
            
            PipeDoc retrievedDoc;
        try {
            retrievedDoc = retrievedPayload.unpack(PipeDoc.class);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
            assertThat(retrievedDoc.getSemanticResultsCount(), equalTo(docWithChunks.getSemanticResultsCount()));
            
            // Verify chunk content
            if (retrievedDoc.getSemanticResultsCount() > 0) {
                SemanticProcessingResult result = retrievedDoc.getSemanticResults(0);
                for (int i = 0; i < result.getChunksCount(); i++) {
                    SemanticChunk originalChunk = docWithChunks.getSemanticResults(0).getChunks(i);
                    SemanticChunk retrievedChunk = result.getChunks(i);
                    
                    assertThat(retrievedChunk.getChunkNumber(), equalTo(originalChunk.getChunkNumber()));
                    assertThat(retrievedChunk.getEmbeddingInfo().getTextContent(), 
                        equalTo(originalChunk.getEmbeddingInfo().getTextContent()));
                }
            }
        }
    }
    
    @Test
    void testBlobType() {
        // Create a PipeDoc with Blob
        Blob blob = Blob.newBuilder()
            .setBlobId(UUID.randomUUID().toString())
            .setData(ByteString.copyFromUtf8("Test binary data"))
            .setMimeType("text/plain")
            .setFilename("test.txt")
            .build();
            
        PipeDoc docWithBlob = PipeDoc.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle("Document with Blob")
            .setBlob(blob)
            .build();
        
        CreateNodeRequest request = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("doc_with_blob.pb")
            .setType(Node.NodeType.FILE)
            .setPayload(Any.pack(docWithBlob))
            .build();
        
        Node node = filesystemService.createNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify type
        assertThat(node.getPayloadType(), equalTo("type.googleapis.com/io.pipeline.search.model.PipeDoc"));
        
        // Retrieve and verify blob is preserved
        Node retrievedNode = getNodeWithPayload(node.getId());
        assertThat(retrievedNode.hasPayload(), is(true));
        Any retrievedPayload = retrievedNode.getPayload();
        
        PipeDoc retrievedDoc;
        try {
            retrievedDoc = retrievedPayload.unpack(PipeDoc.class);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        assertThat(retrievedDoc.hasBlob(), is(true));
        assertThat(retrievedDoc.getBlob().getBlobId(), equalTo(blob.getBlobId()));
        assertThat(retrievedDoc.getBlob().getData().toStringUtf8(), equalTo("Test binary data"));
        assertThat(retrievedDoc.getBlob().getMimeType(), equalTo("text/plain"));
    }
    
    @Test
    void testTypeCountAccuracyInChildren() {
        // Create folder structure with different types in each folder
        Map<String, String> folderToType = new HashMap<>();
        
        // Create folders
        for (String typeName : Arrays.asList("pipedocs", "pipestreams", "requests")) {
            CreateNodeRequest folderRequest = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName(typeName)
                .setType(Node.NodeType.FOLDER)
                .build();
            
            Node folder = filesystemService.createNode(folderRequest)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
            
            folderToType.put(folder.getId(), typeName);
            
            // Add type-specific files
            switch (typeName) {
                case "pipedocs":
                    for (int i = 0; i < 3; i++) {
                        PipeDoc doc = PipeDoc.newBuilder()
                            .setId("folder-doc-" + i)
                            .setTitle("Folder Doc " + i)
                            .build();
                        
                        CreateNodeRequest fileRequest = CreateNodeRequest.newBuilder()
                            .setDrive(getTestDrive())
                            .setName("doc_" + i + ".pb")
                            .setType(Node.NodeType.FILE)
                            .setParentId(folder.getId())
                            .setPayload(Any.pack(doc))
                            .build();
                        
                        filesystemService.createNode(fileRequest)
                            .subscribe().withSubscriber(UniAssertSubscriber.create())
                            .awaitItem();
                    }
                    break;
                    
                case "pipestreams":
                    for (int i = 0; i < 2; i++) {
                        PipeStream stream = PipeStream.newBuilder()
                            .setStreamId("folder-stream-" + i)
                            .setDocument(PipeDoc.newBuilder().setId("stream-doc-" + i).build())
                            .setCurrentPipelineName("test-pipeline")
                            .setTargetStepName("test-step")
                            .build();
                        
                        CreateNodeRequest fileRequest = CreateNodeRequest.newBuilder()
                            .setDrive(getTestDrive())
                            .setName("stream_" + i + ".pb")
                            .setType(Node.NodeType.FILE)
                            .setParentId(folder.getId())
                            .setPayload(Any.pack(stream))
                            .build();
                        
                        filesystemService.createNode(fileRequest)
                            .subscribe().withSubscriber(UniAssertSubscriber.create())
                            .awaitItem();
                    }
                    break;
                    
                case "requests":
                    for (int i = 0; i < 4; i++) {
                        ModuleProcessRequest mpr = ModuleProcessRequest.newBuilder()
                            .setDocument(PipeDoc.newBuilder().setId("req-doc-" + i).build())
                            .build();
                        
                        CreateNodeRequest fileRequest = CreateNodeRequest.newBuilder()
                            .setDrive(getTestDrive())
                            .setName("request_" + i + ".pb")
                            .setType(Node.NodeType.FILE)
                            .setParentId(folder.getId())
                            .setPayload(Any.pack(mpr))
                            .build();
                        
                        filesystemService.createNode(fileRequest)
                            .subscribe().withSubscriber(UniAssertSubscriber.create())
                            .awaitItem();
                    }
                    break;
            }
        }
        
        // Get children of root and verify types
        GetChildrenResponse rootChildren = filesystemService.getChildren(
            GetChildrenRequest.newBuilder()
                .setDrive(getTestDrive())
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(rootChildren.getNodesCount(), equalTo(3));
        assertThat(rootChildren.getNodesList().stream()
            .allMatch(node -> node.getType() == Node.NodeType.FOLDER), is(true));
        
        // Verify type counts in format operation
        FormatFilesystemResponse formatResponse = filesystemService.formatFilesystem(
            FormatFilesystemRequest.newBuilder()
                .setDrive(getTestDrive())
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .setDryRun(true)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(formatResponse.getNodesDeleted(), equalTo(9)); // 3+2+4
        assertThat(formatResponse.getFoldersDeleted(), equalTo(3));
        
        Map<String, Integer> typeMap = formatResponse.getDeletedByTypeMap();
        assertThat(typeMap.size(), equalTo(3));
        assertThat(typeMap, hasEntry("type.googleapis.com/io.pipeline.search.model.PipeDoc", 3));
        assertThat(typeMap, hasEntry("type.googleapis.com/io.pipeline.search.model.PipeStream", 2));
        assertThat(typeMap, hasEntry("type.googleapis.com/io.pipeline.search.model.ModuleProcessRequest", 4));
    }
}