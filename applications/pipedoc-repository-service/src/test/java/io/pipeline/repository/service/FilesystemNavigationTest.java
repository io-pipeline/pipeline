package io.pipeline.repository.service;

import com.google.protobuf.Any;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.repository.filesystem.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for filesystem navigation and manipulation operations.
 */
@QuarkusTest
public class FilesystemNavigationTest extends IsolatedRedisTest {
    
    @GrpcClient("test-filesystem")
    MutinyFilesystemServiceGrpc.MutinyFilesystemServiceStub filesystemService;
    
    @BeforeEach
    void setUp(TestInfo testInfo) {
        super.setupTestIsolation(testInfo);
    }
    
    @Test
    void testMoveNode() {
        // Create folder structure
        Node folder1 = createFolder("Folder 1");
        Node folder2 = createFolder("Folder 2");
        Node file = createFileInFolder("test.txt", folder1.getId());
        
        // Verify initial location
        GetChildrenResponse folder1Children = getChildren(folder1.getId());
        assertThat(folder1Children.getNodesCount(), is(1));
        assertThat(folder1Children.getNodes(0).getId(), is(file.getId()));
        
        // Move file to folder2
        MoveNodeRequest moveRequest = MoveNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setNodeId(file.getId())
            .setNewParentId(folder2.getId())
            .build();
        
        Node movedFile = filesystemService.moveNode(moveRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify file was moved
        assertThat(movedFile.getParentId(), is(folder2.getId()));
        assertThat(movedFile.getName(), is("test.txt"));
        
        // Verify folder1 is now empty
        GetChildrenResponse folder1ChildrenAfter = getChildren(folder1.getId());
        assertThat(folder1ChildrenAfter.getNodesCount(), is(0));
        
        // Verify folder2 contains the file
        GetChildrenResponse folder2Children = getChildren(folder2.getId());
        assertThat(folder2Children.getNodesCount(), is(1));
        assertThat(folder2Children.getNodes(0).getId(), is(file.getId()));
    }
    
    @Test
    void testMoveNodeWithRename() {
        Node folder = createFolder("Target Folder");
        Node file = createFile("original.txt");
        
        // Move and rename
        MoveNodeRequest moveRequest = MoveNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setNodeId(file.getId())
            .setNewParentId(folder.getId())
            .setNewName("renamed.txt")
            .build();
        
        Node movedFile = filesystemService.moveNode(moveRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(movedFile.getParentId(), is(folder.getId()));
        assertThat(movedFile.getName(), is("renamed.txt"));
    }
    
    @Test
    void testCopyNode() {
        // Create source structure
        Node sourceFolder = createFolder("Source");
        Node file = createFileInFolder("document.txt", sourceFolder.getId());
        Node targetFolder = createFolder("Target");
        
        // Copy file
        CopyNodeRequest copyRequest = CopyNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setNodeId(file.getId())
            .setTargetParentId(targetFolder.getId())
            .build();
        
        Node copiedFile = filesystemService.copyNode(copyRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify copy
        assertNotEquals(file.getId(), copiedFile.getId());
        assertThat(copiedFile.getParentId(), is(targetFolder.getId()));
        assertThat(copiedFile.getName(), is("document.txt"));
        
        // Verify original still exists
        Node original = getNode(file.getId());
        assertThat(original.getParentId(), is(sourceFolder.getId()));
        
        // Verify both locations have the file
        assertThat(getChildren(sourceFolder.getId()).getNodesCount(), is(1));
        assertThat(getChildren(targetFolder.getId()).getNodesCount(), is(1));
    }
    
    @Test
    void testCopyNodeWithRename() {
        Node file = createFile("original.txt");
        Node targetFolder = createFolder("Target");
        
        // Copy with rename
        CopyNodeRequest copyRequest = CopyNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setNodeId(file.getId())
            .setTargetParentId(targetFolder.getId())
            .setNewName("copy.txt")
            .build();
        
        Node copiedFile = filesystemService.copyNode(copyRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(copiedFile.getName(), is("copy.txt"));
        assertThat(copiedFile.getParentId(), is(targetFolder.getId()));
    }
    
    @Test
    void testDeepCopyFolder() {
        // Create nested structure
        Node rootFolder = createFolder("Root");
        Node subFolder = createFolderIn("SubFolder", rootFolder.getId());
        Node file1 = createFileInFolder("file1.txt", rootFolder.getId());
        Node file2 = createFileInFolder("file2.txt", subFolder.getId());
        Node targetFolder = createFolder("Target");
        
        // Deep copy the root folder
        CopyNodeRequest copyRequest = CopyNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setNodeId(rootFolder.getId())
            .setTargetParentId(targetFolder.getId())
            .setDeep(true)
            .build();
        
        Node copiedRoot = filesystemService.copyNode(copyRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify structure was copied
        assertNotEquals(rootFolder.getId(), copiedRoot.getId());
        assertThat(copiedRoot.getName(), is("Root"));
        
        // Check copied structure
        GetChildrenResponse copiedRootChildren = getChildren(copiedRoot.getId());
        assertThat(copiedRootChildren.getNodesCount(), is(2)); // file1 and SubFolder
        
        // Find the copied subfolder
        Node copiedSubFolder = copiedRootChildren.getNodesList().stream()
            .filter(n -> n.getType() == Node.NodeType.FOLDER)
            .findFirst()
            .orElseThrow();
        
        assertThat(copiedSubFolder.getName(), is("SubFolder"));
        
        // Check subfolder contents
        GetChildrenResponse copiedSubChildren = getChildren(copiedSubFolder.getId());
        assertThat(copiedSubChildren.getNodesCount(), is(1));
        assertThat(copiedSubChildren.getNodes(0).getName(), is("file2.txt"));
    }
    
    @Test
    void testGetPath() {
        // Create deep structure
        Node folder1 = createFolder("Level1");
        Node folder2 = createFolderIn("Level2", folder1.getId());
        Node folder3 = createFolderIn("Level3", folder2.getId());
        Node file = createFileInFolder("deep.txt", folder3.getId());
        
        // Get path for the file
        GetPathRequest pathRequest = GetPathRequest.newBuilder()
            .setDrive(getTestDrive())
            .setId(file.getId())
            .build();
        
        GetPathResponse pathResponse = filesystemService.getPath(pathRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify path includes all ancestors
        List<Node> ancestors = pathResponse.getAncestorsList();
        assertThat(ancestors.size(), is(4)); // root -> folder1 -> folder2 -> folder3
        
        // Check order (should be from root to immediate parent)
        assertThat(ancestors.get(0).getName(), is("Level1"));
        assertThat(ancestors.get(1).getName(), is("Level2"));
        assertThat(ancestors.get(2).getName(), is("Level3"));
        assertThat(ancestors.get(3).getName(), is("deep.txt"));
    }
    
    @Test
    void testGetTree() {
        // Create tree structure
        Node root = createFolder("Root");
        Node child1 = createFolderIn("Child1", root.getId());
        Node child2 = createFolderIn("Child2", root.getId());
        Node grandchild1 = createFileInFolder("file1.txt", child1.getId());
        Node grandchild2 = createFileInFolder("file2.txt", child2.getId());
        
        // Get tree
        GetTreeRequest treeRequest = GetTreeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setRootId(root.getId())
            .setMaxDepth(3)
            .build();
        
        GetTreeResponse treeResponse = filesystemService.getTree(treeRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify tree structure
        assertThat(treeResponse.getRoot().getId(), is(root.getId()));
        assertThat(treeResponse.getChildrenCount(), is(2)); // Two child folders
        
        // Verify children
        TreeNode child1Node = treeResponse.getChildrenList().stream()
            .filter(tn -> tn.getNode().getName().equals("Child1"))
            .findFirst()
            .orElseThrow();
        
        assertThat(child1Node.getChildrenCount(), is(1));
        assertThat(child1Node.getChildren(0).getNode().getName(), is("file1.txt"));
    }
    
    @Test
    void testSearchNodes() {
        // Create searchable content
        Node folder = createFolder("Documents");
        
        PipeDoc doc1 = PipeDoc.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle("Important Report")
            .setBody("This is a very important quarterly report")
            .build();
        
        PipeDoc doc2 = PipeDoc.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle("Meeting Notes")
            .setBody("Notes from the important meeting")
            .build();
        
        PipeDoc doc3 = PipeDoc.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle("Random Document")
            .setBody("Some random content here")
            .build();
        
        createFileWithPayload("important-report.pb", folder.getId(), doc1);
        createFileWithPayload("important-notes.pb", folder.getId(), doc2);
        createFileWithPayload("random.pb", folder.getId(), doc3);
        
        // Search for "important" in filenames
        SearchNodesRequest searchRequest = SearchNodesRequest.newBuilder()
            .setDrive(getTestDrive())
            .setQuery("important")
            .addPaths(folder.getId())
            .build();
        
        SearchNodesResponse searchResponse = filesystemService.searchNodes(searchRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Should find the first two documents
        assertThat(searchResponse.getNodesCount(), is(2));
        assertThat(searchResponse.getTotalCount(), is(2));
        
        List<String> foundNames = searchResponse.getNodesList().stream()
            .map(Node::getName)
            .toList();
        assertThat(foundNames, containsInAnyOrder("important-report.pb", "important-notes.pb"));
    }
    
    @Test
    void testSearchNodesWithTypeFilter() {
        Node folder = createFolder("Mixed");
        createFileInFolder("file1.txt", folder.getId());
        createFolderIn("subfolder", folder.getId());
        createFileInFolder("file2.txt", folder.getId());
        
        // Search only for files
        SearchNodesRequest searchRequest = SearchNodesRequest.newBuilder()
            .setDrive(getTestDrive())
            .setQuery("file")
            .addTypes(Node.NodeType.FILE)
            .build();
        
        SearchNodesResponse searchResponse = filesystemService.searchNodes(searchRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Should only find files
        assertThat(searchResponse.getNodesCount(), is(2));
        assertTrue(searchResponse.getNodesList().stream()
            .allMatch(n -> n.getType() == Node.NodeType.FILE));
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
    
    private Node createFileWithPayload(String name, String parentId, PipeDoc doc) {
        CreateNodeRequest.Builder builder = CreateNodeRequest.newBuilder()
            .setDrive(getTestDrive())
            .setName(name)
            .setType(Node.NodeType.FILE)
            .setPayload(Any.pack(doc));
        
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
    
    private GetChildrenResponse getChildren(String parentId) {
        GetChildrenRequest.Builder builder = GetChildrenRequest.newBuilder()
            .setDrive(getTestDrive());
        
        if (parentId != null) {
            builder.setParentId(parentId);
        }
        
        return filesystemService.getChildren(builder.build())
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
    }
}