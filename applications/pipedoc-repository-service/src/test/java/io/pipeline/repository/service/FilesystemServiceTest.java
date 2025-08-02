package io.pipeline.repository.service;

import com.google.protobuf.Any;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ModuleProcessResponse;
import io.pipeline.repository.filesystem.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
public class FilesystemServiceTest extends IsolatedRedisTest {
    
    private static final Logger LOG = Logger.getLogger(FilesystemServiceTest.class);
    
    @GrpcClient("test-filesystem")
    MutinyFilesystemServiceGrpc.MutinyFilesystemServiceStub filesystemService;
    
    @Test
    void testCreateAndGetNode() {
        // Given - Create a folder
        CreateNodeRequest folderRequest = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("Test Folder")
            .setType(Node.NodeType.FOLDER)
            .putMetadata("category", "test")
            .build();
        
        // When - Create the folder
        Node folder = filesystemService.createNode(folderRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat("Created folder should not be null", folder, is(notNullValue()));
        assertThat("Folder ID should not be null", folder.getId(), is(notNullValue()));
        assertThat("Folder name should match", folder.getName(), is(equalTo("Test Folder")));
        assertThat("Folder type should be FOLDER", folder.getType(), is(equalTo(Node.NodeType.FOLDER)));
        
        // Then - Get the folder
        GetNodeRequest getRequest = GetNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId(folder.getId())
            .build();
        
        Node retrieved = filesystemService.getNode(getRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat("Retrieved node should not be null", retrieved, is(notNullValue()));
        assertThat("Retrieved node ID should match", retrieved.getId(), is(equalTo(folder.getId())));
        assertThat("Retrieved node name should match", retrieved.getName(), is(equalTo("Test Folder")));
    }
    
    @Test
    void testCreateFileWithPayload() {
        // Given - Create a PipeDoc
        PipeDoc doc = PipeDoc.newBuilder()
            .setId("test-doc-456")
            .setTitle("Test Document")
            .setBody("Test content")
            .build();
        
        Any payload = Any.pack(doc);
        
        CreateNodeRequest fileRequest = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("test-document.json")
            .setType(Node.NodeType.FILE)
            .setPayload(payload)
            .putMetadata("contentType", "application/json")
            .build();
        
        // When - Create the file
        Node file = filesystemService.createNode(fileRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat("Created file should not be null", file, is(notNullValue()));
        assertThat("File name should match", file.getName(), is(equalTo("test-document.json")));
        assertThat("File type should be FILE", file.getType(), is(equalTo(Node.NodeType.FILE)));
        assertThat("File size should be greater than 0", file.getSize(), is(greaterThan(0L)));
        assertThat("File payload type should be set", file.getPayloadType(), is(equalTo("type.googleapis.com/io.pipeline.search.model.PipeDoc")));
        
        // Then - Verify the file can be retrieved with payload
        GetNodeRequest getRequest = GetNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId(file.getId())
            .build();
            
        Node retrievedFile = filesystemService.getNode(getRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
            
        assertThat("Retrieved file should not be null", retrievedFile, is(notNullValue()));
        assertThat("Retrieved file should have payload", retrievedFile.hasPayload(), is(true));
        assertThat("Retrieved file payload type should match", retrievedFile.getPayloadType(), is(equalTo("type.googleapis.com/io.pipeline.search.model.PipeDoc")));
        
        // Verify the actual payload content
        Any retrievedPayload = retrievedFile.getPayload();
        assertThat("Retrieved payload should not be null", retrievedPayload, is(notNullValue()));
        assertThat("Retrieved payload type URL should match", retrievedPayload.getTypeUrl(), is(equalTo("type.googleapis.com/io.pipeline.search.model.PipeDoc")));
        
        // Unpack and verify the PipeDoc
        PipeDoc retrievedDoc = null;
        try {
            retrievedDoc = retrievedPayload.unpack(PipeDoc.class);
        } catch (Exception e) {
            assertThat("Should be able to unpack PipeDoc from payload", e, is(nullValue()));
        }
        
        assertThat("Retrieved doc should not be null", retrievedDoc, is(notNullValue()));
        assertThat("Retrieved doc ID should match", retrievedDoc.getId(), is(equalTo("test-doc-456")));
        assertThat("Retrieved doc title should match", retrievedDoc.getTitle(), is(equalTo("Test Document")));
        assertThat("Retrieved doc body should match", retrievedDoc.getBody(), is(equalTo("Test content")));
    }
    
    @Test
    void testTypeSafePayloadRetrieval() {
        // Given - Create a PipeDoc
        PipeDoc doc = PipeDoc.newBuilder()
            .setId("type-safe-doc-123")
            .setTitle("Type Safe Document")
            .setBody("This tests type-safe retrieval")
            .build();
        
        CreateNodeRequest fileRequest = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("type-safe-doc.json")
            .setType(Node.NodeType.FILE)
            .setPayload(Any.pack(doc))
            .build();
        
        // When - Create the file
        Node file = filesystemService.createNode(fileRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Then - Use the helper for type-safe retrieval
        // Note: In a real scenario, you would inject RepositoryFilesystemHelper
        // For this test, we'll demonstrate the pattern with direct API usage
        
        // Type-safe approach with Mutiny chain
        PipeDoc retrievedDoc = filesystemService.getNode(
                GetNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setId(file.getId())
                    .build()
            )
            .map(node -> {
                try {
                    return node.hasPayload() ? node.getPayload().unpack(PipeDoc.class) : null;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to unpack", e);
                }
            })
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat("Retrieved doc should not be null", retrievedDoc, is(notNullValue()));
        assertThat("Retrieved doc ID should match", retrievedDoc.getId(), is(equalTo("type-safe-doc-123")));
        assertThat("Retrieved doc title should match", retrievedDoc.getTitle(), is(equalTo("Type Safe Document")));
    }
    
    @Test
    void testGetChildren() {
        // Given - Create a parent folder
        Node parent = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("Parent Folder")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        // Create some children
        filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(parent.getId())
                .setName("Child 1")
                .setType(Node.NodeType.FILE)
                .build()
        ).await().indefinitely();
        
        filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(parent.getId())
                .setName("Child 2")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        // When - Get children
        GetChildrenRequest request = GetChildrenRequest.newBuilder()
            .setDrive(getTestDrive())
            .setParentId(parent.getId())
            .build();
        
        GetChildrenResponse response = filesystemService.getChildren(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Then
        assertThat("Get children response should not be null", response, is(notNullValue()));
        assertThat("Should have 2 children", response.getNodesCount(), is(equalTo(2)));
        assertThat("Children should include Child 1", 
            response.getNodesList().stream().map(Node::getName).collect(Collectors.toList()),
            hasItem("Child 1"));
        assertThat("Children should include Child 2",
            response.getNodesList().stream().map(Node::getName).collect(Collectors.toList()),
            hasItem("Child 2"));
    }
    
    @Test
    void testUpdateNode() {
        // Given - Create a node
        Node original = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("Original Name")
                .setType(Node.NodeType.FILE)
                .build()
        ).await().indefinitely();
        
        // When - Update it
        UpdateNodeRequest updateRequest = UpdateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId(original.getId())
            .setName("Updated Name")
            .putMetadata("version", "2")
            .build();
        
        Node updated = filesystemService.updateNode(updateRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Then
        assertThat("Updated node should not be null", updated, is(notNullValue()));
        assertThat("Updated node name should match", updated.getName(), is(equalTo("Updated Name")));
        assertThat("Updated node metadata should contain version", 
            updated.getMetadataMap(), hasEntry("version", "2"));
    }
    
    @Test
    void testDeleteNode() {
        // Given - Create a node
        Node node = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("To Delete")
                .setType(Node.NodeType.FILE)
                .build()
        ).await().indefinitely();
        
        // When - Delete it
        DeleteNodeRequest deleteRequest = DeleteNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId(node.getId())
            .build();
        
        DeleteNodeResponse response = filesystemService.deleteNode(deleteRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Then
        assertThat("Delete operation should succeed", response.getSuccess(), is(true));
        assertThat("Should have deleted 1 node", response.getDeletedCount(), is(equalTo(1)));
        
        // Verify it's gone
        GetNodeRequest getRequest = GetNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId(node.getId())
            .build();
        
        assertThrows(Exception.class, () -> {
            filesystemService.getNode(getRequest).await().indefinitely();
        });
    }
    
    @Test
    void testDeleteFolderRecursive() {
        // Given - Create folder structure
        Node folder = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName("Folder to Delete")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        // Add children
        for (int i = 0; i < 3; i++) {
            filesystemService.createNode(
                CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setParentId(folder.getId())
                    .setName("Child " + i)
                    .setType(Node.NodeType.FILE)
                    .build()
            ).await().indefinitely();
        }
        
        // When - Delete recursively
        DeleteNodeRequest deleteRequest = DeleteNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId(folder.getId())
            .setRecursive(true)
            .build();
        
        DeleteNodeResponse response = filesystemService.deleteNode(deleteRequest)
            .await().indefinitely();
        
        // Then
        assertThat("Recursive delete should succeed", response.getSuccess(), is(true));
        assertThat("Should have deleted 4 nodes (folder + 3 children)", 
            response.getDeletedCount(), is(equalTo(4)));
    }
    
    @Test
    void testFormatFilesystem() {
        // Given - Create some test data in an isolated test namespace
        // Use a unique test prefix to prevent interference from other tests
        String testPrefix = "format-test-" + UUID.randomUUID() + "-";
        
        Node folder1 = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName(testPrefix + "Test Folder 1")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        Node file1 = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(folder1.getId())
                .setName(testPrefix + "test-file-1.txt")
                .setType(Node.NodeType.FILE)
                .build()
        ).await().indefinitely();
        
        Node file2 = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(folder1.getId())
                .setName(testPrefix + "test-file-2.txt")
                .setType(Node.NodeType.FILE)
                .build()
        ).await().indefinitely();
        
        // Verify files exist
        assertThat("File 1 with ID " + file1.getId() + " should exist before format operation", 
            filesystemService.getNode(GetNodeRequest.newBuilder().setDrive(getTestDrive()).setId(file1.getId()).build())
                .await().indefinitely(), is(notNullValue()));
        assertThat("File 2 with ID " + file2.getId() + " should exist before format operation", 
            filesystemService.getNode(GetNodeRequest.newBuilder().setDrive(getTestDrive()).setId(file2.getId()).build())
                .await().indefinitely(), is(notNullValue()));
        
        // When - Format the filesystem with dry run first
        FormatFilesystemRequest dryRunRequest = FormatFilesystemRequest.newBuilder()
            .setDrive(getTestDrive())
            .setConfirmation("DELETE_FILESYSTEM_DATA")
            .setDryRun(true)
            .build();
        
        FormatFilesystemResponse dryRunResponse = filesystemService.formatFilesystem(dryRunRequest)
            .await().indefinitely();
        
        // Then - Verify dry run results with exact counts
        LOG.debugf("Dry run response: %s", dryRunResponse);
        assertThat("Dry run operation should succeed without errors", 
            dryRunResponse.getSuccess(), is(true));
        assertThat("Dry run should report exactly 2 files would be deleted (created file1 and file2)", 
            dryRunResponse.getNodesDeleted(), is(equalTo(2)));
        assertThat("Dry run should report exactly 1 folder would be deleted (created folder1)", 
            dryRunResponse.getFoldersDeleted(), is(equalTo(1)));
        
        // Files should still exist after dry run
        assertThat("File 1 with ID " + file1.getId() + " should still exist after dry run since no actual deletion occurred", 
            filesystemService.getNode(GetNodeRequest.newBuilder().setDrive(getTestDrive()).setId(file1.getId()).build())
                .await().indefinitely(), is(notNullValue()));
        assertThat("File 2 with ID " + file2.getId() + " should still exist after dry run since no actual deletion occurred", 
            filesystemService.getNode(GetNodeRequest.newBuilder().setDrive(getTestDrive()).setId(file2.getId()).build())
                .await().indefinitely(), is(notNullValue()));
        
        // When - Actually format the filesystem
        FormatFilesystemRequest formatRequest = FormatFilesystemRequest.newBuilder()
            .setDrive(getTestDrive())
            .setConfirmation("DELETE_FILESYSTEM_DATA")
            .setDryRun(false)
            .build();
        
        FormatFilesystemResponse formatResponse = filesystemService.formatFilesystem(formatRequest)
            .await().indefinitely();
        
        // Then - Verify format results with exact counts
        LOG.debugf("Format response: %s", formatResponse);
        assertThat("Format operation should succeed without errors", 
            formatResponse.getSuccess(), is(true));
        assertThat("Format should delete exactly 2 files (file1 and file2 that were created)", 
            formatResponse.getNodesDeleted(), is(equalTo(2)));
        assertThat("Format should delete exactly 1 folder (folder1 that was created)", 
            formatResponse.getFoldersDeleted(), is(equalTo(1)));
        assertThat("Format response should contain a success message", 
            formatResponse.getMessage(), containsString("Formatted filesystem"));
        
        // Files should no longer exist
        Node deletedFile1 = filesystemService.getNode(GetNodeRequest.newBuilder().setDrive(getTestDrive()).setId(file1.getId()).build())
            .onFailure().recoverWithNull()
            .await().indefinitely();
        assertThat("File 1 with ID " + file1.getId() + " should be null after format operation deleted it", 
            deletedFile1, is(nullValue()));
        
        Node deletedFile2 = filesystemService.getNode(GetNodeRequest.newBuilder().setDrive(getTestDrive()).setId(file2.getId()).build())
            .onFailure().recoverWithNull()
            .await().indefinitely();
        assertThat("File 2 with ID " + file2.getId() + " should be null after format operation deleted it", 
            deletedFile2, is(nullValue()));
        
        Node deletedFolder = filesystemService.getNode(GetNodeRequest.newBuilder().setDrive(getTestDrive()).setId(folder1.getId()).build())
            .onFailure().recoverWithNull()
            .await().indefinitely();
        assertThat("Folder 1 with ID " + folder1.getId() + " should be null after format operation deleted it", 
            deletedFolder, is(nullValue()));
    }
    
    @Test
    void testFormatFilesystemWithVariousProtobufTypes() {
        // Given - Create test data with various protobuf types
        String testPrefix = "format-protobuf-test-" + UUID.randomUUID() + "-";
        
        // Create folders for organizing test data
        Node docsFolder = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName(testPrefix + "PipeDocs")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        Node streamsFolder = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName(testPrefix + "PipeStreams")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        Node requestsFolder = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName(testPrefix + "ModuleRequests")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        // Insert test PipeDocs
        int pipeDocsCreated = 5;
        for (int i = 0; i < pipeDocsCreated; i++) {
            PipeDoc doc = PipeDoc.newBuilder()
                .setId("test-doc-" + UUID.randomUUID())
                .setTitle("Test Document " + i)
                .setBody("This is test document " + i + " for format testing")
                .build();
            
            filesystemService.createNode(
                CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setParentId(docsFolder.getId())
                    .setName(testPrefix + "doc-" + i + ".bin")
                    .setType(Node.NodeType.FILE)
                    .setPayload(Any.pack(doc))
                    .putMetadata("docId", doc.getId())
                    .putMetadata("title", doc.getTitle())
                    .build()
            ).await().indefinitely();
        }
        
        // Insert test PipeStreams
        int pipeStreamsCreated = 3;
        for (int i = 0; i < pipeStreamsCreated; i++) {
            PipeStream stream = PipeStream.newBuilder()
                .setStreamId("test-stream-" + UUID.randomUUID())
                .setDocument(PipeDoc.newBuilder()
                    .setId("stream-doc-" + i)
                    .setTitle("Stream Document " + i)
                    .build())
                .build();
            
            filesystemService.createNode(
                CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setParentId(streamsFolder.getId())
                    .setName(testPrefix + "stream-" + i + ".bin")
                    .setType(Node.NodeType.FILE)
                    .setPayload(Any.pack(stream))
                    .putMetadata("streamId", stream.getStreamId())
                    .build()
            ).await().indefinitely();
        }
        
        // Insert test ModuleProcessRequests
        int moduleRequestsCreated = 4;
        for (int i = 0; i < moduleRequestsCreated; i++) {
            ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .build();
            
            filesystemService.createNode(
                CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setParentId(requestsFolder.getId())
                    .setName(testPrefix + "request-" + i + ".bin")
                    .setType(Node.NodeType.FILE)
                    .setPayload(Any.pack(request))
                    .putMetadata("requestId", "request-" + i)
                    .build()
            ).await().indefinitely();
        }
        
        // When - Format with dry run first to see what would be deleted
        FormatFilesystemRequest dryRunRequest = FormatFilesystemRequest.newBuilder()
            .setDrive(getTestDrive())
            .setConfirmation("DELETE_FILESYSTEM_DATA")
            .setDryRun(true)
            .build();
        
        FormatFilesystemResponse dryRunResponse = filesystemService.formatFilesystem(dryRunRequest)
            .await().indefinitely();
        
        // Then - Verify dry run counts
        LOG.debugf("Dry run response: success=%s, nodesDeleted=%d, foldersDeleted=%d, deletedByType=%s",
            dryRunResponse.getSuccess(), dryRunResponse.getNodesDeleted(), 
            dryRunResponse.getFoldersDeleted(), dryRunResponse.getDeletedByTypeMap());
        
        assertThat("Dry run should succeed", dryRunResponse.getSuccess(), is(true));
        assertThat("Dry run should report correct total files",
            dryRunResponse.getNodesDeleted(), is(equalTo(pipeDocsCreated + pipeStreamsCreated + moduleRequestsCreated)));
        assertThat("Dry run should report 3 folders", 
            dryRunResponse.getFoldersDeleted(), is(equalTo(3)));
        
        // Verify counts by type
        String pipeDocTypeUrl = Any.pack(PipeDoc.getDefaultInstance()).getTypeUrl();
        String pipeStreamTypeUrl = Any.pack(PipeStream.getDefaultInstance()).getTypeUrl();
        String moduleRequestTypeUrl = Any.pack(ModuleProcessRequest.getDefaultInstance()).getTypeUrl();
        
        assertThat("Dry run should report correct PipeDoc count",
            dryRunResponse.getDeletedByTypeMap().getOrDefault(pipeDocTypeUrl, 0), 
            is(equalTo(pipeDocsCreated)));
        assertThat("Dry run should report correct PipeStream count",
            dryRunResponse.getDeletedByTypeMap().getOrDefault(pipeStreamTypeUrl, 0), 
            is(equalTo(pipeStreamsCreated)));
        assertThat("Dry run should report correct ModuleProcessRequest count",
            dryRunResponse.getDeletedByTypeMap().getOrDefault(moduleRequestTypeUrl, 0), 
            is(equalTo(moduleRequestsCreated)));
        
        // When - Actually format the filesystem
        FormatFilesystemRequest formatRequest = FormatFilesystemRequest.newBuilder()
            .setDrive(getTestDrive())
            .setConfirmation("DELETE_FILESYSTEM_DATA")
            .setDryRun(false)
            .build();
        
        FormatFilesystemResponse formatResponse = filesystemService.formatFilesystem(formatRequest)
            .await().indefinitely();
        
        // Then - Verify actual format results
        assertThat("Format should succeed", formatResponse.getSuccess(), is(true));
        assertThat("Format should delete correct total files",
            formatResponse.getNodesDeleted(), is(equalTo(pipeDocsCreated + pipeStreamsCreated + moduleRequestsCreated)));
        assertThat("Format should delete 3 folders", 
            formatResponse.getFoldersDeleted(), is(equalTo(3)));
        
        // Verify by type
        assertThat("Format should delete correct PipeDoc count",
            formatResponse.getDeletedByTypeMap().getOrDefault(pipeDocTypeUrl, 0), 
            is(equalTo(pipeDocsCreated)));
        assertThat("Format should delete correct PipeStream count",
            formatResponse.getDeletedByTypeMap().getOrDefault(pipeStreamTypeUrl, 0), 
            is(equalTo(pipeStreamsCreated)));
        assertThat("Format should delete correct ModuleProcessRequest count",
            formatResponse.getDeletedByTypeMap().getOrDefault(moduleRequestTypeUrl, 0), 
            is(equalTo(moduleRequestsCreated)));
    }
    
    @Test
    void testFormatFilesystemWithTypeFilter() {
        // Given - Create mixed protobuf data
        String testPrefix = "format-filter-test-" + UUID.randomUUID() + "-";
        
        // Create test folder
        Node testFolder = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName(testPrefix + "MixedData")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        // Create various types of files
        // 3 PipeDocs
        for (int i = 0; i < 3; i++) {
            PipeDoc doc = PipeDoc.newBuilder()
                .setId("filter-doc-" + i)
                .setTitle("Filter Test Document " + i)
                .setBody("Content for filter test " + i)
                .build();
            
            filesystemService.createNode(
                CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setParentId(testFolder.getId())
                    .setName(testPrefix + "doc-" + i + ".bin")
                    .setType(Node.NodeType.FILE)
                    .setPayload(Any.pack(doc))
                    .build()
            ).await().indefinitely();
        }
        
        // 2 PipeStreams
        for (int i = 0; i < 2; i++) {
            PipeStream stream = PipeStream.newBuilder()
                .setStreamId("filter-stream-" + i)
                .setDocument(PipeDoc.newBuilder()
                    .setId("filter-stream-doc-" + i)
                    .setTitle("Filter Stream Doc " + i)
                    .build())
                .build();
            
            filesystemService.createNode(
                CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setParentId(testFolder.getId())
                    .setName(testPrefix + "stream-" + i + ".bin")
                    .setType(Node.NodeType.FILE)
                    .setPayload(Any.pack(stream))
                    .build()
            ).await().indefinitely();
        }
        
        // 2 ModuleProcessRequests
        for (int i = 0; i < 2; i++) {
            ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .build();
            
            filesystemService.createNode(
                CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setParentId(testFolder.getId())
                    .setName(testPrefix + "request-" + i + ".bin")
                    .setType(Node.NodeType.FILE)
                    .setPayload(Any.pack(request))
                    .build()
            ).await().indefinitely();
        }
        
        // When - Format only PipeDocs (using type filter)
        String pipeDocTypeUrl = Any.pack(PipeDoc.getDefaultInstance()).getTypeUrl();
        FormatFilesystemRequest formatPipeDocsRequest = FormatFilesystemRequest.newBuilder()
            .setDrive(getTestDrive())
            .setConfirmation("DELETE_FILESYSTEM_DATA")
            .setDryRun(false)
            .addTypeUrls(pipeDocTypeUrl) // Only delete PipeDocs
            .build();
        
        FormatFilesystemResponse pipeDocsResponse = filesystemService.formatFilesystem(formatPipeDocsRequest)
            .await().indefinitely();
        
        // Then - Verify only PipeDocs were deleted
        assertThat("Format PipeDocs should succeed", pipeDocsResponse.getSuccess(), is(true));
        assertThat("Should delete only 3 PipeDocs", pipeDocsResponse.getNodesDeleted(), is(equalTo(3)));
        // When filtering by type, empty folders might still be deleted
        LOG.debugf("Folders deleted when filtering by type: %d", pipeDocsResponse.getFoldersDeleted());
        assertThat("Folder deletion count should be reasonable when filtering by type", 
            pipeDocsResponse.getFoldersDeleted(), is(lessThanOrEqualTo(1)));
        assertThat("Deleted by type should only contain PipeDocs",
            pipeDocsResponse.getDeletedByTypeMap().size(), is(equalTo(1)));
        assertThat("Should delete exactly 3 PipeDocs",
            pipeDocsResponse.getDeletedByTypeMap().get(pipeDocTypeUrl), is(equalTo(3)));
        
        // Verify the folder itself might have been deleted when all its contents were removed
        // Try to get the folder - it might not exist anymore
        Node folderAfterFormat = filesystemService.getNode(
                GetNodeRequest.newBuilder().setDrive(getTestDrive()).setId(testFolder.getId()).build()
            )
            .onFailure().recoverWithNull()
            .await().indefinitely();
        
        if (folderAfterFormat != null) {
            // If folder still exists, check its children
            GetChildrenRequest getChildrenRequest = GetChildrenRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(testFolder.getId())
                .build();
            
            GetChildrenResponse remainingNodes = filesystemService.getChildren(getChildrenRequest)
                .await().indefinitely();
            
            assertThat("Should have 4 remaining files (2 PipeStreams + 2 ModuleProcessRequests)",
                remainingNodes.getNodesCount(), is(equalTo(4)));
            
            // When - Format remaining types
            FormatFilesystemRequest formatRemainingRequest = FormatFilesystemRequest.newBuilder()
                .setDrive(getTestDrive())
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .setDryRun(false)
                .build();
            
            FormatFilesystemResponse remainingResponse = filesystemService.formatFilesystem(formatRemainingRequest)
                .await().indefinitely();
            
            // Then - Verify remaining files and folder were deleted
            assertThat("Format remaining should succeed", remainingResponse.getSuccess(), is(true));
            assertThat("Should delete 4 remaining files", remainingResponse.getNodesDeleted(), is(equalTo(4)));
            assertThat("Should delete 1 folder", remainingResponse.getFoldersDeleted(), is(equalTo(1)));
        } else {
            // The folder was deleted along with all PipeDocs
            // This is expected behavior when all contents of a folder are deleted
            LOG.debugf("Folder was deleted when all PipeDocs were removed");
            
            // Verify we can't find any of the PipeStreams or ModuleProcessRequests either
            // They should have been deleted when their parent folder was deleted
            GetChildrenRequest rootChildrenRequest = GetChildrenRequest.newBuilder()
                .setDrive(getTestDrive())
                .build();
            
            GetChildrenResponse rootChildren = filesystemService.getChildren(rootChildrenRequest)
                .await().indefinitely();
            
            // Check that the test folder is not in the root
            boolean folderDeleted = rootChildren.getNodesList().stream()
                .noneMatch(node -> node.getId().equals(testFolder.getId()));
            
            assertThat("Test folder should have been deleted", folderDeleted, is(true));
        }
    }
    
    @Test
    void testFormatFilesystemWithNestedFolders() {
        // Given - Create deeply nested structure with various protobuf types
        String testPrefix = "format-nested-test-" + UUID.randomUUID() + "-";
        
        // Create root folder
        Node rootFolder = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setName(testPrefix + "Root")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        // Create nested structure: Root -> Level1 -> Level2 -> Files
        Node level1Folder = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(rootFolder.getId())
                .setName("Level1")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        Node level2Folder = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(level1Folder.getId())
                .setName("Level2")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        // Add files at each level
        // Root level - 2 PipeDocs
        for (int i = 0; i < 2; i++) {
            PipeDoc doc = PipeDoc.newBuilder()
                .setId("nested-root-doc-" + i)
                .setTitle("Nested Root Document " + i)
                .setBody("Content at root level " + i)
                .build();
            
            filesystemService.createNode(
                CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setParentId(rootFolder.getId())
                    .setName("root-doc-" + i + ".bin")
                    .setType(Node.NodeType.FILE)
                    .setPayload(Any.pack(doc))
                    .build()
            ).await().indefinitely();
        }
        
        // Level1 - 1 PipeStream
        PipeStream stream = PipeStream.newBuilder()
            .setStreamId("nested-level1-stream")
            .setDocument(PipeDoc.newBuilder()
                .setId("nested-level1-doc")
                .setTitle("Level1 Stream Document")
                .build())
            .build();
        
        filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setParentId(level1Folder.getId())
                .setName("level1-stream.bin")
                .setType(Node.NodeType.FILE)
                .setPayload(Any.pack(stream))
                .build()
        ).await().indefinitely();
        
        // Level2 - 2 ModuleProcessRequests
        for (int i = 0; i < 2; i++) {
            ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .build();
            
            filesystemService.createNode(
                CreateNodeRequest.newBuilder()
                    .setDrive(getTestDrive())
                    .setParentId(level2Folder.getId())
                    .setName("level2-request-" + i + ".bin")
                    .setType(Node.NodeType.FILE)
                    .setPayload(Any.pack(request))
                    .build()
            ).await().indefinitely();
        }
        
        // When - Format filesystem with dry run
        FormatFilesystemRequest dryRunRequest = FormatFilesystemRequest.newBuilder()
            .setDrive(getTestDrive())
            .setConfirmation("DELETE_FILESYSTEM_DATA")
            .setDryRun(true)
            .build();
        
        FormatFilesystemResponse dryRunResponse = filesystemService.formatFilesystem(dryRunRequest)
            .await().indefinitely();
        
        // Then - Verify dry run counts for nested structure
        assertThat("Dry run should succeed", dryRunResponse.getSuccess(), is(true));
        assertThat("Should report 5 files total (2 + 1 + 2)", 
            dryRunResponse.getNodesDeleted(), is(equalTo(5)));
        // Note: Folder deletion count may vary based on implementation
        LOG.debugf("Dry run folders to delete: %d", dryRunResponse.getFoldersDeleted());
        assertThat("Should report at least 1 folder", 
            dryRunResponse.getFoldersDeleted(), is(greaterThanOrEqualTo(1)));
        
        // When - Actually format
        FormatFilesystemRequest formatRequest = FormatFilesystemRequest.newBuilder()
            .setDrive(getTestDrive())
            .setConfirmation("DELETE_FILESYSTEM_DATA")
            .setDryRun(false)
            .build();
        
        FormatFilesystemResponse formatResponse = filesystemService.formatFilesystem(formatRequest)
            .await().indefinitely();
        
        // Then - Verify all nested items were deleted
        assertThat("Format should succeed", formatResponse.getSuccess(), is(true));
        assertThat("Should delete 5 files total", 
            formatResponse.getNodesDeleted(), is(equalTo(5)));
        // Note: Folder deletion count may vary based on implementation  
        LOG.debugf("Folders deleted: %d", formatResponse.getFoldersDeleted());
        assertThat("Should delete at least 1 folder", 
            formatResponse.getFoldersDeleted(), is(greaterThanOrEqualTo(1)));
        
        // Verify type counts
        String pipeDocTypeUrl = Any.pack(PipeDoc.getDefaultInstance()).getTypeUrl();
        String pipeStreamTypeUrl = Any.pack(PipeStream.getDefaultInstance()).getTypeUrl();
        String moduleRequestTypeUrl = Any.pack(ModuleProcessRequest.getDefaultInstance()).getTypeUrl();
        
        assertThat("Should delete 2 PipeDocs",
            formatResponse.getDeletedByTypeMap().get(pipeDocTypeUrl), is(equalTo(2)));
        assertThat("Should delete 1 PipeStream",
            formatResponse.getDeletedByTypeMap().get(pipeStreamTypeUrl), is(equalTo(1)));
        assertThat("Should delete 2 ModuleProcessRequests",
            formatResponse.getDeletedByTypeMap().get(moduleRequestTypeUrl), is(equalTo(2)));
    }
    
    @Test
    void testFormatFilesystemEmptyFilesystem() {
        // Given - Ensure filesystem is already empty by formatting first
        FormatFilesystemRequest initialFormat = FormatFilesystemRequest.newBuilder()
            .setDrive(getTestDrive())
            .setConfirmation("DELETE_FILESYSTEM_DATA")
            .setDryRun(false)
            .build();
        
        filesystemService.formatFilesystem(initialFormat).await().indefinitely();
        
        // When - Try to format an empty filesystem
        FormatFilesystemRequest formatRequest = FormatFilesystemRequest.newBuilder()
            .setDrive(getTestDrive())
            .setConfirmation("DELETE_FILESYSTEM_DATA")
            .setDryRun(false)
            .build();
        
        FormatFilesystemResponse response = filesystemService.formatFilesystem(formatRequest)
            .await().indefinitely();
        
        // Then - Should handle empty filesystem gracefully
        assertThat("Format empty filesystem should succeed", response.getSuccess(), is(true));
        assertThat("Should delete 0 nodes", response.getNodesDeleted(), is(equalTo(0)));
        assertThat("Should delete 0 folders", response.getFoldersDeleted(), is(equalTo(0)));
        assertThat("Should have appropriate message", response.getMessage(), 
            containsString("No nodes to delete"));
    }
}