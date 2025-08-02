package io.pipeline.repository.service;

import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.model.SemanticChunk;
import io.pipeline.data.model.SemanticProcessingResult;
import io.pipeline.data.model.ChunkEmbedding;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.pipeline.repository.filesystem.*;
import io.pipeline.data.module.ModuleProcessRequest;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for FilesystemService using various protobuf types from ProtobufTestDataHelper.
 */
@QuarkusTest
public class FilesystemProtobufTest extends IsolatedRedisTest {
    
    @GrpcClient("test-filesystem")
    MutinyFilesystemServiceGrpc.MutinyFilesystemServiceStub filesystemService;
    
    @Inject
    ProtobufTestDataHelper testDataHelper;
    
    @BeforeEach
    void setUp(TestInfo testInfo) {
        super.setupTestIsolation(testInfo);
    }
    
    @Test
    void testCreateNodesWithPipeDocPayloads() {
        // Get sample PipeDocs
        Collection<PipeDoc> pipeDocs = testDataHelper.getSamplePipeDocuments();
        assertThat(pipeDocs.isEmpty(), is(false));
        
        // Create a folder to hold the documents
        CreateNodeRequest folderRequest = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("PipeDoc Collection")
            .setType(Node.NodeType.FOLDER)
            .build();
        
        Node folder = filesystemService.createNode(folderRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertNotNull(folder);
        
        // Create nodes for each PipeDoc
        List<Node> createdNodes = new ArrayList<>();
        int count = 0;
        for (PipeDoc doc : pipeDocs) {
            if (count++ >= 5) break; // Limit to 5 for testing
            
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("Document_" + doc.getId())
                .setType(Node.NodeType.FILE)
                .setParentId(folder.getId())
                .setPayload(Any.pack(doc))
                .putMetadata("docId", doc.getId())
                .putMetadata("title", doc.hasTitle() ? doc.getTitle() : "Untitled")
                .build();
            
            Node node = filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
            
            assertNotNull(node);
            assertThat(node.getPayloadType(), equalTo("type.googleapis.com/io.pipeline.search.model.PipeDoc"));
            assertThat(node.getSize(), greaterThan(0L));
            createdNodes.add(node);
        }
        
        // Verify we can get children
        GetChildrenResponse children = filesystemService.getChildren(
            GetChildrenRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(folder.getId())
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(children.getNodesCount(), equalTo(5));
    }
    
    @Test
    void testCreateNodesWithPipeStreamPayloads() {
        // Get sample PipeStreams
        Collection<PipeStream> pipeStreams = testDataHelper.getTikaPipeStreams();
        assertThat(pipeStreams.isEmpty(), is(false));
        int expectedNodes = Math.min(3, pipeStreams.size());
        
        // Create a folder for streams
        CreateNodeRequest folderRequest = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("PipeStream Collection")
            .setType(Node.NodeType.FOLDER)
            .build();
        
        Node folder = filesystemService.createNode(folderRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Create nodes for PipeStreams
        List<Node> createdNodes = new ArrayList<>();
        int count = 0;
        for (PipeStream stream : pipeStreams) {
            if (count++ >= 3) break;
            
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("Stream_" + stream.getStreamId())
                .setType(Node.NodeType.FILE)
                .setParentId(folder.getId())
                .setPayload(Any.pack(stream))
                .putMetadata("streamId", stream.getStreamId())
                .build();
            
            Node node = filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
            
            assertNotNull(node);
            assertThat(node.getPayloadType(), equalTo("type.googleapis.com/io.pipeline.search.model.PipeStream"));
            createdNodes.add(node);
        }
        
        assertThat(createdNodes.size(), equalTo(expectedNodes));
    }
    
    @Test
    void testCreateNodesWithChunkerData() {
        // Get chunker output documents
        Collection<PipeDoc> chunkerDocs = testDataHelper.getChunkerPipeDocuments();
        assertThat(chunkerDocs.isEmpty(), is(false));
        
        // Create folder structure
        CreateNodeRequest rootFolder = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("Chunker Output")
            .setType(Node.NodeType.FOLDER)
            .build();
        
        Node root = filesystemService.createNode(rootFolder)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Process chunker documents
        int docCount = 0;
        for (PipeDoc doc : chunkerDocs) {
            if (docCount++ >= 3) break;
            
            // Create a folder for each document
            CreateNodeRequest docFolder = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("Doc_" + doc.getId())
                .setType(Node.NodeType.FOLDER)
                .setParentId(root.getId())
                .build();
            
            Node docNode = filesystemService.createNode(docFolder)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
            
            // Store the document
            CreateNodeRequest docFile = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("document.pb")
                .setType(Node.NodeType.FILE)
                .setParentId(docNode.getId())
                .setPayload(Any.pack(doc))
                .build();
            
            filesystemService.createNode(docFile)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
            
            // Store chunks if available
            if (doc.getSemanticResultsCount() > 0) {
                SemanticProcessingResult result = doc.getSemanticResults(0);
                if (result.getChunksCount() > 0) {
                    CreateNodeRequest chunksFolder = CreateNodeRequest.newBuilder()
                        .setDrive(getTestDrive())
                        .setName("chunks")
                        .setType(Node.NodeType.FOLDER)
                        .setParentId(docNode.getId())
                        .build();
                    
                    Node chunksNode = filesystemService.createNode(chunksFolder)
                        .subscribe().withSubscriber(UniAssertSubscriber.create())
                        .awaitItem()
                        .getItem();
                    
                    // Store individual chunks
                    int chunkCount = 0;
                    for (SemanticChunk chunk : result.getChunksList()) {
                        if (chunkCount++ >= 5) break;
                        
                        CreateNodeRequest chunkFile = CreateNodeRequest.newBuilder()
                            .setDrive(getTestDrive())
                            .setName("chunk_" + chunk.getChunkNumber() + ".pb")
                            .setType(Node.NodeType.FILE)
                            .setParentId(chunksNode.getId())
                            .setPayload(Any.pack(chunk))
                            .putMetadata("chunkNumber", String.valueOf(chunk.getChunkNumber()))
                            .putMetadata("textLength", String.valueOf(chunk.getEmbeddingInfo().getTextContent().length()))
                            .build();
                        
                        filesystemService.createNode(chunkFile)
                            .subscribe().withSubscriber(UniAssertSubscriber.create())
                            .awaitItem();
                    }
                }
            }
        }
        
        // Test tree retrieval (when implemented)
        // For now, verify folder structure via getChildren
        GetChildrenResponse rootChildren = filesystemService.getChildren(
            GetChildrenRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(root.getId())
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(rootChildren.getNodesCount(), equalTo(3));
    }
    
    @Test
    void testFormatWithMixedProtobufTypes() {
        // Create a complex filesystem with various protobuf types
        Map<String, Integer> typeCount = new HashMap<>();
        
        // Add PipeDocs
        Collection<PipeDoc> pipeDocs = testDataHelper.getSamplePipeDocuments();
        int pipeDocCount = 0;
        for (PipeDoc doc : pipeDocs) {
            if (pipeDocCount++ >= 3) break;
            
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("doc_" + doc.getId() + ".pb")
                .setType(Node.NodeType.FILE)
                .setPayload(Any.pack(doc))
                .build();
            
            filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        }
        typeCount.put("type.googleapis.com/io.pipeline.search.model.PipeDoc", 3);
        
        // Add PipeStreams
        Collection<PipeStream> pipeStreams = testDataHelper.getTikaPipeStreams();
        int pipeStreamCount = 0;
        for (PipeStream stream : pipeStreams) {
            if (pipeStreamCount++ >= 2) break;
            
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("stream_" + stream.getStreamId() + ".pb")
                .setType(Node.NodeType.FILE)
                .setPayload(Any.pack(stream))
                .build();
            
            filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        }
        typeCount.put("type.googleapis.com/io.pipeline.search.model.PipeStream", 2);
        
        // Add ModuleProcessRequests
        for (int i = 0; i < 4; i++) {
            ModuleProcessRequest mpr = ModuleProcessRequest.newBuilder()
                .setDocument(PipeDoc.newBuilder().setId(UUID.randomUUID().toString()).build())
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
        typeCount.put("type.googleapis.com/io.pipeline.search.model.ModuleProcessRequest", 4);
        
        // Add some folders
        for (int i = 0; i < 2; i++) {
            CreateNodeRequest folder = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("folder_" + i)
                .setType(Node.NodeType.FOLDER)
                .build();
            
            filesystemService.createNode(folder)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        }
        
        // Test dry run first
        FormatFilesystemResponse dryRunResponse = filesystemService.formatFilesystem(
            FormatFilesystemRequest.newBuilder()
                .setDrive(getTestDrive())
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .setDryRun(true)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(dryRunResponse.getNodesDeleted(), equalTo(9)); // Total files
        assertThat(dryRunResponse.getFoldersDeleted(), equalTo(2)); // Total folders
        assertThat(dryRunResponse.getDeletedByTypeMap(), equalTo(typeCount));
        
        // Test filtering by type - delete only PipeDoc files
        FormatFilesystemResponse filteredResponse = filesystemService.formatFilesystem(
            FormatFilesystemRequest.newBuilder()
                .setDrive(getTestDrive())
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .addTypeUrls("type.googleapis.com/io.pipeline.search.model.PipeDoc")
                .setDryRun(false)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(filteredResponse.getNodesDeleted(), equalTo(3));
        assertThat(filteredResponse.getFoldersDeleted(), equalTo(0)); // Folders not deleted with type filter
        assertThat(filteredResponse.getDeletedByTypeMap(), hasEntry(
            "type.googleapis.com/io.pipeline.search.model.PipeDoc", 3
        ));
        
        // Verify remaining nodes
        GetChildrenResponse remaining = filesystemService.getChildren(
            GetChildrenRequest.newBuilder()
                .setDrive(getTestDrive())
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Should have 2 PipeStreams, 4 ModuleProcessRequests, and 2 folders
        assertThat(remaining.getNodesCount(), equalTo(8));
    }
    
    @Test
    void testLargeScaleOperations() {
        // Test with larger datasets from ProtobufTestDataHelper
        Collection<PipeDoc> allDocs = testDataHelper.getPipeDocuments();
        Collection<PipeStream> allStreams = testDataHelper.getPipeStreams();
        
        // Create folder structure
        CreateNodeRequest docsFolder = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("All Documents")
            .setType(Node.NodeType.FOLDER)
            .build();
        
        Node docsFolderNode = filesystemService.createNode(docsFolder)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        CreateNodeRequest streamsFolder = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("All Streams")
            .setType(Node.NodeType.FOLDER)
            .build();
        
        Node streamsFolderNode = filesystemService.createNode(streamsFolder)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Add documents (limit to prevent test timeout)
        AtomicInteger docCount = new AtomicInteger(0);
        allDocs.stream().limit(10).forEach(doc -> {
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("doc_" + docCount.getAndIncrement() + ".pb")
                .setType(Node.NodeType.FILE)
                .setParentId(docsFolderNode.getId())
                .setPayload(Any.pack(doc))
                .build();
            
            filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        });
        
        // Add streams
        AtomicInteger streamCount = new AtomicInteger(0);
        allStreams.stream().limit(10).forEach(stream -> {
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("stream_" + streamCount.getAndIncrement() + ".pb")
                .setType(Node.NodeType.FILE)
                .setParentId(streamsFolderNode.getId())
                .setPayload(Any.pack(stream))
                .build();
            
            filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        });
        
        // Test recursive deletion
        DeleteNodeResponse deleteResponse = filesystemService.deleteNode(
            DeleteNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setId(docsFolderNode.getId())
                .setRecursive(true)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(deleteResponse.getSuccess(), is(true));
        assertThat(deleteResponse.getDeletedCount(), equalTo(11)); // 10 docs + 1 folder
        
        // Verify only streams folder remains
        GetChildrenResponse roots = filesystemService.getChildren(
            GetChildrenRequest.newBuilder()
                .setDrive(getTestDrive())
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(roots.getNodesCount(), equalTo(1));
        assertThat(roots.getNodes(0).getName(), equalTo("All Streams"));
    }
    
    @Test
    void testEmbedderDataStorage() {
        // Test with embedder input/output documents
        Collection<PipeDoc> embedderInput = testDataHelper.getEmbedderInputDocuments();
        Collection<PipeDoc> embedderOutput = testDataHelper.getEmbedderOutputDocuments();
        
        if (embedderInput.isEmpty() || embedderOutput.isEmpty()) {
            // Skip if no embedder data available
            return;
        }
        
        // Create folder structure
        CreateNodeRequest embeddingFolder = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("Embeddings")
            .setType(Node.NodeType.FOLDER)
            .build();
        
        Node embeddingNode = filesystemService.createNode(embeddingFolder)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Store input and output pairs
        Iterator<PipeDoc> inputIter = embedderInput.iterator();
        Iterator<PipeDoc> outputIter = embedderOutput.iterator();
        
        int pairCount = 0;
        while (inputIter.hasNext() && outputIter.hasNext() && pairCount < 5) {
            PipeDoc input = inputIter.next();
            PipeDoc output = outputIter.next();
            
            // Create folder for this pair
            CreateNodeRequest pairFolder = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("embedding_pair_" + pairCount)
                .setType(Node.NodeType.FOLDER)
                .setParentId(embeddingNode.getId())
                .build();
            
            Node pairNode = filesystemService.createNode(pairFolder)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
            
            // Store input
            CreateNodeRequest inputFile = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("input.pb")
                .setType(Node.NodeType.FILE)
                .setParentId(pairNode.getId())
                .setPayload(Any.pack(input))
                .putMetadata("stage", "input")
                .build();
            
            filesystemService.createNode(inputFile)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
            
            // Store output
            CreateNodeRequest outputFile = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("output.pb")
                .setType(Node.NodeType.FILE)
                .setParentId(pairNode.getId())
                .setPayload(Any.pack(output))
                .putMetadata("stage", "output")
                .build();
            
            filesystemService.createNode(outputFile)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
            
            pairCount++;
        }
        
        // Verify structure
        GetChildrenResponse pairs = filesystemService.getChildren(
            GetChildrenRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(embeddingNode.getId())
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(pairs.getNodesCount(), equalTo(pairCount));
    }
    
    @Test
    void testUpdateNodeWithComplexMetadata() {
        // Create a node with initial metadata
        PipeDoc doc = testDataHelper.getSamplePipeDocByIndex(0);
        
        CreateNodeRequest createRequest = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("metadata_test.pb")
            .setType(Node.NodeType.FILE)
            .setPayload(Any.pack(doc))
            .putMetadata("version", "1.0")
            .putMetadata("author", "test")
            .build();
        
        Node node = filesystemService.createNode(createRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Update with complex metadata
        Map<String, String> newMetadata = new HashMap<>();
        newMetadata.put("version", "2.0");
        newMetadata.put("lastModified", Instant.now().toString());
        newMetadata.put("tags", "protobuf,test,sample");
        newMetadata.put("processingStage", "embedder");
        newMetadata.put("chunkCount", String.valueOf(doc.getSemanticResultsCount() > 0 ? doc.getSemanticResults(0).getChunksCount() : 0));
        
        UpdateNodeRequest updateRequest = UpdateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId(node.getId())
            .setName("metadata_test_v2.pb")
            .putAllMetadata(newMetadata)
            .build();
        
        Node updatedNode = filesystemService.updateNode(updateRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify updates
        assertThat(updatedNode.getName(), equalTo("metadata_test_v2.pb"));
        assertThat(updatedNode.getMetadataMap(), hasEntry("version", "2.0"));
        assertThat(updatedNode.getMetadataMap(), hasEntry("author", "test")); // Original preserved
        assertThat(updatedNode.getMetadataMap(), hasEntry("tags", "protobuf,test,sample"));
        assertThat(updatedNode.getMetadataMap().size(), equalTo(6));
    }
    
    @Test
    void testErrorHandlingWithInvalidProtobuf() {
        // Test creating a node with an invalid payload
        StringValue simpleValue = StringValue.of("test");
        
        CreateNodeRequest request = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("invalid.pb")
            .setType(Node.NodeType.FILE)
            .setPayload(Any.pack(simpleValue))
            .build();
        
        // Should succeed - we accept any protobuf type
        Node node = filesystemService.createNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertNotNull(node);
        assertThat(node.getPayloadType(), equalTo("type.googleapis.com/google.protobuf.StringValue"));
    }
}