package io.pipeline.repository.service;

import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.pipeline.repository.filesystem.*;
import io.pipeline.repository.config.NamespacedRedisKeyService;
import io.pipeline.data.module.ModuleProcessRequest;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.jboss.logging.Logger;

/**
 * Advanced tests for FilesystemService including performance, concurrency, and edge cases.
 */
@QuarkusTest
public class FilesystemAdvancedTest extends IsolatedRedisTest {
    
    private static final Logger LOG = Logger.getLogger(FilesystemAdvancedTest.class);
    
    @GrpcClient("test-filesystem")
    MutinyFilesystemServiceGrpc.MutinyFilesystemServiceStub filesystemService;
    
    @Inject
    ProtobufTestDataHelper testDataHelper;
    
    @BeforeEach
    void setUp(TestInfo testInfo) {
        super.setupTestIsolation(testInfo);
    }
    
    @Test
    void testConcurrentNodeCreation() throws InterruptedException {
        // Create a root folder
        CreateNodeRequest folderRequest = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("Concurrent Test Folder")
            .setType(Node.NodeType.FOLDER)
            .build();
        
        Node folder = filesystemService.createNode(folderRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Create nodes concurrently
        int threadCount = 10;
        int nodesPerThread = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < nodesPerThread; i++) {
                        CreateNodeRequest request = CreateNodeRequest.newBuilder()
                            .setDrive(getTestDrive())
                            .setName("node_t" + threadId + "_n" + i)
                            .setType(Node.NodeType.FILE)
                            .setParentId(folder.getId())
                            .setPayload(Any.pack(Empty.getDefaultInstance()))
                            .build();
                        
                        filesystemService.createNode(request)
                            .subscribe().with(
                                node -> successCount.incrementAndGet(),
                                error -> errorCount.incrementAndGet()
                            );
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        Thread.sleep(1000); // Allow async operations to complete
        
        // Verify results
        assertThat(errorCount.get(), equalTo(0));
        assertThat(successCount.get(), equalTo(threadCount * nodesPerThread));
        
        // Verify all nodes were created
        GetChildrenResponse children = filesystemService.getChildren(
            GetChildrenRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(folder.getId())
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(children.getNodesCount(), equalTo(threadCount * nodesPerThread));
    }
    
    @Test
    void testDeepFolderHierarchy() {
        // Create a deep folder structure
        int depth = 10;
        String parentId = null;
        List<Node> folders = new ArrayList<>();
        
        for (int i = 0; i < depth; i++) {
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("level_" + i)
                .setType(Node.NodeType.FOLDER)
                .setParentId(parentId != null ? parentId : "")
                .build();
            
            Node folder = filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
            
            folders.add(folder);
            parentId = folder.getId();
            
            // Add some files at each level
            for (int f = 0; f < 3; f++) {
                CreateNodeRequest fileRequest = CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setName("file_" + i + "_" + f + ".txt")
                    .setType(Node.NodeType.FILE)
                    .setParentId(folder.getId())
                    .build();
                
                filesystemService.createNode(fileRequest)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem();
            }
        }
        
        // Test recursive deletion from middle
        Node middleFolder = folders.get(depth / 2);
        DeleteNodeResponse deleteResponse = filesystemService.deleteNode(
            DeleteNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setId(middleFolder.getId())
                .setRecursive(true)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Should delete:
        // - The middle folder itself (1)
        // - All folders below it: depth - (depth/2) - 1 = 10 - 5 - 1 = 4 folders (levels 6,7,8,9)
        // - Files in middle folder: 3
        // - Files in child folders: 4 folders * 3 files = 12 files
        // Total: 1 + 4 + 3 + 12 = 20
        int foldersBelow = depth - (depth / 2) - 1; // 4
        int filesInMiddle = 3;
        int filesInChildren = foldersBelow * 3; // 12
        int expectedDeleted = 1 + foldersBelow + filesInMiddle + filesInChildren; // 20
        
        assertThat(deleteResponse.getDeletedCount(), equalTo(expectedDeleted));
    }
    
    @Test
    void testLargePayloadHandling() {
        // Create a PipeDoc with many chunks
        PipeDoc.Builder docBuilder = PipeDoc.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle("Large Document")
            .setBody("Base content");
        
        // Add metadata
        for (int i = 0; i < 100; i++) {
            docBuilder.putMetadata("key_" + i, "value_" + i);
        }
        
        PipeDoc largeDoc = docBuilder.build();
        
        CreateNodeRequest request = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("large_document.pb")
            .setType(Node.NodeType.FILE)
            .setPayload(Any.pack(largeDoc))
            .build();
        
        Node node = filesystemService.createNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertNotNull(node);
        assertThat(node.getSize(), greaterThan(1000L)); // Should be a large file
        
        // Verify we can retrieve it
        Node retrieved = filesystemService.getNode(
            GetNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setId(node.getId())
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(retrieved.getSize(), equalTo(node.getSize()));
    }
    
    @Test
    void testBatchOperations() {
        // Test batch creation and deletion
        List<PipeDoc> docs = testDataHelper.getOrderedSamplePipeDocuments();
        assertThat(docs.isEmpty(), is(false));
        
        // Create folder for batch test
        CreateNodeRequest folderRequest = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("Batch Operations")
            .setType(Node.NodeType.FOLDER)
            .build();
        
        Node folder = filesystemService.createNode(folderRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Batch create nodes
        List<Uni<Node>> createOperations = docs.stream()
            .limit(20)
            .map(doc -> {
                CreateNodeRequest request = CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setName("batch_" + doc.getId() + ".pb")
                    .setType(Node.NodeType.FILE)
                    .setParentId(folder.getId())
                    .setPayload(Any.pack(doc))
                    .build();
                
                return filesystemService.createNode(request);
            })
            .collect(Collectors.toList());
        
        // Execute all creates in parallel
        List<Node> createdNodes = Uni.combine().all().unis(createOperations)
            .with(nodes -> nodes.stream()
                .map(n -> (Node) n)
                .collect(Collectors.toList()))
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(createdNodes.size(), equalTo(20));
        
        // Test batch update
        List<Uni<Node>> updateOperations = createdNodes.stream()
            .map(node -> {
                UpdateNodeRequest request = UpdateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setId(node.getId())
                    .putMetadata("batchUpdate", "true")
                    .putMetadata("updateTime", Instant.now().toString())
                    .build();
                
                return filesystemService.updateNode(request);
            })
            .collect(Collectors.toList());
        
        Uni.combine().all().unis(updateOperations)
            .with(results -> results)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem();
        
        // Verify updates
        GetChildrenResponse children = filesystemService.getChildren(
            GetChildrenRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(folder.getId())
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        boolean allUpdated = children.getNodesList().stream()
            .allMatch(node -> "true".equals(node.getMetadataMap().get("batchUpdate")));
        assertThat(allUpdated, is(true));
    }
    
    @Test
    void testDriveIsolation() {
        // Create nodes in multiple drives
        String drive1 = getTestDrive() + "-1";
        String drive2 = getTestDrive() + "-2";
        
        // Set drive overrides for each drive
        String originalDrive = getTestDrive();
        
        // Create drives first
        filesystemService.createDrive(CreateDriveRequest.newBuilder()
            .setName(drive1)
            .setDescription("Test drive 1")
            .build())
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem();
            
        filesystemService.createDrive(CreateDriveRequest.newBuilder()
            .setName(drive2)
            .setDescription("Test drive 2")
            .build())
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem();
        
        // Create in drive1
        setTestDrive(drive1);
        CreateNodeRequest request1 = CreateNodeRequest.newBuilder()
            .setDrive(drive1)
            .setName("file_in_drive1.txt")
            .setType(Node.NodeType.FILE)
            .build();
        
        Node node1 = filesystemService.createNode(request1)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Create in drive2
        setTestDrive(drive2);
        CreateNodeRequest request2 = CreateNodeRequest.newBuilder()
            .setDrive(drive2)
            .setName("file_in_drive2.txt")
            .setType(Node.NodeType.FILE)
            .build();
        
        Node node2 = filesystemService.createNode(request2)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify isolation - node1 should not exist in drive2
        setTestDrive(drive2);
        Throwable exception = filesystemService.getNode(
            GetNodeRequest.newBuilder()
                .setDrive(drive2)
                .setId(node1.getId())
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(exception, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) exception;
        assertThat(sre.getStatus().getCode(), equalTo(Status.NOT_FOUND.getCode()));
        
        // Format drive1 should not affect drive2
        setTestDrive(drive1);
        filesystemService.formatFilesystem(
            FormatFilesystemRequest.newBuilder()
                .setDrive(drive1)
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .setDryRun(false)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem();
        
        // Node2 should still exist
        setTestDrive(drive2);
        Node stillExists = filesystemService.getNode(
            GetNodeRequest.newBuilder()
                .setDrive(drive2)
                .setId(node2.getId())
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertNotNull(stillExists);
        
        // Restore original drive
        setTestDrive(originalDrive);
    }
    
    @Test
    void testComplexFormatOperations() {
        // Create a complex filesystem structure
        Map<String, List<Node>> nodesByType = new HashMap<>();
        
        // Create root folders
        List<Node> rootFolders = IntStream.range(0, 3)
            .mapToObj(i -> CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("category_" + i)
                .setType(Node.NodeType.FOLDER)
                .build())
            .map(request -> filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem())
            .collect(Collectors.toList());
        
        // Add different types of files to each folder
        Collection<PipeDoc> pipeDocs = testDataHelper.getSamplePipeDocuments();
        Collection<PipeStream> pipeStreams = testDataHelper.getSamplePipeStreams();
        LOG.infof("Test data: PipeDocs=%d, PipeStreams=%d", pipeDocs.size(), pipeStreams.size());
        
        // Category 0: PipeDocs
        Iterator<PipeDoc> docIter = pipeDocs.iterator();
        List<Node> pipeDocNodes = new ArrayList<>();
        for (int i = 0; i < 5 && docIter.hasNext(); i++) {
            PipeDoc doc = docIter.next();
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("doc_" + i + ".pb")
                .setType(Node.NodeType.FILE)
                .setParentId(rootFolders.get(0).getId())
                .setPayload(Any.pack(doc))
                .build();
            
            Node node = filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
            pipeDocNodes.add(node);
        }
        nodesByType.put("type.googleapis.com/io.pipeline.search.model.PipeDoc", pipeDocNodes);
        
        // Category 1: PipeStreams
        Iterator<PipeStream> streamIter = pipeStreams.iterator();
        List<Node> pipeStreamNodes = new ArrayList<>();
        LOG.debugf("Creating PipeStream nodes, available streams: %s", streamIter.hasNext());
        
        // If no PipeStreams available, create them manually
        if (!streamIter.hasNext()) {
            LOG.warn("No PipeStreams available from test data helper, creating manually");
            pipeStreamNodes = IntStream.range(0, 3)
                .mapToObj(i -> {
                    PipeStream stream = PipeStream.newBuilder()
                        .setStreamId("stream-" + i)
                        .setDocument(PipeDoc.newBuilder()
                            .setId("stream-doc-" + i)
                            .setTitle("Doc in stream " + i)
                            .build())
                        .setCurrentPipelineName("test-pipeline")
                        .setTargetStepName("test-step")
                        .build();
                        
                    CreateNodeRequest request = CreateNodeRequest.newBuilder()
                        .setDrive(getTestDrive())
                        .setName("stream_" + i + ".pb")
                        .setType(Node.NodeType.FILE)
                        .setParentId(rootFolders.get(1).getId())
                        .setPayload(Any.pack(stream))
                        .build();
                    
                    return filesystemService.createNode(request)
                        .subscribe().withSubscriber(UniAssertSubscriber.create())
                        .awaitItem()
                        .getItem();
                })
                .collect(Collectors.toList());
        } else {
            for (int i = 0; i < 3 && streamIter.hasNext(); i++) {
                PipeStream stream = streamIter.next();
                CreateNodeRequest request = CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setName("stream_" + i + ".pb")
                    .setType(Node.NodeType.FILE)
                    .setParentId(rootFolders.get(1).getId())
                    .setPayload(Any.pack(stream))
                    .build();
                
                Node node = filesystemService.createNode(request)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem()
                    .getItem();
                pipeStreamNodes.add(node);
            }
        }
        nodesByType.put("type.googleapis.com/io.pipeline.search.model.PipeStream", pipeStreamNodes);
        
        // Category 2: Mixed types
        List<Node> mixedNodes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Any payload;
            if (i % 2 == 0) {
                payload = Any.pack(ModuleProcessRequest.newBuilder()
                    .setDocument(PipeDoc.newBuilder()
                        .setId(UUID.randomUUID().toString())
                        .build())
                    .build());
            } else {
                payload = Any.pack(Empty.getDefaultInstance());
            }
            
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("mixed_" + i + ".pb")
                .setType(Node.NodeType.FILE)
                .setParentId(rootFolders.get(2).getId())
                .setPayload(payload)
                .build();
            
            Node node = filesystemService.createNode(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
            mixedNodes.add(node);
        }
        
        // Test selective format operations
        
        // 1. Delete only PipeDocs
        FormatFilesystemResponse pipeDocFormat = filesystemService.formatFilesystem(
            FormatFilesystemRequest.newBuilder()
                .setDrive(getTestDrive())
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .addTypeUrls("type.googleapis.com/io.pipeline.search.model.PipeDoc")
                .setDryRun(false)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(pipeDocFormat.getNodesDeleted(), equalTo(5));
        assertThat(pipeDocFormat.getFoldersDeleted(), equalTo(0));
        
        // 2. Delete multiple types
        FormatFilesystemResponse multiFormat = filesystemService.formatFilesystem(
            FormatFilesystemRequest.newBuilder()
                .setDrive(getTestDrive())
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .addTypeUrls("type.googleapis.com/io.pipeline.search.model.PipeStream")
                .addTypeUrls("type.googleapis.com/io.pipeline.search.model.ModuleProcessRequest")
                .setDryRun(false)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        LOG.infof("Second format result: deleted=%d, byType=%s", multiFormat.getNodesDeleted(), multiFormat.getDeletedByTypeMap());
        assertThat(multiFormat.getNodesDeleted(), equalTo(5)); // 3 PipeStreams + 2 ModuleProcessRequests
        
        // 3. Format all remaining
        FormatFilesystemResponse finalFormat = filesystemService.formatFilesystem(
            FormatFilesystemRequest.newBuilder()
                .setDrive(getTestDrive())
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .setDryRun(false)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(finalFormat.getNodesDeleted(), equalTo(2)); // 2 Empty messages
        assertThat(finalFormat.getFoldersDeleted(), equalTo(3)); // 3 root folders
    }
    
    @Test
    void testNodeNameValidation() {
        // Test various invalid node names
        List<String> invalidNames = Arrays.asList(
            "", // empty
            "   ", // only spaces
            null, // null
            "name/with/slashes",
            "name\\with\\backslashes",
            "..", // parent directory
            ".", // current directory
            "name\0with\0nulls"
        );
        
        for (String invalidName : invalidNames) {
            CreateNodeRequest request = CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName(invalidName != null ? invalidName : "")
                .setType(Node.NodeType.FILE)
                .build();
            
            if (invalidName == null || invalidName.trim().isEmpty()) {
                // Should fail for null or empty names
                Throwable exception = filesystemService.createNode(request)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitFailure()
                    .getFailure();
                
                assertThat(exception, instanceOf(StatusRuntimeException.class));
                StatusRuntimeException sre = (StatusRuntimeException) exception;
                assertThat(sre.getStatus().getCode(), equalTo(Status.INVALID_ARGUMENT.getCode()));
            } else {
                // Other names might be allowed depending on implementation
                // Just verify it doesn't crash
                try {
                    filesystemService.createNode(request)
                        .subscribe().withSubscriber(UniAssertSubscriber.create())
                        .awaitItem();
                } catch (AssertionError e) {
                    // Some names might be rejected, which is fine
                    // Check if the failure is a StatusRuntimeException with INVALID_ARGUMENT
                    Throwable cause = e.getCause();
                    if (cause instanceof StatusRuntimeException) {
                        StatusRuntimeException sre = (StatusRuntimeException) cause;
                        assertThat(sre.getStatus().getCode(), equalTo(Status.INVALID_ARGUMENT.getCode()));
                    } else {
                        throw e;
                    }
                }
            }
        }
    }
    
    private void setTestDrive(String drive) {
        // This would update the test drive in the parent class
        // For this test, we're simulating different drives
        NamespacedRedisKeyService.setTestDriveOverride(drive);
    }
}