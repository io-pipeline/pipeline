package io.pipeline.repository.service;

import com.google.protobuf.Any;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.repository.filesystem.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
public class FilesystemServiceTest {
    
    private static final Logger LOG = Logger.getLogger(FilesystemServiceTest.class);
    
    @GrpcClient("test-filesystem")
    MutinyFilesystemServiceGrpc.MutinyFilesystemServiceStub filesystemService;
    
    @Test
    void testCreateAndGetNode() {
        // Given - Create a folder
        CreateNodeRequest folderRequest = CreateNodeRequest.newBuilder()
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
    }
    
    @Test
    void testGetChildren() {
        // Given - Create a parent folder
        Node parent = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setName("Parent Folder")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        // Create some children
        filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setParentId(parent.getId())
                .setName("Child 1")
                .setType(Node.NodeType.FILE)
                .build()
        ).await().indefinitely();
        
        filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setParentId(parent.getId())
                .setName("Child 2")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        // When - Get children
        GetChildrenRequest request = GetChildrenRequest.newBuilder()
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
                .setName("Original Name")
                .setType(Node.NodeType.FILE)
                .build()
        ).await().indefinitely();
        
        // When - Update it
        UpdateNodeRequest updateRequest = UpdateNodeRequest.newBuilder()
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
                .setName("To Delete")
                .setType(Node.NodeType.FILE)
                .build()
        ).await().indefinitely();
        
        // When - Delete it
        DeleteNodeRequest deleteRequest = DeleteNodeRequest.newBuilder()
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
                .setName("Folder to Delete")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        // Add children
        for (int i = 0; i < 3; i++) {
            filesystemService.createNode(
                CreateNodeRequest.newBuilder()
                    .setParentId(folder.getId())
                    .setName("Child " + i)
                    .setType(Node.NodeType.FILE)
                    .build()
            ).await().indefinitely();
        }
        
        // When - Delete recursively
        DeleteNodeRequest deleteRequest = DeleteNodeRequest.newBuilder()
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
        String testPrefix = "format-test-" + System.currentTimeMillis() + "-";
        
        Node folder1 = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setName(testPrefix + "Test Folder 1")
                .setType(Node.NodeType.FOLDER)
                .build()
        ).await().indefinitely();
        
        Node file1 = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setParentId(folder1.getId())
                .setName(testPrefix + "test-file-1.txt")
                .setType(Node.NodeType.FILE)
                .build()
        ).await().indefinitely();
        
        Node file2 = filesystemService.createNode(
            CreateNodeRequest.newBuilder()
                .setParentId(folder1.getId())
                .setName(testPrefix + "test-file-2.txt")
                .setType(Node.NodeType.FILE)
                .build()
        ).await().indefinitely();
        
        // Verify files exist
        assertThat("File 1 with ID " + file1.getId() + " should exist before format operation", 
            filesystemService.getNode(GetNodeRequest.newBuilder().setId(file1.getId()).build())
                .await().indefinitely(), is(notNullValue()));
        assertThat("File 2 with ID " + file2.getId() + " should exist before format operation", 
            filesystemService.getNode(GetNodeRequest.newBuilder().setId(file2.getId()).build())
                .await().indefinitely(), is(notNullValue()));
        
        // When - Format the filesystem with dry run first
        FormatFilesystemRequest dryRunRequest = FormatFilesystemRequest.newBuilder()
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
            filesystemService.getNode(GetNodeRequest.newBuilder().setId(file1.getId()).build())
                .await().indefinitely(), is(notNullValue()));
        assertThat("File 2 with ID " + file2.getId() + " should still exist after dry run since no actual deletion occurred", 
            filesystemService.getNode(GetNodeRequest.newBuilder().setId(file2.getId()).build())
                .await().indefinitely(), is(notNullValue()));
        
        // When - Actually format the filesystem
        FormatFilesystemRequest formatRequest = FormatFilesystemRequest.newBuilder()
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
        Node deletedFile1 = filesystemService.getNode(GetNodeRequest.newBuilder().setId(file1.getId()).build())
            .onFailure().recoverWithNull()
            .await().indefinitely();
        assertThat("File 1 with ID " + file1.getId() + " should be null after format operation deleted it", 
            deletedFile1, is(nullValue()));
        
        Node deletedFile2 = filesystemService.getNode(GetNodeRequest.newBuilder().setId(file2.getId()).build())
            .onFailure().recoverWithNull()
            .await().indefinitely();
        assertThat("File 2 with ID " + file2.getId() + " should be null after format operation deleted it", 
            deletedFile2, is(nullValue()));
        
        Node deletedFolder = filesystemService.getNode(GetNodeRequest.newBuilder().setId(folder1.getId()).build())
            .onFailure().recoverWithNull()
            .await().indefinitely();
        assertThat("Folder 1 with ID " + folder1.getId() + " should be null after format operation deleted it", 
            deletedFolder, is(nullValue()));
    }
}