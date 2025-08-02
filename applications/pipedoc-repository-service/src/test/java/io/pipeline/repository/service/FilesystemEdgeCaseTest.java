package io.pipeline.repository.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.pipeline.repository.filesystem.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import com.google.protobuf.InvalidProtocolBufferException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for edge cases and error handling in FilesystemService.
 */
@QuarkusTest
public class FilesystemEdgeCaseTest extends IsolatedRedisTest {
    
    @GrpcClient("test-filesystem")
    MutinyFilesystemServiceGrpc.MutinyFilesystemServiceStub filesystemService;
    
    @BeforeEach
    void setUp(TestInfo testInfo) {
        super.setupTestIsolation(testInfo);
    }
    
    @Test
    void testUpdateNonExistentNode() {
        UpdateNodeRequest request = UpdateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId("non-existent-id")
            .setName("new-name")
            .build();
        
        Throwable error = filesystemService.updateNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.NOT_FOUND.getCode()));
    }
    
    @Test
    void testMoveNodeToItself() {
        // Create a folder
        Node folder = createFolder("test-folder");
        
        // Try to move it to itself
        MoveNodeRequest request = MoveNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setNodeId(folder.getId())
            .setNewParentId(folder.getId())
            .build();
        
        Throwable error = filesystemService.moveNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.INVALID_ARGUMENT.getCode()));
    }
    
    @Test
    void testMoveNodeToOwnChild() {
        // Create nested folders
        Node parent = createFolder("parent");
        Node child = createFolderIn("child", parent.getId());
        Node grandchild = createFolderIn("grandchild", child.getId());
        
        // Try to move parent to grandchild
        MoveNodeRequest request = MoveNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setNodeId(parent.getId())
            .setNewParentId(grandchild.getId())
            .build();
        
        Throwable error = filesystemService.moveNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.INVALID_ARGUMENT.getCode()));
    }
    
    @Test
    void testCopyFolderIntoItself() {
        // Create a folder with content
        Node folder = createFolder("test-folder");
        createFileInFolder("file.txt", folder.getId());
        
        // Try to copy folder into itself
        CopyNodeRequest request = CopyNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setNodeId(folder.getId())
            .setTargetParentId(folder.getId())
            .setDeep(true)
            .build();
        
        Throwable error = filesystemService.copyNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.INVALID_ARGUMENT.getCode()));
    }
    
    @Test
    void testUnicodeFilenames() {
        // Test various unicode characters in filenames
        String[] unicodeNames = {
            "文件名.txt",  // Chinese
            "файл.txt",    // Russian
            "αρχείο.txt",  // Greek
            "ملف.txt",     // Arabic
            "파일.txt",     // Korean
            "🎉🎊📁.txt",  // Emojis
            "file with spaces and émojis 🚀.txt"
        };
        
        for (String name : unicodeNames) {
            Node file = createFile(name);
            assertThat(file.getName(), is(name));
            
            // Verify we can retrieve it
            Node retrieved = getNode(file.getId());
            assertThat(retrieved.getName(), is(name));
        }
    }
    
    @Test
    void testSpecialCharactersInMetadata() {
        Node file = createFile("test.txt");
        
        UpdateNodeRequest request = UpdateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId(file.getId())
            .putMetadata("special", "Line1\nLine2\tTab\r\nCRLF")
            .putMetadata("unicode", "Hello 世界 🌍")
            .putMetadata("quotes", "He said \"Hello\" and 'Goodbye'")
            .build();
        
        Node updated = filesystemService.updateNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(updated.getMetadataMap().get("special"), is("Line1\nLine2\tTab\r\nCRLF"));
        assertThat(updated.getMetadataMap().get("unicode"), is("Hello 世界 🌍"));
        assertThat(updated.getMetadataMap().get("quotes"), is("He said \"Hello\" and 'Goodbye'"));
    }
    
    @Test
    void testCrossDriveOperation() {
        // Create nodes in different drives
        String drive1 = getTestDrive();
        String drive2 = "other-drive-" + System.currentTimeMillis();
        
        // Create drive2
        CreateDriveRequest createDriveReq = CreateDriveRequest.newBuilder()
            .setName(drive2)
            .setDescription("Second test drive")
            .build();
        
        filesystemService.createDrive(createDriveReq)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem();
        
        // Create a node in drive1
        CreateNodeRequest nodeReq = CreateNodeRequest.newBuilder()
            .setDrive(drive1)
            .setName("file-in-drive1.txt")
            .setType(Node.NodeType.FILE)
            .build();
        
        Node fileInDrive1 = filesystemService.createNode(nodeReq)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Try to access it from drive2 (should fail)
        GetNodeRequest getReq = GetNodeRequest.newBuilder()
            .setDrive(drive2)
            .setId(fileInDrive1.getId())
            .build();
        
        Throwable error = filesystemService.getNode(getReq)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.NOT_FOUND.getCode()));
    }
    
    @Test
    void testSearchWithInvalidPattern() {
        // Create some test files
        createFile("test1.txt");
        createFile("test2.txt");
        
        // Search with invalid regex pattern
        SearchNodesRequest request = SearchNodesRequest.newBuilder()
            .setDrive(getTestDrive())
            .setQuery("[invalid")  // Unclosed bracket
            .build();
        
        // Should still work (treated as literal string)
        SearchNodesResponse response = filesystemService.searchNodes(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Should find nothing since no file contains "[invalid"
        assertThat(response.getNodesCount(), is(0));
    }
    
    @Test
    void testVeryLargePayload() {
        // Create a large payload (1MB of text)
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 1024 * 1024; i++) {
            largeText.append('A');
        }
        
        StringValue largePayload = StringValue.newBuilder()
            .setValue(largeText.toString())
            .build();
        
        CreateNodeRequest request = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName("large-payload.txt")
            .setType(Node.NodeType.FILE)
            .setPayload(Any.pack(largePayload))
            .build();
        
        Node created = filesystemService.createNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify we can retrieve it
        Node retrieved = getNode(created.getId());
        assertThat(retrieved.getPayload().getTypeUrl(), containsString("StringValue"));
        
        // Unpack and verify size
        try {
            StringValue unpacked = retrieved.getPayload().unpack(StringValue.class);
            assertThat(unpacked.getValue().length(), is(1024 * 1024));
        } catch (InvalidProtocolBufferException e) {
            fail("Failed to unpack StringValue: " + e.getMessage());
        }
    }
    
    @Test
    void testDeleteNonExistentNode() {
        DeleteNodeRequest request = DeleteNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId("non-existent-id")
            .build();
        
        Throwable error = filesystemService.deleteNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.NOT_FOUND.getCode()));
    }
    
    @Test
    void testGetPathOfNonExistentNode() {
        GetPathRequest request = GetPathRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId("non-existent-id")
            .build();
        
        Throwable error = filesystemService.getPath(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.NOT_FOUND.getCode()));
    }
    
    @Test
    void testEmptyDriveName() {
        CreateNodeRequest request = CreateNodeRequest.newBuilder()
            .setDrive("")
            .setName("test.txt")
            .setType(Node.NodeType.FILE)
            .build();
        
        Throwable error = filesystemService.createNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.INVALID_ARGUMENT.getCode()));
    }
    
    @Test
    void testNullNodeName() {
        CreateNodeRequest request = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setType(Node.NodeType.FILE)
            // name is not set (null)
            .build();
        
        Throwable error = filesystemService.createNode(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.INVALID_ARGUMENT.getCode()));
    }
    
    // Helper methods
    private Node createFolder(String name) {
        return createFolder(name, null);
    }
    
    private Node createFolderIn(String name, String parentId) {
        return createFolder(name, parentId);
    }
    
    private Node createFolder(String name, String parentId) {
        CreateNodeRequest.Builder builder = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName(name)
            .setType(Node.NodeType.FOLDER);
        
        if (parentId != null) {
            builder.setParentId(parentId);
        }
        
        return filesystemService.createNode(builder.build())
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
    }
    
    private Node createFile(String name) {
        return createFileInFolder(name, null);
    }
    
    private Node createFileInFolder(String name, String parentId) {
        CreateNodeRequest.Builder builder = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName(name)
            .setType(Node.NodeType.FILE);
        
        if (parentId != null) {
            builder.setParentId(parentId);
        }
        
        return filesystemService.createNode(builder.build())
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
    }
    
    private Node getNode(String id) {
        return filesystemService.getNode(
            GetNodeRequest.newBuilder()
                .setDrive(getTestDrive())
                .setId(id)
                .build()
        ).subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
    }
}