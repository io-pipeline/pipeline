package io.pipeline.repository.service;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.pipeline.repository.filesystem.*;
import io.vertx.mutiny.redis.client.Command;
import io.pipeline.repository.redis.RedisFilesystemNode;
import io.pipeline.repository.config.NamespacedRedisKeyService;
import io.quarkus.grpc.GrpcService;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.hash.ReactiveHashCommands;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.set.ReactiveSetCommands;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Drive-aware implementation of the FilesystemService.
 * Each drive is completely isolated - no path traversal between drives.
 */
@GrpcService
public class FilesystemServiceImpl implements FilesystemService {
    
    private static final Logger LOG = Logger.getLogger(FilesystemServiceImpl.class);
    
    // Key prefixes within a drive
    private static final String NODE_PREFIX = "node:";
    private static final String CHILDREN_PREFIX = "children:";
    private static final String ROOT_NODES = "roots";
    
    @Inject
    ReactiveRedisDataSource redis;
    
    @Inject
    GenericRepositoryService payloadRepository;
    
    @Inject
    SvgValidator svgValidator;
    
    @Inject
    NamespacedRedisKeyService keyService;
    
    private ReactiveHashCommands<String, String, String> hash() {
        return redis.hash(String.class, String.class, String.class);
    }
    
    private ReactiveSetCommands<String, String> set() {
        return redis.set(String.class, String.class);
    }
    
    private ReactiveKeyCommands<String> keys() {
        return redis.key(String.class);
    }
    
    // Helper methods for drive-aware keys
    private String nodeKey(String drive, String nodeId) {
        return keyService.filesystemKey(drive, NODE_PREFIX + nodeId);
    }
    
    private String childrenKey(String drive, String parentId) {
        return keyService.filesystemKey(drive, CHILDREN_PREFIX + parentId);
    }
    
    private String rootNodesKey(String drive) {
        return keyService.filesystemKey(drive, ROOT_NODES);
    }
    
    private String extractNodeId(String key) {
        if (key == null) return null;
        int nodeIndex = key.lastIndexOf(NODE_PREFIX);
        if (nodeIndex == -1) return null;
        return key.substring(nodeIndex + NODE_PREFIX.length());
    }
    
