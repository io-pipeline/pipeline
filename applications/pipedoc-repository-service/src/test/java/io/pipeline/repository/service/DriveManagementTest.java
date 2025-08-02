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

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for drive management operations.
 */
@QuarkusTest
public class DriveManagementTest extends IsolatedRedisTest {
    
    @GrpcClient("test-filesystem")
    MutinyFilesystemServiceGrpc.MutinyFilesystemServiceStub filesystemService;
    
    @BeforeEach
    void setUp(TestInfo testInfo) {
        super.setupTestIsolation(testInfo);
    }
    
    @Test
    void testCreateAndGetDrive() {
        // Create a drive
        String driveName = "test-drive-" + System.currentTimeMillis();
        CreateDriveRequest createRequest = CreateDriveRequest.newBuilder()
            .setName(driveName)
            .setDescription("Test drive for unit tests")
            .putMetadata("environment", "test")
            .putMetadata("owner", "test-user")
            .build();
        
        Drive createdDrive = filesystemService.createDrive(createRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify creation
        assertThat(createdDrive.getName(), is(driveName));
        assertThat(createdDrive.getDescription(), is("Test drive for unit tests"));
        assertThat(createdDrive.getMetadataMap().get("environment"), is("test"));
        assertThat(createdDrive.getMetadataMap().get("owner"), is("test-user"));
        assertNotNull(createdDrive.getCreatedAt());
        assertThat(createdDrive.getTotalSize(), is(0L));
        assertThat(createdDrive.getNodeCount(), is(0L));
        
        // Get the drive
        GetDriveRequest getRequest = GetDriveRequest.newBuilder()
            .setName(driveName)
            .build();
        
        Drive retrievedDrive = filesystemService.getDrive(getRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify retrieval
        assertThat(retrievedDrive.getName(), is(createdDrive.getName()));
        assertThat(retrievedDrive.getDescription(), is(createdDrive.getDescription()));
        assertThat(retrievedDrive.getCreatedAt(), is(createdDrive.getCreatedAt()));
    }
    
    @Test
    void testCreateDriveDuplicateName() {
        String driveName = "duplicate-test-" + System.currentTimeMillis();
        
        // Create first drive
        CreateDriveRequest createRequest = CreateDriveRequest.newBuilder()
            .setName(driveName)
            .setDescription("First drive")
            .build();
        
        filesystemService.createDrive(createRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem();
        
        // Try to create duplicate
        CreateDriveRequest duplicateRequest = CreateDriveRequest.newBuilder()
            .setName(driveName)
            .setDescription("Duplicate drive")
            .build();
        
        Throwable error = filesystemService.createDrive(duplicateRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.ALREADY_EXISTS.getCode()));
    }
    
    @Test
    void testListDrives() {
        // Create multiple drives
        String prefix = "list-test-" + System.currentTimeMillis() + "-";
        
        for (int i = 0; i < 3; i++) {
            CreateDriveRequest request = CreateDriveRequest.newBuilder()
                .setName(prefix + i)
                .setDescription("Test drive " + i)
                .build();
            
            filesystemService.createDrive(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        }
        
        // List all drives
        ListDrivesRequest listRequest = ListDrivesRequest.newBuilder()
            .setPageSize(10)
            .build();
        
        ListDrivesResponse response = filesystemService.listDrives(listRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify we have at least our 3 drives
        assertThat(response.getDrivesCount(), is(greaterThanOrEqualTo(3)));
        
        // Count our test drives
        long testDriveCount = response.getDrivesList().stream()
            .filter(d -> d.getName().startsWith(prefix))
            .count();
        assertThat(testDriveCount, is(3L));
    }
    
    @Test
    void testListDrivesWithPagination() {
        // Create enough drives to test pagination
        String prefix = "page-test-" + System.currentTimeMillis() + "-";
        
        for (int i = 0; i < 5; i++) {
            CreateDriveRequest request = CreateDriveRequest.newBuilder()
                .setName(prefix + String.format("%02d", i))
                .setDescription("Page test drive " + i)
                .build();
            
            filesystemService.createDrive(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        }
        
        // Get first page
        ListDrivesRequest firstPageRequest = ListDrivesRequest.newBuilder()
            .setPageSize(2)
            .setFilter("name:" + prefix)
            .build();
        
        ListDrivesResponse firstPage = filesystemService.listDrives(firstPageRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(firstPage.getDrivesCount(), is(2));
        assertNotNull(firstPage.getNextPageToken());
        assertThat(firstPage.getTotalCount(), is(5));
        
        // Get next page
        ListDrivesRequest nextPageRequest = ListDrivesRequest.newBuilder()
            .setPageSize(2)
            .setPageToken(firstPage.getNextPageToken())
            .setFilter("name:" + prefix)
            .build();
        
        ListDrivesResponse nextPage = filesystemService.listDrives(nextPageRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(nextPage.getDrivesCount(), is(2));
    }
    
    @Test
    void testDeleteDrive() {
        String driveName = "delete-test-" + System.currentTimeMillis();
        
        // Create drive
        CreateDriveRequest createRequest = CreateDriveRequest.newBuilder()
            .setName(driveName)
            .setDescription("Drive to be deleted")
            .build();
        
        filesystemService.createDrive(createRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem();
        
        // Add some content
        CreateNodeRequest nodeRequest = CreateNodeRequest.newBuilder()
            .setDrive(driveName)
            .setName("test-file.txt")
            .setType(Node.NodeType.FILE)
            .build();
        
        filesystemService.createNode(nodeRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem();
        
        // Delete without confirmation (should fail)
        DeleteDriveRequest deleteRequestNoConfirm = DeleteDriveRequest.newBuilder()
            .setName(driveName)
            .build();
        
        Throwable error = filesystemService.deleteDrive(deleteRequestNoConfirm)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.FAILED_PRECONDITION.getCode()));
        
        // Delete with proper confirmation
        DeleteDriveRequest deleteRequest = DeleteDriveRequest.newBuilder()
            .setName(driveName)
            .setConfirmation("DELETE_DRIVE_DATA")
            .build();
        
        DeleteDriveResponse deleteResponse = filesystemService.deleteDrive(deleteRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat(deleteResponse.getSuccess(), is(true));
        assertThat(deleteResponse.getNodesDeleted(), is(1));
        
        // Verify drive is gone
        GetDriveRequest getRequest = GetDriveRequest.newBuilder()
            .setName(driveName)
            .build();
        
        Throwable getError = filesystemService.getDrive(getRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(getError, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException getSre = (StatusRuntimeException) getError;
        assertThat(getSre.getStatus().getCode(), is(Status.NOT_FOUND.getCode()));
    }
    
    @Test
    void testDeleteNonExistentDrive() {
        DeleteDriveRequest deleteRequest = DeleteDriveRequest.newBuilder()
            .setName("non-existent-drive")
            .setConfirmation("DELETE_DRIVE_DATA")
            .build();
        
        Throwable error = filesystemService.deleteDrive(deleteRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .getFailure();
        
        assertThat(error, instanceOf(StatusRuntimeException.class));
        StatusRuntimeException sre = (StatusRuntimeException) error;
        assertThat(sre.getStatus().getCode(), is(Status.NOT_FOUND.getCode()));
    }
    
    @Test
    void testDriveStatistics() {
        String driveName = "stats-test-" + System.currentTimeMillis();
        
        // Create drive
        CreateDriveRequest createRequest = CreateDriveRequest.newBuilder()
            .setName(driveName)
            .setDescription("Drive for statistics test")
            .build();
        
        Drive drive = filesystemService.createDrive(createRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Initial stats
        assertThat(drive.getNodeCount(), is(0L));
        assertThat(drive.getTotalSize(), is(0L));
        
        // Add content
        CreateNodeRequest folderRequest = CreateNodeRequest.newBuilder()
            .setDrive(driveName)
            .setName("test-folder")
            .setType(Node.NodeType.FOLDER)
            .build();
        
        Node folder = filesystemService.createNode(folderRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Add files
        for (int i = 0; i < 3; i++) {
            CreateNodeRequest fileRequest = CreateNodeRequest.newBuilder()
                .setDrive(driveName)
                .setName("file-" + i + ".txt")
                .setType(Node.NodeType.FILE)
                .setParentId(folder.getId())
                .build();
            
            filesystemService.createNode(fileRequest)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();
        }
        
        // Get updated drive info
        GetDriveRequest getRequest = GetDriveRequest.newBuilder()
            .setName(driveName)
            .build();
        
        Drive updatedDrive = filesystemService.getDrive(getRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Verify stats updated
        assertThat(updatedDrive.getNodeCount(), is(4L)); // 1 folder + 3 files
        assertNotNull(updatedDrive.getLastAccessed());
    }
    
    @Test
    void testInvalidDriveName() {
        // Test drive names with invalid characters
        List<String> invalidNames = List.of(
            "drive:with:colons",
            "drive/with/slashes",
            "drive\\with\\backslashes",
            "",
            "   "
        );
        
        for (String invalidName : invalidNames) {
            CreateDriveRequest request = CreateDriveRequest.newBuilder()
                .setName(invalidName)
                .setDescription("Invalid drive name test")
                .build();
            
            Throwable error = filesystemService.createDrive(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();
            
            assertThat(error, instanceOf(StatusRuntimeException.class));
            StatusRuntimeException sre = (StatusRuntimeException) error;
            assertThat(sre.getStatus().getCode(), is(Status.INVALID_ARGUMENT.getCode()));
        }
    }
}