    @Override
    public Uni<Drive> createDrive(CreateDriveRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive name is required")
                )
            );
        }
        
        // Validate drive name - no special characters
        String driveName = request.getName().trim();
        if (driveName.contains(":") || driveName.contains("/") || driveName.contains("\\") || driveName.isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive name cannot contain special characters (: / \\) and cannot be empty")
                )
            );
        }
        String driveMetaKey = keyService.driveMetadataKey(driveName);
        
        // Check if drive already exists
        return keys().exists(driveMetaKey)
            .flatMap(exists -> {
                if (exists) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.ALREADY_EXISTS.withDescription("Drive already exists: " + driveName)
                        )
                    );
                }
                
                // Create drive metadata
                Map<String, String> driveMeta = new HashMap<>();
                driveMeta.put("name", driveName);
                driveMeta.put("description", request.getDescription());
                driveMeta.put("created_at", Instant.now().toString());
                driveMeta.put("last_accessed", Instant.now().toString());
                driveMeta.put("total_size", "0");
                driveMeta.put("node_count", "0");
                
                // Add custom metadata
                if (request.getMetadataMap() != null) {
                    request.getMetadataMap().forEach((k, v) -> 
                        driveMeta.put("meta:" + k, v));
                }
                
                // Store drive metadata
                return hash().hset(driveMetaKey, driveMeta)
                    .flatMap(v -> set().sadd(keyService.allDrivesKey(), driveName))
                    .map(v -> buildDriveProto(driveName, driveMeta));
            });
    }
    
    @Override
    public Uni<Drive> getDrive(GetDriveRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive name is required")
                )
            );
        }
        
        String driveMetaKey = keyService.driveMetadataKey(request.getName());
        return hash().hgetall(driveMetaKey)
            .map(meta -> {
                if (meta.isEmpty()) {
                    throw new StatusRuntimeException(
                        Status.NOT_FOUND.withDescription("Drive not found: " + request.getName())
                    );
                }
                
                // Update last accessed
                hash().hset(driveMetaKey, "last_accessed", Instant.now().toString())
                    .subscribe().with(v -> {});
                
                return buildDriveProto(request.getName(), meta);
            });
    }
    
    @Override
    public Uni<ListDrivesResponse> listDrives(ListDrivesRequest request) {
        return set().smembers(keyService.allDrivesKey())
            .flatMap(driveNames -> {
                if (driveNames.isEmpty()) {
                    return Uni.createFrom().item(
                        ListDrivesResponse.newBuilder()
                            .setTotalCount(0)
                            .build()
                    );
                }
                
                // Apply filter if provided
                Stream<String> filteredNames = driveNames.stream();
                if (request.getFilter() != null && !request.getFilter().isEmpty()) {
                    String filter = request.getFilter();
                    if (filter.startsWith("name:")) {
                        String nameFilter = filter.substring(5);
                        filteredNames = filteredNames.filter(name -> name.contains(nameFilter));
                    }
                }
                
                List<String> filteredList = filteredNames.sorted().collect(Collectors.toList());
                final int totalCount = filteredList.size();
                
                // Apply pagination
                final int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 10;
                int initialOffset = 0;
                
                if (request.getPageToken() != null && !request.getPageToken().isEmpty()) {
                    try {
                        initialOffset = Integer.parseInt(request.getPageToken());
                    } catch (NumberFormatException e) {
                        // Ignore bad page tokens
                    }
                }
                
                final int offset = initialOffset;
                
                List<String> pagedNames = filteredList.stream()
                    .skip(offset)
                    .limit(pageSize)
                    .collect(Collectors.toList());
                
                // Load drive metadata for each drive
                List<Uni<Drive>> driveUnis = pagedNames.stream()
                    .map(name -> hash().hgetall(keyService.driveMetadataKey(name))
                        .map(meta -> buildDriveProto(name, meta)))
                    .collect(Collectors.toList());
                
                return Uni.combine().all().unis(driveUnis)
                    .with(drives -> {
                        List<Drive> driveList = drives.stream()
                            .map(d -> (Drive) d)
                            .collect(Collectors.toList());
                        
                        ListDrivesResponse.Builder response = ListDrivesResponse.newBuilder()
                            .addAllDrives(driveList)
                            .setTotalCount(totalCount);
                        
                        // Add next page token if there are more results
                        if (offset + pageSize < totalCount) {
                            response.setNextPageToken(String.valueOf(offset + pageSize));
                        }
                        
                        return response.build();
                    });
            });
    }
    
    @Override
    public Uni<DeleteDriveResponse> deleteDrive(DeleteDriveRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive name is required")
                )
            );
        }
        
        String driveName = request.getName();
        
        // Check if drive exists first
        return hash().hgetall(keyService.driveMetadataKey(driveName))
            .flatMap(meta -> {
                if (meta.isEmpty()) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("Drive not found: " + driveName)
                        )
                    );
                }
                
                // Check confirmation
                if (!"DELETE_DRIVE_DATA".equals(request.getConfirmation())) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.FAILED_PRECONDITION.withDescription("Invalid confirmation. Must be 'DELETE_DRIVE_DATA'")
                        )
                    );
                }
                
                // Format the drive to delete all its data
                return formatFilesystem(FormatFilesystemRequest.newBuilder()
                        .setDrive(driveName)
                        .setConfirmation("DELETE_FILESYSTEM_DATA")
                        .setDryRun(false)
                        .build())
                    .flatMap(formatResult -> {
                        // Remove drive from list
                        return set().srem(keyService.allDrivesKey(), driveName)
                            .flatMap(v -> keys().del(keyService.driveMetadataKey(driveName)))
                            .map(v -> DeleteDriveResponse.newBuilder()
                                .setSuccess(true)
                                .setMessage("Deleted drive: " + driveName)
                                .setNodesDeleted(formatResult.getNodesDeleted() + formatResult.getFoldersDeleted())
                                .build());
                    });
            });
    }
    
    private Drive buildDriveProto(String name, Map<String, String> meta) {
        Drive.Builder builder = Drive.newBuilder()
            .setName(name);
        
        if (meta.containsKey("description")) {
            builder.setDescription(meta.get("description"));
        }
        
        if (meta.containsKey("created_at")) {
            builder.setCreatedAt(Timestamp.newBuilder()
                .setSeconds(Instant.parse(meta.get("created_at")).getEpochSecond())
                .build());
        }
        
        if (meta.containsKey("last_accessed")) {
            builder.setLastAccessed(Timestamp.newBuilder()
                .setSeconds(Instant.parse(meta.get("last_accessed")).getEpochSecond())
                .build());
        }
        
        if (meta.containsKey("total_size")) {
            builder.setTotalSize(Long.parseLong(meta.get("total_size")));
        }
        
        if (meta.containsKey("node_count")) {
            builder.setNodeCount(Long.parseLong(meta.get("node_count")));
        }
        
        // Add custom metadata
        meta.entrySet().stream()
            .filter(e -> e.getKey().startsWith("meta:"))
            .forEach(e -> builder.putMetadata(
                e.getKey().substring(5), 
                e.getValue()));
        
        return builder.build();
    }
    
    @Override
    public Uni<Node> createNode(CreateNodeRequest request) {
        // Validate request
        if (request.getDrive() == null || request.getDrive().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive is required")
                )
            );
        }
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node name is required")
                )
            );
        }
        
        String drive = request.getDrive();
        String nodeId = UUID.randomUUID().toString();
        RedisFilesystemNode node = new RedisFilesystemNode();
        node.setId(nodeId);
        node.setName(request.getName());
        node.setType(request.getType().name());
        node.setCreatedAt(Instant.now());
        node.setUpdatedAt(Instant.now());
        node.setSize(0L); // Initialize size
        
        // Set parent and path
        Uni<Void> parentValidation = Uni.createFrom().voidItem();
        if (request.getParentId() != null && !request.getParentId().isEmpty()) {
            parentValidation = validateParentExists(drive, request.getParentId())
                .map(parentPath -> {
                    node.setParentId(request.getParentId());
                    node.setPath(parentPath + request.getParentId() + ",");
                    return null;
                });
        } else {
            node.setPath(",");
        }
        
        return parentValidation
            .flatMap(v -> {
                // Handle payload for files
                if (request.getType() == Node.NodeType.FILE && request.hasPayload()) {
                    Any payload = request.getPayload();
                    
                    // Store payload using generic repository
                    Map<String, String> payloadMeta = new HashMap<>();
                    payloadMeta.put("nodeId", nodeId);
                    payloadMeta.put("filename", request.getName());
                    payloadMeta.put("drive", drive);
                    
                    return payloadRepository.storeAny(payload, payloadMeta)
                        .map(payloadId -> {
                            node.setPayloadRef(payloadId);
                            node.setPayloadTypeUrl(payload.getTypeUrl());
                            node.setSize((long) payload.getSerializedSize());
                            LOG.debugf("Stored payload for node %s: type=%s, size=%d", 
                                nodeId, payload.getTypeUrl(), payload.getSerializedSize());
                            return null;
                        });
                }
                return Uni.createFrom().voidItem();
            })
            .flatMap(v -> {
                // Copy metadata and validate SVG if present
                if (request.getMetadataMap() != null) {
                    Map<String, String> metadata = new HashMap<>(request.getMetadataMap());
                    
                    // Check for icon SVG in metadata
                    if (metadata.containsKey("iconSvg")) {
                        try {
                            String validatedSvg = svgValidator.validateAndSanitize(metadata.get("iconSvg"));
                            node.setIconSvg(validatedSvg);
                            metadata.remove("iconSvg");
                        } catch (IllegalArgumentException e) {
                            LOG.warn("Invalid SVG provided for node " + nodeId + ": " + e.getMessage());
                            node.setIconSvg(svgValidator.getDefaultIcon());
                        }
                    }
                    
                    node.setMetadata(metadata);
                }
                
                // Store node in Redis
                return storeNode(drive, node);
            })
            .flatMap(v -> {
                // Update parent's children set
                if (node.getParentId() != null) {
                    return set().sadd(childrenKey(drive, node.getParentId()), nodeId)
                        .flatMap(x -> updateDriveStats(drive, 1, 0L))
                        .map(x -> toProto(node));
                } else {
                    // Add to root nodes
                    return set().sadd(rootNodesKey(drive), nodeId)
                        .flatMap(x -> updateDriveStats(drive, 1, 0L))
                        .map(x -> toProto(node));
                }
            });
    }
    
    // Other methods would follow the same pattern...
    // For brevity, I'll add stubs for the remaining required methods
    
    @Override
    public Uni<Node> getNode(GetNodeRequest request) {
        if (request.getDrive() == null || request.getDrive().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive is required")
                )
            );
        }
        
        if (request.getId() == null || request.getId().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        return loadNode(request.getDrive(), request.getId())
            .flatMap(node -> {
                if (node == null) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("Node not found: " + request.getId())
                        )
                    );
                }
                return toProtoWithPayload(node);
            });
    }
    
    @Override
    public Uni<GetChildrenResponse> getChildren(GetChildrenRequest request) {
        if (request.getDrive() == null || request.getDrive().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive is required")
                )
            );
        }
        
        String drive = request.getDrive();
        String parentId = request.getParentId();
        
        // Get children from the appropriate set
        Uni<Set<String>> childIdsUni;
        if (parentId == null || parentId.isEmpty()) {
            // Get root nodes
            childIdsUni = set().smembers(rootNodesKey(drive));
        } else {
            // Get children of specific parent
            childIdsUni = set().smembers(childrenKey(drive, parentId));
        }
        
        return childIdsUni.flatMap(childIds -> {
            if (childIds.isEmpty()) {
                return Uni.createFrom().item(GetChildrenResponse.newBuilder()
                    .setTotalCount(0)
                    .build());
            }
            
            // Load all child nodes
            List<Uni<RedisFilesystemNode>> nodeLoads = childIds.stream()
                .map(id -> loadNode(drive, id))
                .collect(Collectors.toList());
            
            return Uni.combine().all().unis(nodeLoads)
                .with(nodes -> {
                    List<Node> nodeList = nodes.stream()
                        .map(obj -> (RedisFilesystemNode) obj)
                        .filter(Objects::nonNull)
                        .map(this::toProto)
                        .sorted((a, b) -> a.getName().compareTo(b.getName()))
                        .collect(Collectors.toList());
                    
                    return GetChildrenResponse.newBuilder()
                        .addAllNodes(nodeList)
                        .setTotalCount(nodeList.size())
                        .build();
                });
        });
    }
    
    @Override
    public Uni<Node> updateNode(UpdateNodeRequest request) {
        if (request.getDrive() == null || request.getDrive().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive is required")
                )
            );
        }
        
        if (request.getId() == null || request.getId().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        String drive = request.getDrive();
        String nodeId = request.getId();
        
        return loadNode(drive, nodeId)
            .flatMap(node -> {
                if (node == null) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("Node not found: " + nodeId)
                        )
                    );
                }
                
                // Update fields if provided
                if (request.getName() != null && !request.getName().isEmpty()) {
                    node.setName(request.getName());
                }
                
                // Update metadata if provided
                if (request.getMetadataMap() != null && !request.getMetadataMap().isEmpty()) {
                    Map<String, String> metadata = node.getMetadata();
                    if (metadata == null) {
                        metadata = new HashMap<>();
                    }
                    metadata.putAll(request.getMetadataMap());
                    node.setMetadata(metadata);
                }
                
                // Update icon SVG if provided in metadata
                if (request.getMetadataMap().containsKey("iconSvg")) {
                    try {
                        String validatedSvg = svgValidator.validateAndSanitize(request.getMetadataMap().get("iconSvg"));
                        node.setIconSvg(validatedSvg);
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Invalid SVG provided for node " + nodeId + ": " + e.getMessage());
                        // Keep existing icon
                    }
                }
                
                // Update timestamp
                node.setUpdatedAt(Instant.now());
                
                // Store updated node
                return storeNode(drive, node)
                    .map(v -> toProto(node));
            });
    }
    
    @Override
    public Uni<DeleteNodeResponse> deleteNode(DeleteNodeRequest request) {
        if (request.getDrive() == null || request.getDrive().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive is required")
                )
            );
        }
        
        if (request.getId() == null || request.getId().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        String drive = request.getDrive();
        String nodeId = request.getId();
        boolean recursive = request.getRecursive();
        
        return loadNode(drive, nodeId)
            .flatMap(node -> {
                if (node == null) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("Node not found: " + nodeId)
                        )
                    );
                }
                
                // If it's a folder and not recursive, check if it has children
                if (Node.NodeType.FOLDER.name().equals(node.getType()) && !recursive) {
                    return set().scard(childrenKey(drive, nodeId))
                        .flatMap(childCount -> {
                            if (childCount > 0) {
                                return Uni.createFrom().failure(
                                    new StatusRuntimeException(
                                        Status.FAILED_PRECONDITION
                                            .withDescription("Folder has children. Use recursive=true to delete")
                                    )
                                );
                            }
                            return deleteNodeAndUpdateParent(drive, node, 1);
                        });
                } else if (Node.NodeType.FOLDER.name().equals(node.getType()) && recursive) {
                    // Recursive delete
                    return deleteNodeRecursive(drive, node);
                } else {
                    // Simple file delete
                    return deleteNodeAndUpdateParent(drive, node, 1);
                }
            });
    }
    
    private Uni<DeleteNodeResponse> deleteNodeAndUpdateParent(String drive, RedisFilesystemNode node, int count) {
        String nodeId = node.getId();
        
        // Delete the node
        return keys().del(nodeKey(drive, nodeId))
            .flatMap(v -> {
                // Remove from parent's children or root nodes
                if (node.getParentId() != null) {
                    return set().srem(childrenKey(drive, node.getParentId()), nodeId);
                } else {
                    return set().srem(rootNodesKey(drive), nodeId);
                }
            })
            .flatMap(v -> {
                // If it's a folder, delete its children set
                if (Node.NodeType.FOLDER.name().equals(node.getType())) {
                    return keys().del(childrenKey(drive, nodeId));
                }
                return Uni.createFrom().item(0L);
            })
            .flatMap(v -> updateDriveStats(drive, -count, 0L))
            .map(v -> DeleteNodeResponse.newBuilder()
                .setSuccess(true)
                .setDeletedCount(count)
                .build());
    }
    
    private Uni<DeleteNodeResponse> deleteNodeRecursive(String drive, RedisFilesystemNode node) {
        String nodeId = node.getId();
        
        // Get all children
        return set().smembers(childrenKey(drive, nodeId))
            .flatMap(childIds -> {
                if (childIds.isEmpty()) {
                    // No children, just delete this node
                    return deleteNodeAndUpdateParent(drive, node, 1);
                }
                
                // Load and delete all children
                List<Uni<Integer>> deleteOps = childIds.stream()
                    .map(childId -> loadNode(drive, childId)
                        .flatMap(child -> {
                            if (child == null) {
                                return Uni.createFrom().item(0);
                            }
                            if (Node.NodeType.FOLDER.name().equals(child.getType())) {
                                // Recursive delete for child folders
                                return deleteNodeRecursive(drive, child)
                                    .map(resp -> resp.getDeletedCount());
                            } else {
                                // Simple delete for files
                                return deleteNodeAndUpdateParent(drive, child, 1)
                                    .map(resp -> resp.getDeletedCount());
                            }
                        }))
                    .collect(Collectors.toList());
                
                return Uni.combine().all().unis(deleteOps)
                    .with(counts -> {
                        int totalDeleted = counts.stream()
                            .mapToInt(c -> (Integer) c)
                            .sum();
                        // Now delete the parent folder
                        return deleteNodeAndUpdateParent(drive, node, totalDeleted + 1);
                    })
                    .flatMap(resp -> resp);
            });
    }
    
    @Override
    public Uni<Node> moveNode(MoveNodeRequest request) {
        if (request.getDrive() == null || request.getDrive().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive is required")
                )
            );
        }
        
        String drive = request.getDrive();
        String nodeId = request.getNodeId();
        String newParentId = request.getNewParentId();
        
        String newName = request.getNewName();
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        return loadNode(drive, nodeId)
            .flatMap(node -> {
                if (node == null) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("Node not found: " + nodeId)
                        )
                    );
                }
                
                // Validate we're not moving to itself
                if (nodeId.equals(newParentId)) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.INVALID_ARGUMENT.withDescription("Cannot move node to itself")
                        )
                    );
                }
                
                // Validate new parent exists (if not root)
                if (newParentId != null && !newParentId.trim().isEmpty()) {
                    return loadNode(drive, newParentId)
                        .flatMap(newParent -> {
                            if (newParent == null) {
                                return Uni.createFrom().failure(
                                    new StatusRuntimeException(
                                        Status.NOT_FOUND.withDescription("New parent not found: " + newParentId)
                                    )
                                );
                            }
                            if (!Node.NodeType.FOLDER.name().equals(newParent.getType())) {
                                return Uni.createFrom().failure(
                                    new StatusRuntimeException(
                                        Status.INVALID_ARGUMENT.withDescription("New parent must be a folder")
                                    )
                                );
                            }
                            
                            // Check if new parent is a descendant of the node being moved
                            return isDescendantOf(drive, newParentId, nodeId)
                                .flatMap(isDescendant -> {
                                    if (isDescendant) {
                                        return Uni.createFrom().failure(
                                            new StatusRuntimeException(
                                                Status.INVALID_ARGUMENT.withDescription("Cannot move node to its own descendant")
                                            )
                                        );
                                    }
                                    return moveNodeInternal(drive, node, newParentId, request.getNewName());
                                });
                        });
                } else {
                    // Moving to root
                    return moveNodeInternal(drive, node, null, request.getNewName());
                }
            });
    }
    
    private Uni<Node> moveNodeInternal(String drive, RedisFilesystemNode node, String newParentId, String newName) {
        String nodeId = node.getId();
        String oldParentId = node.getParentId();
        
        // Remove from old parent/root
        Uni<Long> removeFromOld;
        if (oldParentId != null) {
            removeFromOld = set().srem(childrenKey(drive, oldParentId), nodeId)
                .map(Long::valueOf);
        } else {
            removeFromOld = set().srem(rootNodesKey(drive), nodeId)
                .map(Long::valueOf);
        }
        
        return removeFromOld
            .flatMap(v -> {
                // Add to new parent/root
                if (newParentId != null) {
                    return set().sadd(childrenKey(drive, newParentId), nodeId);
                } else {
                    return set().sadd(rootNodesKey(drive), nodeId);
                }
            })
            .flatMap(v -> {
                // Update node's parent reference and name if provided
                node.setParentId(newParentId);
                if (newName != null && !newName.trim().isEmpty()) {
                    node.setName(newName);
                }
                node.setUpdatedAt(Instant.now());
                return storeNode(drive, node);
            })
            .map(v -> toProto(node));
    }
    
    @Override
    public Uni<Node> copyNode(CopyNodeRequest request) {
        if (request.getDrive() == null || request.getDrive().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive is required")
                )
            );
        }
        
        String drive = request.getDrive();
        String nodeId = request.getNodeId();
        String newParentId = request.getTargetParentId();
        String newName = request.getNewName();
        
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        return loadNode(drive, nodeId)
            .flatMap(node -> {
                if (node == null) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("Node not found: " + nodeId)
                        )
                    );
                }
                
                // For folder copies, validate we're not copying into itself or its descendants
                if (Node.NodeType.FOLDER.name().equals(node.getType()) && request.getDeep()) {
                    if (nodeId.equals(newParentId)) {
                        return Uni.createFrom().failure(
                            new StatusRuntimeException(
                                Status.INVALID_ARGUMENT.withDescription("Cannot copy folder into itself")
                            )
                        );
                    }
                    
                    // Check if target parent is a descendant of the source folder
                    if (newParentId != null && !newParentId.trim().isEmpty()) {
                        return isDescendantOf(drive, newParentId, nodeId)
                            .flatMap(isDescendant -> {
                                if (isDescendant) {
                                    return Uni.createFrom().failure(
                                        new StatusRuntimeException(
                                            Status.INVALID_ARGUMENT.withDescription("Cannot copy folder into its own descendant")
                                        )
                                    );
                                }
                                // Continue with parent validation
                                return validateParentAndCopy(drive, node, newParentId, newName);
                            });
                    } else {
                        // Copying to root
                        return copyNodeRecursive(drive, node, null, newName);
                    }
                }
                
                // For non-folder copies or non-deep copies, just validate parent
                return validateParentAndCopy(drive, node, newParentId, newName);
            });
    }
    
    private Uni<Node> validateParentAndCopy(String drive, RedisFilesystemNode node, String newParentId, String newName) {
        if (newParentId != null && !newParentId.trim().isEmpty()) {
            return loadNode(drive, newParentId)
                .flatMap(newParent -> {
                    if (newParent == null) {
                        return Uni.createFrom().failure(
                            new StatusRuntimeException(
                                Status.NOT_FOUND.withDescription("New parent not found: " + newParentId)
                            )
                        );
                    }
                    if (!Node.NodeType.FOLDER.name().equals(newParent.getType())) {
                        return Uni.createFrom().failure(
                            new StatusRuntimeException(
                                Status.INVALID_ARGUMENT.withDescription("New parent must be a folder")
                            )
                        );
                    }
                    return copyNodeRecursive(drive, node, newParentId, newName);
                });
        } else {
            // Copying to root
            return copyNodeRecursive(drive, node, null, newName);
        }
    }
    
    private Uni<Node> copyNodeRecursive(String drive, RedisFilesystemNode sourceNode, String newParentId, String newName) {
        // Create a copy of the node
        RedisFilesystemNode copyNode = new RedisFilesystemNode();
        copyNode.setId(UUID.randomUUID().toString());
        copyNode.setParentId(newParentId);
        copyNode.setName(newName != null && !newName.trim().isEmpty() ? newName : sourceNode.getName());
        copyNode.setType(sourceNode.getType());
        copyNode.setMimeType(sourceNode.getMimeType());
        copyNode.setSize(sourceNode.getSize());
        copyNode.setIconSvg(sourceNode.getIconSvg());
        copyNode.setServiceType(sourceNode.getServiceType());
        copyNode.setPayloadTypeUrl(sourceNode.getPayloadTypeUrl());
        copyNode.setPayloadRef(sourceNode.getPayloadRef()); // Share the same payload
        
        // Set path
        if (newParentId != null) {
            copyNode.setPath("," + newParentId + ",");
        } else {
            copyNode.setPath(",");
        }
        
        // Set timestamps
        Instant now = Instant.now();
        copyNode.setCreatedAt(now);
        copyNode.setUpdatedAt(now);
        
        // Store the copy
        return storeNode(drive, copyNode)
            .flatMap(v -> {
                // Add to parent or root
                if (newParentId != null) {
                    return set().sadd(childrenKey(drive, newParentId), copyNode.getId());
                } else {
                    return set().sadd(rootNodesKey(drive), copyNode.getId());
                }
            })
            .flatMap(v -> {
                // If it's a folder, recursively copy children
                if (Node.NodeType.FOLDER.name().equals(sourceNode.getType())) {
                    return copyChildrenRecursive(drive, sourceNode.getId(), copyNode.getId())
                        .map(x -> toProto(copyNode));
                } else {
                    return Uni.createFrom().item(toProto(copyNode));
                }
            });
    }
    
    private Uni<Void> copyChildrenRecursive(String drive, String sourceParentId, String targetParentId) {
        return set().smembers(childrenKey(drive, sourceParentId))
            .flatMap(childIds -> {
                if (childIds.isEmpty()) {
                    return Uni.createFrom().voidItem();
                }
                
                List<Uni<Node>> copyOps = childIds.stream()
                    .map(childId -> loadNode(drive, childId)
                        .flatMap(child -> {
                            if (child == null) {
                                return Uni.createFrom().nullItem();
                            }
                            return copyNodeRecursive(drive, child, targetParentId, null);
                        }))
                    .collect(Collectors.toList());
                
                return Uni.combine().all().unis(copyOps)
                    .discardItems();
            });
    }
    
    @Override
    public Uni<GetPathResponse> getPath(GetPathRequest request) {
        if (request.getDrive() == null || request.getDrive().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive is required")
                )
            );
        }
        
        String drive = request.getDrive();
        String nodeId = request.getId();
        
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        return buildPath(drive, nodeId, new ArrayList<>())
            .map(path -> {
                Collections.reverse(path); // Path was built from child to parent
                return GetPathResponse.newBuilder()
                    .addAllAncestors(path)
                    .build();
            });
    }
    
    private Uni<List<Node>> buildPath(String drive, String nodeId, List<Node> path) {
        if (nodeId == null) {
            return Uni.createFrom().item(path);
        }
        
        return loadNode(drive, nodeId)
            .flatMap(node -> {
                if (node == null) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("Node not found in path: " + nodeId)
                        )
                    );
                }
                
                path.add(toProto(node));
                
                // Continue building path up to root
                if (node.getParentId() != null) {
                    return buildPath(drive, node.getParentId(), path);
                } else {
                    return Uni.createFrom().item(path);
                }
            });
    }
    
    @Override
    public Uni<GetTreeResponse> getTree(GetTreeRequest request) {
        if (request.getDrive() == null || request.getDrive().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive is required")
                )
            );
        }
        
        String drive = request.getDrive();
        String rootId = request.getRootId();
        int maxDepth = request.getMaxDepth() > 0 ? request.getMaxDepth() : Integer.MAX_VALUE;
        // GetTreeRequest doesn't have includeFiles, assume we include all types
        boolean includeFiles = true;
        
        // Build tree from the specified root or all root nodes
        if (rootId != null && !rootId.trim().isEmpty()) {
            return loadNode(drive, rootId)
                .flatMap(rootNode -> {
                    if (rootNode == null) {
                        return Uni.createFrom().failure(
                            new StatusRuntimeException(
                                Status.NOT_FOUND.withDescription("Root node not found: " + rootId)
                            )
                        );
                    }
                    // Get children of the root node
                    return set().smembers(childrenKey(drive, rootId))
                        .flatMap(childIds -> {
                            if (childIds.isEmpty()) {
                                return Uni.createFrom().item(GetTreeResponse.newBuilder()
                                    .setRoot(toProto(rootNode))
                                    .build());
                            }
                            
                            List<Uni<TreeNode>> childTreeOps = childIds.stream()
                                .map(childId -> loadNode(drive, childId)
                                    .flatMap(childNode -> {
                                        if (childNode == null || 
                                            (!includeFiles && Node.NodeType.FILE.name().equals(childNode.getType()))) {
                                            return Uni.createFrom().nullItem();
                                        }
                                        return buildTreeNode(drive, childNode, 1, maxDepth, includeFiles);
                                    }))
                                .collect(Collectors.toList());
                            
                            return Uni.combine().all().unis(childTreeOps)
                                .with(childTrees -> {
                                    GetTreeResponse.Builder builder = GetTreeResponse.newBuilder()
                                        .setRoot(toProto(rootNode));
                                    
                                    childTrees.stream()
                                        .filter(Objects::nonNull)
                                        .forEach(tn -> builder.addChildren((TreeNode) tn));
                                    
                                    return builder.build();
                                });
                        });
                });
        } else {
            // Get all root nodes
            return set().smembers(rootNodesKey(drive))
                .flatMap(rootNodeIds -> {
                    if (rootNodeIds.isEmpty()) {
                        return Uni.createFrom().item(GetTreeResponse.newBuilder().build());
                    }
                    
                    List<Uni<TreeNode>> treeOps = rootNodeIds.stream()
                        .map(nodeId -> loadNode(drive, nodeId)
                            .flatMap(node -> {
                                if (node == null) {
                                    return Uni.createFrom().nullItem();
                                }
                                return buildTreeNode(drive, node, 0, maxDepth, includeFiles);
                            }))
                        .collect(Collectors.toList());
                    
                    return Uni.combine().all().unis(treeOps)
                        .with(treeNodes -> {
                            GetTreeResponse.Builder builder = GetTreeResponse.newBuilder();
                            treeNodes.stream()
                                .filter(Objects::nonNull)
                                .forEach(tn -> builder.addChildren((TreeNode) tn));
                            return builder.build();
                        });
                });
        }
    }
    
    private Uni<TreeNode> buildTreeNode(String drive, RedisFilesystemNode node, int currentDepth, int maxDepth, boolean includeFiles) {
        TreeNode.Builder builder = TreeNode.newBuilder()
            .setNode(toProto(node));
        
        // Don't fetch children if we've reached max depth or if it's a file
        if (currentDepth >= maxDepth || !Node.NodeType.FOLDER.name().equals(node.getType())) {
            return Uni.createFrom().item(builder.build());
        }
        
        return set().smembers(childrenKey(drive, node.getId()))
            .flatMap(childIds -> {
                if (childIds.isEmpty()) {
                    return Uni.createFrom().item(builder.build());
                }
                
                List<Uni<TreeNode>> childOps = childIds.stream()
                    .map(childId -> loadNode(drive, childId)
                        .flatMap(child -> {
                            if (child == null) {
                                return Uni.createFrom().nullItem();
                            }
                            // Skip files if not requested
                            if (!includeFiles && !Node.NodeType.FOLDER.name().equals(child.getType())) {
                                return Uni.createFrom().nullItem();
                            }
                            return buildTreeNode(drive, child, currentDepth + 1, maxDepth, includeFiles);
                        }))
                    .collect(Collectors.toList());
                
                return Uni.combine().all().unis(childOps)
                    .with(children -> {
                        children.stream()
                            .filter(Objects::nonNull)
                            .forEach(child -> builder.addChildren((TreeNode) child));
                        return builder.build();
                    });
            });
    }
    
    @Override
    public Uni<SearchNodesResponse> searchNodes(SearchNodesRequest request) {
        if (request.getDrive() == null || request.getDrive().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive is required")
                )
            );
        }
        
        // Allow empty query to get all nodes
        String query = request.getQuery() != null ? request.getQuery().toLowerCase() : "";
        boolean hasQuery = !query.trim().isEmpty();
        
        List<Node.NodeType> typeFilter = request.getTypesList();
        Map<String, String> metadataFilters = request.getMetadataFiltersMap();
        
        String pattern = keyService.filesystemKey(request.getDrive(), "node:*");
        
        return keys().keys(pattern)
            .onItem().transformToMulti(keyList -> Multi.createFrom().iterable(keyList))
            .onItem().transformToUniAndConcatenate(key -> {
                String nodeId = extractNodeId(key);
                if (nodeId == null) return Uni.createFrom().nullItem();
                
                return loadNode(request.getDrive(), nodeId);
            })
            .filter(Objects::nonNull)
            .filter(node -> {
                // If no query, include all nodes
                if (!hasQuery) {
                    return true;
                }
                
                // Simple name matching
                if (node.getName() != null && 
                    node.getName().toLowerCase().contains(query)) {
                    return true;
                }
                
                // Simple metadata value matching
                if (node.getMetadata() != null) {
                    for (String value : node.getMetadata().values()) {
                        if (value != null && value.toLowerCase().contains(query)) {
                            return true;
                        }
                    }
                }
                
                return false;
            })
            .filter(node -> {
                // Filter by type if specified
                if (!typeFilter.isEmpty()) {
                    return typeFilter.contains(Node.NodeType.valueOf(node.getType()));
                }
                return true;
            })
            .filter(node -> {
                // Filter by metadata if specified
                if (!metadataFilters.isEmpty()) {
                    if (node.getMetadata() == null) {
                        return false;
                    }
                    for (Map.Entry<String, String> filter : metadataFilters.entrySet()) {
                        String value = node.getMetadata().get(filter.getKey());
                        if (value == null || !value.equals(filter.getValue())) {
                            return false;
                        }
                    }
                }
                return true;
            })
            .collect().asList()
            .flatMap(nodes -> {
                // Convert nodes to proto with payloads asynchronously
                List<Uni<Node>> nodeUnis = nodes.stream()
                    .map(this::toProtoWithPayload)
                    .collect(Collectors.toList());
                
                // Handle empty list case
                if (nodeUnis.isEmpty()) {
                    return Uni.createFrom().item(
                        SearchNodesResponse.newBuilder()
                            .setTotalCount(0)
                            .build()
                    );
                }
                
                return Uni.combine().all().unis(nodeUnis)
                    .with(protoNodes -> {
                        List<Node> nodeList = protoNodes.stream()
                            .map(o -> (Node) o)
                            .collect(Collectors.toList());
                        
                        return SearchNodesResponse.newBuilder()
                            .addAllNodes(nodeList)
                            .setTotalCount(nodeList.size())
                            .build();
                    });
            });
    }
    
    @Override
    public Uni<FormatFilesystemResponse> formatFilesystem(FormatFilesystemRequest request) {
        if (request.getDrive() == null || request.getDrive().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Drive is required")
                )
            );
        }
        
        if (!"DELETE_FILESYSTEM_DATA".equals(request.getConfirmation())) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Invalid confirmation. Must be 'DELETE_FILESYSTEM_DATA'")
                )
            );
        }
        
        String drive = request.getDrive();
        boolean dryRun = request.getDryRun();
        
        // Get all filesystem keys for this drive
        String filesystemPrefix = keyService.filesystemKey(drive, "");
        String pattern = filesystemPrefix + "*";
        
        return keys().keys(pattern)
            .flatMap(allKeys -> {
                // Collect all node keys
                List<String> nodeKeys = allKeys.stream()
                    .filter(key -> key.startsWith(filesystemPrefix + NODE_PREFIX))
                    .collect(Collectors.toList());
                
                if (nodeKeys.isEmpty()) {
                    String message = "No nodes to delete";
                    return Uni.createFrom().item(FormatFilesystemResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage(message)
                        .setNodesDeleted(0)
                        .setFoldersDeleted(0)
                        .build());
                }
                
                // Load all nodes to count by type
                List<Uni<RedisFilesystemNode>> nodeLoads = nodeKeys.stream()
                    .map(key -> {
                        String nodeId = key.substring((filesystemPrefix + NODE_PREFIX).length());
                        return loadNode(drive, nodeId);
                    })
                    .collect(Collectors.toList());
                
                return Uni.combine().all().unis(nodeLoads)
                    .with(nodes -> {
                        int fileCount = 0;
                        int folderCount = 0;
                        Map<String, Integer> typeCountMap = new HashMap<>();
                        List<String> keysToDelete = new ArrayList<>();
                        
                        // Process nodes and apply type filter if specified
                        List<String> typeUrls = request.getTypeUrlsList();
                        boolean hasTypeFilter = typeUrls != null && !typeUrls.isEmpty();
                        
                        for (int i = 0; i < nodes.size(); i++) {
                            RedisFilesystemNode node = (RedisFilesystemNode) nodes.get(i);
                            if (node != null) {
                                boolean shouldDelete = true;
                                
                                LOG.debugf("Processing node %s: type=%s, payloadType=%s", 
                                    node.getId(), node.getType(), node.getPayloadTypeUrl());
                                
                                if (Node.NodeType.FOLDER.name().equals(node.getType())) {
                                    // For now, only delete folders if no type filter is specified
                                    if (hasTypeFilter) {
                                        shouldDelete = false;
                                        LOG.debugf("Skipping folder %s due to type filter", node.getId());
                                    } else {
                                        folderCount++;
                                    }
                                } else {
                                    // File node
                                    if (hasTypeFilter && !typeUrls.contains(node.getPayloadTypeUrl())) {
                                        shouldDelete = false;
                                        LOG.debugf("Skipping file %s: payloadType %s not in filter %s", 
                                            node.getId(), node.getPayloadTypeUrl(), typeUrls);
                                    } else {
                                        fileCount++;
                                        // Count by payload type
                                        if (node.getPayloadTypeUrl() != null) {
                                            typeCountMap.merge(node.getPayloadTypeUrl(), 1, Integer::sum);
                                        }
                                    }
                                }
                                
                                if (shouldDelete) {
                                    LOG.debugf("Will delete node %s", node.getId());
                                    keysToDelete.add(nodeKeys.get(i));
                                }
                            }
                        }
                        
                        // Delete selected keys if not dry run
                        if (!dryRun) {
                            // Delete node keys
                            for (String key : keysToDelete) {
                                keys().del(key).subscribe().with(v -> {});
                            }
                            
                            // If no type filter, also delete children sets and root nodes set
                            if (!hasTypeFilter) {
                                List<String> otherKeys = allKeys.stream()
                                    .filter(key -> key.startsWith(filesystemPrefix + CHILDREN_PREFIX) ||
                                                 key.equals(filesystemPrefix + ROOT_NODES))
                                    .collect(Collectors.toList());
                                
                                for (String key : otherKeys) {
                                    keys().del(key).subscribe().with(v -> {});
                                }
                            }
                        }
                        
                        LOG.infof("Format operation: dryRun=%s, fileCount=%d, folderCount=%d, typeFilter=%s, typeCounts=%s",
                            dryRun, fileCount, folderCount, typeUrls, typeCountMap);
                        
                        String message = dryRun ? 
                            String.format("Would delete %d files and %d folders", fileCount, folderCount) :
                            String.format("Formatted filesystem. Deleted %d files and %d folders", fileCount, folderCount);
                        
                        FormatFilesystemResponse.Builder builder = FormatFilesystemResponse.newBuilder()
                            .setSuccess(true)
                            .setMessage(message)
                            .setNodesDeleted(fileCount)
                            .setFoldersDeleted(folderCount);
                        
                        // Add type counts
                        builder.putAllDeletedByType(typeCountMap);
                        
                        return builder.build();
                    });
            });
    }
    
    // Helper methods
    private Uni<String> validateParentExists(String drive, String parentId) {
        return loadNode(drive, parentId)
            .map(parent -> {
                if (parent == null) {
                    throw new StatusRuntimeException(
                        Status.INVALID_ARGUMENT.withDescription("Parent node not found: " + parentId)
                    );
                }
                if (!parent.getType().equals(Node.NodeType.FOLDER.name())) {
                    throw new StatusRuntimeException(
                        Status.INVALID_ARGUMENT.withDescription("Parent must be a folder")
                    );
                }
                return parent.getPath();
            });
    }
    
    private Uni<Void> storeNode(String drive, RedisFilesystemNode node) {
        Map<String, String> nodeData = new HashMap<>();
        nodeData.put("id", node.getId());
        nodeData.put("name", node.getName());
        nodeData.put("type", node.getType());
        nodeData.put("parentId", node.getParentId() != null ? node.getParentId() : "");
        nodeData.put("path", node.getPath() != null ? node.getPath() : "");
        nodeData.put("createdAt", node.getCreatedAt() != null ? node.getCreatedAt().toString() : "");
        nodeData.put("updatedAt", node.getUpdatedAt() != null ? node.getUpdatedAt().toString() : "");
        nodeData.put("size", node.getSize() != null ? String.valueOf(node.getSize()) : "0");
        nodeData.put("payloadRef", node.getPayloadRef() != null ? node.getPayloadRef() : "");
        nodeData.put("payloadTypeUrl", node.getPayloadTypeUrl() != null ? node.getPayloadTypeUrl() : "");
        
        if (node.getIconSvg() != null) {
            nodeData.put("iconSvg", node.getIconSvg());
        }
        
        if (node.getMetadata() != null) {
            node.getMetadata().forEach((k, v) -> nodeData.put("meta:" + k, v));
        }
        
        String nodeKey = nodeKey(drive, node.getId());
        return hash().hset(nodeKey, nodeData).replaceWithVoid();
    }
    
    private Uni<RedisFilesystemNode> loadNode(String drive, String nodeId) {
        String nodeKey = nodeKey(drive, nodeId);
        return hash().hgetall(nodeKey)
            .map(data -> {
                if (data.isEmpty()) {
                    return null;
                }
                
                RedisFilesystemNode node = new RedisFilesystemNode();
                node.setId(data.get("id"));
                node.setName(data.get("name"));
                node.setType(data.get("type"));
                node.setParentId(data.get("parentId").isEmpty() ? null : data.get("parentId"));
                node.setPath(data.get("path"));
                String createdAtStr = data.get("createdAt");
                if (createdAtStr != null && !createdAtStr.isEmpty()) {
                    node.setCreatedAt(Instant.parse(createdAtStr));
                } else {
                    node.setCreatedAt(Instant.now()); // Fallback for nodes without timestamps
                }
                
                String updatedAtStr = data.get("updatedAt");
                if (updatedAtStr != null && !updatedAtStr.isEmpty()) {
                    node.setUpdatedAt(Instant.parse(updatedAtStr));
                } else {
                    node.setUpdatedAt(node.getCreatedAt()); // Use createdAt as fallback
                }
                String sizeStr = data.get("size");
                if (sizeStr != null && !sizeStr.isEmpty()) {
                    node.setSize(Long.parseLong(sizeStr));
                } else {
                    node.setSize(0L); // Default size
                }
                node.setPayloadRef(data.get("payloadRef").isEmpty() ? null : data.get("payloadRef"));
                node.setPayloadTypeUrl(data.get("payloadTypeUrl").isEmpty() ? null : data.get("payloadTypeUrl"));
                
                if (data.containsKey("iconSvg")) {
                    node.setIconSvg(data.get("iconSvg"));
                }
                
                Map<String, String> metadata = new HashMap<>();
                data.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("meta:"))
                    .forEach(e -> metadata.put(e.getKey().substring(5), e.getValue()));
                
                if (!metadata.isEmpty()) {
                    node.setMetadata(metadata);
                }
                
                return node;
            });
    }
    
    private Node toProto(RedisFilesystemNode redisNode) {
        Node.Builder builder = Node.newBuilder()
            .setId(redisNode.getId())
            .setName(redisNode.getName())
            .setType(Node.NodeType.valueOf(redisNode.getType()))
            .setPath(redisNode.getPath())
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(redisNode.getCreatedAt().getEpochSecond())
                .setNanos(redisNode.getCreatedAt().getNano())
                .build())
            .setUpdatedAt(Timestamp.newBuilder()
                .setSeconds(redisNode.getUpdatedAt().getEpochSecond())
                .setNanos(redisNode.getUpdatedAt().getNano())
                .build())
            .setSize(redisNode.getSize() != null ? redisNode.getSize() : 0L);
        
        if (redisNode.getParentId() != null) {
            builder.setParentId(redisNode.getParentId());
        }
        
        if (redisNode.getPayloadTypeUrl() != null) {
            builder.setPayloadType(redisNode.getPayloadTypeUrl());
        }
        
        
        if (redisNode.getIconSvg() != null) {
            builder.setIconSvg(redisNode.getIconSvg());
        }
        
        if (redisNode.getMetadata() != null) {
            builder.putAllMetadata(redisNode.getMetadata());
        }
        
        return builder.build();
    }
    
    private Uni<Node> toProtoWithPayload(RedisFilesystemNode redisNode) {
        Node.Builder builder = Node.newBuilder()
            .setId(redisNode.getId())
            .setName(redisNode.getName())
            .setType(Node.NodeType.valueOf(redisNode.getType()))
            .setPath(redisNode.getPath())
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(redisNode.getCreatedAt().getEpochSecond())
                .setNanos(redisNode.getCreatedAt().getNano())
                .build())
            .setUpdatedAt(Timestamp.newBuilder()
                .setSeconds(redisNode.getUpdatedAt().getEpochSecond())
                .setNanos(redisNode.getUpdatedAt().getNano())
                .build())
            .setSize(redisNode.getSize() != null ? redisNode.getSize() : 0L);
        
        if (redisNode.getParentId() != null) {
            builder.setParentId(redisNode.getParentId());
        }
        
        if (redisNode.getPayloadTypeUrl() != null) {
            builder.setPayloadType(redisNode.getPayloadTypeUrl());
        }
        
        if (redisNode.getIconSvg() != null) {
            builder.setIconSvg(redisNode.getIconSvg());
        }
        
        if (redisNode.getMetadata() != null) {
            builder.putAllMetadata(redisNode.getMetadata());
        }
        
        // Retrieve and set payload if it exists
        if (redisNode.getPayloadRef() != null) {
            return payloadRepository.getAny(redisNode.getPayloadRef())
                .map(payload -> {
                    if (payload != null) {
                        builder.setPayload(payload);
                    }
                    return builder.build();
                })
                .onFailure().recoverWithItem(throwable -> {
                    LOG.warn("Failed to retrieve payload for node " + redisNode.getId() + ": " + throwable.getMessage());
                    return builder.build();
                });
        } else {
            return Uni.createFrom().item(builder.build());
        }
    }
    
    private Uni<Void> updateDriveStats(String driveName, int nodeCountDelta, long sizeDelta) {
        String driveMetaKey = keyService.driveMetadataKey(driveName);
        
        return hash().hget(driveMetaKey, "node_count")
            .flatMap(currentCountStr -> {
                long currentCount = currentCountStr != null ? Long.parseLong(currentCountStr) : 0L;
                long newCount = Math.max(0, currentCount + nodeCountDelta);
                
                return hash().hset(driveMetaKey, "node_count", String.valueOf(newCount));
            })
            .flatMap(v -> {
                if (sizeDelta != 0) {
                    return hash().hget(driveMetaKey, "total_size")
                        .flatMap(currentSizeStr -> {
                            long currentSize = currentSizeStr != null ? Long.parseLong(currentSizeStr) : 0L;
                            long newSize = Math.max(0, currentSize + sizeDelta);
                            
                            return hash().hset(driveMetaKey, "total_size", String.valueOf(newSize));
                        });
                }
                return Uni.createFrom().voidItem();
            })
            .replaceWithVoid();
    }
    
    /**
     * Check if a node is a descendant of another node.
     * This is used to prevent circular references when moving nodes.
     */
    private Uni<Boolean> isDescendantOf(String drive, String nodeId, String ancestorId) {
        if (nodeId == null || ancestorId == null || nodeId.equals(ancestorId)) {
            return Uni.createFrom().item(false);
        }
        
        return loadNode(drive, nodeId)
            .flatMap(node -> {
                if (node == null) {
                    return Uni.createFrom().item(false);
                }
                
                // Check if the node's path contains the ancestor ID
                String path = node.getPath();
                if (path != null && path.contains("," + ancestorId + ",")) {
                    return Uni.createFrom().item(true);
                }
                
                // If no parent, it's not a descendant
                if (node.getParentId() == null || node.getParentId().isEmpty()) {
                    return Uni.createFrom().item(false);
                }
                
                // If parent is the ancestor, it's a descendant
                if (node.getParentId().equals(ancestorId)) {
                    return Uni.createFrom().item(true);
                }
                
                // Recursively check the parent
                return isDescendantOf(drive, node.getParentId(), ancestorId);
            });
    }
}
