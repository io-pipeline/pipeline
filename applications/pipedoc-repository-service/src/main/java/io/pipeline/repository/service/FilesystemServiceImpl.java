package io.pipeline.repository.service;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.pipeline.repository.filesystem.*;
import io.pipeline.repository.redis.RedisFilesystemNode;
import io.quarkus.grpc.GrpcService;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.hash.ReactiveHashCommands;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.set.ReactiveSetCommands;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-based implementation of the filesystem service.
 * Uses a combination of direct Redis operations for the filesystem structure
 * and the generic repository for payload storage.
 */
@GrpcService
public class FilesystemServiceImpl implements FilesystemService {
    
    private static final Logger LOG = Logger.getLogger(FilesystemServiceImpl.class);
    private static final String NODE_PREFIX = "fs:node:";
    private static final String CHILDREN_PREFIX = "fs:children:";
    private static final String ROOT_NODES = "fs:roots";
    
    @Inject
    ReactiveRedisDataSource redis;
    
    @Inject
    GenericRepositoryService payloadRepository;
    
    @Inject
    SvgValidator svgValidator;
    
    private ReactiveHashCommands<String, String, String> hash() {
        return redis.hash(String.class, String.class, String.class);
    }
    
    private ReactiveSetCommands<String, String> set() {
        return redis.set(String.class, String.class);
    }
    
    private ReactiveKeyCommands<String> keys() {
        return redis.key(String.class);
    }
    
    @Override
    public Uni<Node> createNode(CreateNodeRequest request) {
        // Validate request
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node name is required")
                )
            );
        }
        
        String nodeId = UUID.randomUUID().toString();
        RedisFilesystemNode node = new RedisFilesystemNode();
        node.setId(nodeId);
        node.setName(request.getName());
        node.setType(request.getType().name());
        node.setCreatedAt(Instant.now());
        node.setUpdatedAt(Instant.now());
        
        // Set parent and path
        Uni<Void> parentValidation = Uni.createFrom().voidItem();
        if (request.getParentId() != null && !request.getParentId().isEmpty()) {
            parentValidation = validateParentExists(request.getParentId())
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
                    
                    return payloadRepository.storeAny(payload, payloadMeta)
                        .map(payloadId -> {
                            node.setPayloadRef(payloadId);
                            node.setPayloadTypeUrl(payload.getTypeUrl());
                            node.setSize((long) payload.getSerializedSize());
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
                            metadata.remove("iconSvg"); // Don't store in metadata after moving to iconSvg field
                        } catch (IllegalArgumentException e) {
                            LOG.warn("Invalid SVG provided for node " + nodeId + ": " + e.getMessage());
                            // Use default icon on validation failure
                            node.setIconSvg(svgValidator.getDefaultIcon());
                        }
                    }
                    
                    node.setMetadata(metadata);
                }
                
                // Store node in Redis
                return storeNode(node);
            })
            .flatMap(v -> {
                // Update parent's children set
                if (node.getParentId() != null) {
                    return set().sadd(CHILDREN_PREFIX + node.getParentId(), nodeId)
                        .map(x -> toProto(node));
                } else {
                    // Add to root nodes
                    return set().sadd(ROOT_NODES, nodeId)
                        .map(x -> toProto(node));
                }
            });
    }
    
    @Override
    public Uni<Node> getNode(GetNodeRequest request) {
        if (request.getId() == null || request.getId().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        return loadNode(request.getId())
            .map(node -> {
                if (node == null) {
                    throw new StatusRuntimeException(
                        Status.NOT_FOUND.withDescription("Node not found")
                    );
                }
                return toProto(node);
            });
    }
    
    @Override
    public Uni<GetChildrenResponse> getChildren(GetChildrenRequest request) {
        String parentId = request.getParentId();
        
        Uni<Set<String>> childIds;
        if (parentId == null || parentId.isEmpty()) {
            // Get root nodes
            childIds = set().smembers(ROOT_NODES);
        } else {
            // Validate parent exists
            childIds = validateNodeExists(parentId)
                .flatMap(exists -> {
                    if (!exists) {
                        throw new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("Parent node not found")
                        );
                    }
                    return set().smembers(CHILDREN_PREFIX + parentId);
                });
        }
        
        return childIds
            .flatMap(ids -> {
                // Handle empty result set
                if (ids.isEmpty()) {
                    return Uni.createFrom().item(
                        GetChildrenResponse.newBuilder()
                            .setTotalCount(0)
                            .build()
                    );
                }
                
                // Load all child nodes
                List<Uni<Node>> nodeUnis = ids.stream()
                    .map(id -> loadNode(id).map(this::toProto))
                    .collect(Collectors.toList());
                
                return Uni.combine().all().unis(nodeUnis).with(nodes -> {
                    List<Node> nodeList = nodes.stream()
                            .filter(Objects::nonNull)
                            .map(n -> (Node) n)
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
        if (request.getId() == null || request.getId().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        return loadNode(request.getId())
            .flatMap(node -> {
                if (node == null) {
                    throw new StatusRuntimeException(
                        Status.NOT_FOUND.withDescription("Node not found")
                    );
                }
                
                // Update fields if provided
                if (request.getName() != null && !request.getName().isEmpty()) {
                    node.setName(request.getName());
                }
                
                if (request.hasPayload()) {
                    // Update payload
                    Any payload = request.getPayload();
                    Map<String, String> payloadMeta = new HashMap<>();
                    payloadMeta.put("nodeId", node.getId());
                    payloadMeta.put("filename", node.getName());
                    
                    return payloadRepository.updateAny(node.getPayloadRef(), payload, payloadMeta)
                        .flatMap(updated -> {
                            if (!updated && node.getPayloadRef() == null) {
                                // Create new payload
                                return payloadRepository.storeAny(payload, payloadMeta)
                                    .map(payloadId -> {
                                        node.setPayloadRef(payloadId);
                                        node.setPayloadTypeUrl(payload.getTypeUrl());
                                        node.setSize((long) payload.getSerializedSize());
                                        return node;
                                    });
                            }
                            node.setPayloadTypeUrl(payload.getTypeUrl());
                            node.setSize((long) payload.getSerializedSize());
                            return Uni.createFrom().item(node);
                        });
                }
                
                if (request.getMetadataCount() > 0) {
                    Map<String, String> newMetadata = new HashMap<>(request.getMetadataMap());
                    
                    // Check for icon SVG in metadata
                    if (newMetadata.containsKey("iconSvg")) {
                        try {
                            String validatedSvg = svgValidator.validateAndSanitize(newMetadata.get("iconSvg"));
                            node.setIconSvg(validatedSvg);
                            newMetadata.remove("iconSvg"); // Don't store in metadata after moving to iconSvg field
                        } catch (IllegalArgumentException e) {
                            LOG.warn("Invalid SVG provided for node update " + request.getId() + ": " + e.getMessage());
                            // Keep existing icon on validation failure
                        }
                    }
                    
                    node.getMetadata().putAll(newMetadata);
                }
                
                node.setUpdatedAt(Instant.now());
                
                return Uni.createFrom().item(node);
            })
            .flatMap(updatedNode -> storeNode(updatedNode)
                .map(v -> toProto(updatedNode)));
    }
    
    @Override
    public Uni<DeleteNodeResponse> deleteNode(DeleteNodeRequest request) {
        if (request.getId() == null || request.getId().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        return loadNode(request.getId())
            .flatMap(node -> {
                if (node == null) {
                    return Uni.createFrom().item(
                        DeleteNodeResponse.newBuilder()
                            .setSuccess(false)
                            .setDeletedCount(0)
                            .build()
                    );
                }
                
                return deleteNodeRecursive(node, request.getRecursive());
            });
    }
    
    // Helper methods
    
    private Uni<Void> storeNode(RedisFilesystemNode node) {
        String nodeKey = NODE_PREFIX + node.getId();
        Map<String, String> nodeData = new HashMap<>();
        
        nodeData.put("id", node.getId());
        nodeData.put("name", node.getName());
        nodeData.put("type", node.getType());
        if (node.getParentId() != null) nodeData.put("parentId", node.getParentId());
        if (node.getPath() != null) nodeData.put("path", node.getPath());
        if (node.getPayloadRef() != null) nodeData.put("payloadRef", node.getPayloadRef());
        if (node.getPayloadTypeUrl() != null) nodeData.put("payloadTypeUrl", node.getPayloadTypeUrl());
        if (node.getIconSvg() != null) nodeData.put("iconSvg", node.getIconSvg());
        if (node.getServiceType() != null) nodeData.put("serviceType", node.getServiceType());
        if (node.getPayloadType() != null) nodeData.put("payloadType", node.getPayloadType());
        if (node.getSize() != null) nodeData.put("size", node.getSize().toString());
        if (node.getMimeType() != null) nodeData.put("mimeType", node.getMimeType());
        nodeData.put("createdAt", node.getCreatedAt().toString());
        nodeData.put("updatedAt", node.getUpdatedAt().toString());
        
        // Store metadata separately with prefix
        node.getMetadata().forEach((k, v) -> nodeData.put("meta:" + k, v));
        
        return hash().hset(nodeKey, nodeData).replaceWithVoid();
    }
    
    private Uni<RedisFilesystemNode> loadNode(String nodeId) {
        String nodeKey = NODE_PREFIX + nodeId;
        
        return hash().hgetall(nodeKey)
            .map(data -> {
                if (data.isEmpty()) {
                    return null;
                }
                
                RedisFilesystemNode node = new RedisFilesystemNode();
                node.setId(data.get("id"));
                node.setName(data.get("name"));
                node.setType(data.get("type"));
                node.setParentId(data.get("parentId"));
                node.setPath(data.get("path"));
                node.setPayloadRef(data.get("payloadRef"));
                node.setPayloadTypeUrl(data.get("payloadTypeUrl"));
                // Validate stored SVG before loading
                String storedSvg = data.get("iconSvg");
                if (storedSvg != null) {
                    try {
                        node.setIconSvg(svgValidator.validateAndSanitize(storedSvg));
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Invalid SVG found in storage for node " + nodeId + ": " + e.getMessage());
                        node.setIconSvg(svgValidator.getDefaultIcon());
                    }
                }
                node.setServiceType(data.get("serviceType"));
                node.setPayloadType(data.get("payloadType"));
                
                if (data.get("size") != null) {
                    node.setSize(Long.parseLong(data.get("size")));
                }
                node.setMimeType(data.get("mimeType"));
                
                if (data.get("createdAt") != null) {
                    node.setCreatedAt(Instant.parse(data.get("createdAt")));
                }
                if (data.get("updatedAt") != null) {
                    node.setUpdatedAt(Instant.parse(data.get("updatedAt")));
                }
                
                // Extract metadata
                Map<String, String> metadata = new HashMap<>();
                data.forEach((k, v) -> {
                    if (k.startsWith("meta:")) {
                        metadata.put(k.substring(5), v);
                    }
                });
                node.setMetadata(metadata);
                
                return node;
            });
    }
    
    private Uni<String> validateParentExists(String parentId) {
        return loadNode(parentId)
            .map(parent -> {
                if (parent == null) {
                    throw new StatusRuntimeException(
                        Status.INVALID_ARGUMENT.withDescription("Parent node not found: " + parentId)
                    );
                }
                if (!"FOLDER".equals(parent.getType())) {
                    throw new StatusRuntimeException(
                        Status.INVALID_ARGUMENT.withDescription("Parent must be a folder")
                    );
                }
                return parent.getPath();
            });
    }
    
    private Uni<Boolean> validateNodeExists(String nodeId) {
        return redis.key(String.class).exists(NODE_PREFIX + nodeId);
    }
    
    private Uni<DeleteNodeResponse> deleteNodeRecursive(RedisFilesystemNode node, boolean recursive) {
        int deletedCount = 0;
        
        if ("FOLDER".equals(node.getType())) {
            // Get children
            return set().smembers(CHILDREN_PREFIX + node.getId())
                .flatMap(childIds -> {
                    if (!childIds.isEmpty() && !recursive) {
                        throw new StatusRuntimeException(
                            Status.INVALID_ARGUMENT.withDescription("Cannot delete non-empty folder without recursive flag")
                        );
                    }
                    
                    // Delete children recursively
                    List<Uni<Integer>> deletions = childIds.stream()
                        .map(childId -> loadNode(childId)
                            .flatMap(child -> child != null ? 
                                deleteNodeRecursive(child, true)
                                    .map(DeleteNodeResponse::getDeletedCount) : 
                                Uni.createFrom().item(0)))
                        .collect(Collectors.toList());
                    
                    return Uni.combine().all().unis(deletions).with(counts -> counts.stream()
                            .mapToInt(c -> (Integer) c)
                            .sum());
                })
                .flatMap(childDeletedCount -> {
                    // Delete the node itself
                    return deleteNodeOnly(node)
                        .map(deleted -> DeleteNodeResponse.newBuilder()
                            .setSuccess(deleted)
                            .setDeletedCount(deleted ? childDeletedCount + 1 : childDeletedCount)
                            .build());
                });
        } else {
            // Delete file node
            return deleteNodeOnly(node)
                .map(deleted -> DeleteNodeResponse.newBuilder()
                    .setSuccess(deleted)
                    .setDeletedCount(deleted ? 1 : 0)
                    .build());
        }
    }
    
    private Uni<Boolean> deleteNodeOnly(RedisFilesystemNode node) {
        // Delete payload if exists
        Uni<Boolean> payloadDeletion = node.getPayloadRef() != null ?
            payloadRepository.delete(node.getPayloadRef()) :
            Uni.createFrom().item(true);
        
        return payloadDeletion
            .flatMap(v -> {
                // Remove from parent's children or root nodes
                if (node.getParentId() != null) {
                    return set().srem(CHILDREN_PREFIX + node.getParentId(), node.getId());
                } else {
                    return set().srem(ROOT_NODES, node.getId());
                }
            })
            .flatMap(v -> {
                // Delete the node data
                return redis.key(String.class).del(NODE_PREFIX + node.getId())
                    .map(count -> count > 0);
            });
    }
    
    private Node toProto(RedisFilesystemNode entity) {
        if (entity == null) {
            return null;
        }
        
        Node.Builder builder = Node.newBuilder()
            .setId(entity.getId())
            .setName(entity.getName())
            .setType(Node.NodeType.valueOf(entity.getType()));
        
        if (entity.getParentId() != null) {
            builder.setParentId(entity.getParentId());
        }
        
        // Timestamps
        if (entity.getCreatedAt() != null) {
            builder.setCreatedAt(instantToTimestamp(entity.getCreatedAt()));
        }
        if (entity.getUpdatedAt() != null) {
            builder.setUpdatedAt(instantToTimestamp(entity.getUpdatedAt()));
        }
        
        // Other fields
        if (entity.getSize() != null) {
            builder.setSize(entity.getSize());
        }
        if (entity.getMimeType() != null) {
            builder.setMimeType(entity.getMimeType());
        }
        if (entity.getPath() != null) {
            builder.setPath(entity.getPath());
        }
        if (entity.getMetadata() != null) {
            builder.putAllMetadata(entity.getMetadata());
        }
        if (entity.getIconSvg() != null) {
            builder.setIconSvg(entity.getIconSvg());
        }
        if (entity.getServiceType() != null) {
            builder.setServiceType(entity.getServiceType());
        }
        if (entity.getPayloadType() != null) {
            builder.setPayloadType(entity.getPayloadType());
        }
        
        // Note: We don't load the payload here for efficiency
        // The payload can be retrieved separately when needed
        
        return builder.build();
    }
    
    private Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder()
            .setSeconds(instant.getEpochSecond())
            .setNanos(instant.getNano())
            .build();
    }
    
    /**
     * Updates the paths of all descendants when a folder is moved.
     */
    private Uni<Void> updateDescendantPaths(String nodeId, String newPath) {
        // Find all descendants (nodes whose path contains this nodeId)
        return keys().keys(NODE_PREFIX + "*")
            .flatMap(nodeKeys -> {
                if (nodeKeys.isEmpty()) {
                    return Uni.createFrom().voidItem();
                }
                
                List<Uni<Void>> updates = new ArrayList<>();
                for (String nodeKey : nodeKeys) {
                    updates.add(
                        hash().hget(nodeKey, "path")
                            .flatMap(path -> {
                                if (path != null && path.contains("," + nodeId + ",")) {
                                    // This is a descendant - update its path
                                    String oldPrefix = path.substring(0, path.indexOf("," + nodeId + ",") + nodeId.length() + 2);
                                    String newPrefix = newPath + nodeId + ",";
                                    String updatedPath = path.replace(oldPrefix, newPrefix);
                                    
                                    return hash().hset(nodeKey, "path", updatedPath)
                                        .replaceWithVoid();
                                }
                                return Uni.createFrom().voidItem();
                            })
                    );
                }
                
                return Uni.combine().all().unis(updates).discardItems();
            });
    }
    
    // Implement remaining methods (moveNode, copyNode, getPath, getTree, searchNodes)
    // These would follow similar patterns using Redis operations
    
    @Override
    public Uni<Node> moveNode(MoveNodeRequest request) {
        if (request.getNodeId() == null || request.getNodeId().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        return loadNode(request.getNodeId())
            .flatMap(node -> {
                if (node == null) {
                    throw new StatusRuntimeException(
                        Status.NOT_FOUND.withDescription("Node not found")
                    );
                }
                
                // Validate new parent if specified
                Uni<String> parentPathUni;
                if (request.getNewParentId() != null && !request.getNewParentId().isEmpty()) {
                    parentPathUni = validateParentExists(request.getNewParentId())
                        .map(parentPath -> {
                            // Check for circular reference
                            if (request.getNewParentId().equals(node.getId())) {
                                throw new StatusRuntimeException(
                                    Status.INVALID_ARGUMENT.withDescription("Cannot move node to itself")
                                );
                            }
                            
                            // Check if new parent is a descendant of the node being moved
                            if (parentPath.contains("," + node.getId() + ",")) {
                                throw new StatusRuntimeException(
                                    Status.INVALID_ARGUMENT.withDescription("Cannot move node to its own descendant")
                                );
                            }
                            
                            return parentPath + request.getNewParentId() + ",";
                        });
                } else {
                    // Moving to root
                    parentPathUni = Uni.createFrom().item(",");
                }
                
                return parentPathUni
                    .flatMap(newPath -> {
                        String oldParentId = node.getParentId();
                        
                        // Update node
                        node.setParentId(request.getNewParentId());
                        node.setPath(newPath);
                        if (request.getNewName() != null && !request.getNewName().isEmpty()) {
                            node.setName(request.getNewName());
                        }
                        node.setUpdatedAt(Instant.now());
                        
                        // Remove from old parent's children
                        Uni<Void> removeFromOldParent;
                        if (oldParentId != null && !oldParentId.isEmpty()) {
                            removeFromOldParent = set().srem(CHILDREN_PREFIX + oldParentId, node.getId())
                                .replaceWithVoid();
                        } else {
                            removeFromOldParent = set().srem(ROOT_NODES, node.getId())
                                .replaceWithVoid();
                        }
                        
                        // Add to new parent's children
                        Uni<Void> addToNewParent;
                        if (request.getNewParentId() != null && !request.getNewParentId().isEmpty()) {
                            addToNewParent = set().sadd(CHILDREN_PREFIX + request.getNewParentId(), node.getId())
                                .replaceWithVoid();
                        } else {
                            addToNewParent = set().sadd(ROOT_NODES, node.getId())
                                .replaceWithVoid();
                        }
                        
                        // Update all descendants' paths if it's a folder
                        final Uni<Void> updateDescendants = "FOLDER".equals(node.getType()) ?
                            updateDescendantPaths(node.getId(), node.getPath()) :
                            Uni.createFrom().voidItem();
                        
                        return removeFromOldParent
                            .flatMap(v -> addToNewParent)
                            .flatMap(v -> storeNode(node))
                            .flatMap(v -> updateDescendants)
                            .map(v -> toProto(node));
                    });
            });
    }
    
    @Override
    public Uni<Node> copyNode(CopyNodeRequest request) {
        if (request.getNodeId() == null || request.getNodeId().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        return loadNode(request.getNodeId())
            .flatMap(sourceNode -> {
                if (sourceNode == null) {
                    throw new StatusRuntimeException(
                        Status.NOT_FOUND.withDescription("Source node not found")
                    );
                }
                
                // Validate target parent
                Uni<String> parentPathUni;
                if (request.getTargetParentId() != null && !request.getTargetParentId().isEmpty()) {
                    parentPathUni = validateParentExists(request.getTargetParentId())
                        .map(parentPath -> parentPath + request.getTargetParentId() + ",");
                } else {
                    // Copying to root
                    parentPathUni = Uni.createFrom().item(",");
                }
                
                return parentPathUni
                    .flatMap(newPath -> {
                        // For deep copy of folders, we need to copy all descendants
                        if ("FOLDER".equals(sourceNode.getType()) && request.getDeep()) {
                            return copyNodeRecursive(sourceNode, request.getTargetParentId(), 
                                request.getNewName(), newPath);
                        } else {
                            // Simple copy for files or shallow folder copy
                            return copyNodeSimple(sourceNode, request.getTargetParentId(), 
                                request.getNewName(), newPath);
                        }
                    });
            });
    }
    
    /**
     * Simple copy of a single node (file or empty folder).
     */
    private Uni<Node> copyNodeSimple(RedisFilesystemNode sourceNode, String targetParentId, 
                                     String newName, String newPath) {
        String newNodeId = UUID.randomUUID().toString();
        RedisFilesystemNode newNode = new RedisFilesystemNode();
        
        // Copy all fields
        newNode.setId(newNodeId);
        newNode.setName(newName != null && !newName.isEmpty() ? newName : sourceNode.getName());
        newNode.setType(sourceNode.getType());
        newNode.setParentId(targetParentId);
        newNode.setPath(newPath);
        newNode.setCreatedAt(Instant.now());
        newNode.setUpdatedAt(Instant.now());
        
        // Copy metadata
        if (sourceNode.getMetadata() != null) {
            newNode.setMetadata(new HashMap<>(sourceNode.getMetadata()));
        }
        
        // Copy visual properties
        newNode.setIconSvg(sourceNode.getIconSvg());
        newNode.setServiceType(sourceNode.getServiceType());
        newNode.setPayloadType(sourceNode.getPayloadType());
        newNode.setMimeType(sourceNode.getMimeType());
        
        // Handle payload for files
        Uni<Void> copyPayload = Uni.createFrom().voidItem();
        if ("FILE".equals(sourceNode.getType()) && sourceNode.getPayloadRef() != null) {
            // Copy the payload
            copyPayload = payloadRepository.getAny(sourceNode.getPayloadRef())
                .flatMap(payload -> {
                    if (payload != null) {
                        Map<String, String> payloadMeta = new HashMap<>();
                        payloadMeta.put("nodeId", newNodeId);
                        payloadMeta.put("filename", newNode.getName());
                        payloadMeta.put("copiedFrom", sourceNode.getId());
                        
                        return payloadRepository.storeAny(payload, payloadMeta)
                            .map(newPayloadId -> {
                                newNode.setPayloadRef(newPayloadId);
                                newNode.setPayloadTypeUrl(sourceNode.getPayloadTypeUrl());
                                newNode.setSize(sourceNode.getSize());
                                return null;
                            });
                    }
                    return Uni.createFrom().voidItem();
                });
        }
        
        return copyPayload
            .flatMap(v -> storeNode(newNode))
            .flatMap(v -> {
                // Add to parent's children or root nodes
                if (targetParentId != null && !targetParentId.isEmpty()) {
                    return set().sadd(CHILDREN_PREFIX + targetParentId, newNodeId)
                        .map(x -> toProto(newNode));
                } else {
                    return set().sadd(ROOT_NODES, newNodeId)
                        .map(x -> toProto(newNode));
                }
            });
    }
    
    /**
     * Recursive copy of a folder and all its descendants.
     */
    private Uni<Node> copyNodeRecursive(RedisFilesystemNode sourceNode, String targetParentId,
                                        String newName, String newPath) {
        // First, copy the folder itself
        return copyNodeSimple(sourceNode, targetParentId, newName, newPath)
            .flatMap(copiedFolder -> {
                // Then copy all children
                return getChildNodes(sourceNode.getId())
                    .flatMap(children -> {
                        if (children.isEmpty()) {
                            return Uni.createFrom().item(copiedFolder);
                        }
                        
                        // Copy each child recursively
                        List<Uni<Node>> copyOperations = new ArrayList<>();
                        for (RedisFilesystemNode child : children) {
                            String childNewPath = newPath + copiedFolder.getId() + ",";
                            
                            if ("FOLDER".equals(child.getType())) {
                                copyOperations.add(
                                    copyNodeRecursive(child, copiedFolder.getId(), null, childNewPath)
                                );
                            } else {
                                copyOperations.add(
                                    copyNodeSimple(child, copiedFolder.getId(), null, childNewPath)
                                );
                            }
                        }
                        
                        return Uni.combine().all().unis(copyOperations)
                            .discardItems()
                            .map(v -> copiedFolder);
                    });
            });
    }
    
    /**
     * Helper to get child nodes directly as RedisFilesystemNode objects.
     */
    private Uni<List<RedisFilesystemNode>> getChildNodes(String parentId) {
        return set().smembers(CHILDREN_PREFIX + parentId)
            .flatMap(childIds -> {
                if (childIds.isEmpty()) {
                    return Uni.createFrom().item(Collections.emptyList());
                }
                
                List<Uni<RedisFilesystemNode>> loadOps = new ArrayList<>();
                for (String childId : childIds) {
                    loadOps.add(loadNode(childId));
                }
                
                return Uni.combine().all().unis(loadOps)
                    .with(results -> results.stream()
                        .filter(Objects::nonNull)
                        .map(obj -> (RedisFilesystemNode) obj)
                        .collect(Collectors.toList()));
            });
    }
    
    @Override
    public Uni<GetPathResponse> getPath(GetPathRequest request) {
        if (request.getId() == null || request.getId().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Node ID is required")
                )
            );
        }
        
        return loadNode(request.getId())
            .flatMap(node -> {
                if (node == null) {
                    throw new StatusRuntimeException(
                        Status.NOT_FOUND.withDescription("Node not found")
                    );
                }
                
                // Build path from root to this node
                List<Node> ancestors = new ArrayList<>();
                
                // Parse the path string to get node IDs
                String path = node.getPath();
                if (path != null && !path.equals(",")) {
                    // Path format is ",id1,id2,...,"
                    String[] nodeIds = path.substring(1, path.length() - 1).split(",");
                    
                    // Load all ancestor nodes
                    List<Uni<Node>> ancestorUnis = new ArrayList<>();
                    for (String ancestorId : nodeIds) {
                        if (!ancestorId.isEmpty()) {
                            ancestorUnis.add(
                                loadNode(ancestorId)
                                    .map(ancestor -> ancestor != null ? toProto(ancestor) : null)
                            );
                        }
                    }
                    
                    return Uni.combine().all().unis(ancestorUnis)
                        .with(results -> {
                            // Filter out nulls and add to ancestors
                            for (Object result : results) {
                                if (result != null) {
                                    ancestors.add((Node) result);
                                }
                            }
                            
                            // Add the current node as the last ancestor
                            ancestors.add(toProto(node));
                            
                            return GetPathResponse.newBuilder()
                                .addAllAncestors(ancestors)
                                .build();
                        });
                }
                
                // Node is at root level
                ancestors.add(toProto(node));
                
                return Uni.createFrom().item(
                    GetPathResponse.newBuilder()
                        .addAllAncestors(ancestors)
                        .build()
                );
            });
    }
    
    @Override
    public Uni<GetTreeResponse> getTree(GetTreeRequest request) {
        String rootId = request.getRootId();
        int maxDepth = request.getMaxDepth() > 0 ? request.getMaxDepth() : Integer.MAX_VALUE;
        
        if (rootId != null && !rootId.isEmpty()) {
            // Get tree starting from a specific node
            return loadNode(rootId)
                .flatMap(rootNode -> {
                    if (rootNode == null) {
                        throw new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("Root node not found")
                        );
                    }
                    
                    // Get children tree nodes
                    if ("FOLDER".equals(rootNode.getType()) && maxDepth > 0) {
                        return buildChildrenTreeNodes(rootNode, 0, maxDepth)
                            .map(children -> GetTreeResponse.newBuilder()
                                .setRoot(toProto(rootNode))
                                .addAllChildren(children)
                                .build());
                    } else {
                        return Uni.createFrom().item(
                            GetTreeResponse.newBuilder()
                                .setRoot(toProto(rootNode))
                                .build()
                        );
                    }
                });
        } else {
            // Get tree starting from all root nodes
            return set().smembers(ROOT_NODES)
                .flatMap(rootIds -> {
                    if (rootIds.isEmpty()) {
                        return Uni.createFrom().item(
                            GetTreeResponse.newBuilder()
                                .setRoot(Node.newBuilder()
                                    .setId("root")
                                    .setName("Root")
                                    .setType(Node.NodeType.FOLDER)
                                    .build())
                                .build()
                        );
                    }
                    
                    // Build tree for each root node
                    List<Uni<TreeNode>> treeUnis = new ArrayList<>();
                    for (String nodeId : rootIds) {
                        treeUnis.add(
                            loadNode(nodeId)
                                .flatMap(node -> node != null ? 
                                    buildTreeNode(node, 0, maxDepth) : 
                                    Uni.createFrom().nullItem())
                        );
                    }
                    
                    return Uni.combine().all().unis(treeUnis)
                        .with(trees -> {
                            List<TreeNode> children = trees.stream()
                                .filter(Objects::nonNull)
                                .map(obj -> (TreeNode) obj)
                                .collect(Collectors.toList());
                            
                            // Create a virtual root node to contain all root nodes
                            Node virtualRoot = Node.newBuilder()
                                .setId("root")
                                .setName("Root")
                                .setType(Node.NodeType.FOLDER)
                                .build();
                            
                            return GetTreeResponse.newBuilder()
                                .setRoot(virtualRoot)
                                .addAllChildren(children)
                                .build();
                        });
                });
        }
    }
    
    /**
     * Recursively builds a tree node structure.
     */
    private Uni<TreeNode> buildTreeNode(RedisFilesystemNode node, int currentDepth, int maxDepth) {
        TreeNode.Builder treeBuilder = TreeNode.newBuilder()
            .setNode(toProto(node));
        
        // Only add children if we haven't reached max depth and node is a folder
        if (currentDepth < maxDepth && "FOLDER".equals(node.getType())) {
            return set().smembers(CHILDREN_PREFIX + node.getId())
                .flatMap(childIds -> {
                    if (childIds.isEmpty()) {
                        return Uni.createFrom().item(treeBuilder.build());
                    }
                    
                    // Load and build tree for each child
                    List<Uni<TreeNode>> childTreeUnis = new ArrayList<>();
                    for (String childId : childIds) {
                        childTreeUnis.add(
                            loadNode(childId)
                                .flatMap(child -> child != null ?
                                    buildTreeNode(child, currentDepth + 1, maxDepth) :
                                    Uni.createFrom().nullItem())
                        );
                    }
                    
                    return Uni.combine().all().unis(childTreeUnis)
                        .with(childTrees -> {
                            List<TreeNode> children = childTrees.stream()
                                .filter(Objects::nonNull)
                                .map(obj -> (TreeNode) obj)
                                .sorted((a, b) -> {
                                    // Sort folders first, then by name
                                    boolean aIsFolder = a.getNode().getType() == Node.NodeType.FOLDER;
                                    boolean bIsFolder = b.getNode().getType() == Node.NodeType.FOLDER;
                                    if (aIsFolder && !bIsFolder) return -1;
                                    if (!aIsFolder && bIsFolder) return 1;
                                    return a.getNode().getName().compareTo(b.getNode().getName());
                                })
                                .collect(Collectors.toList());
                            
                            return treeBuilder.addAllChildren(children).build();
                        });
                });
        }
        
        return Uni.createFrom().item(treeBuilder.build());
    }
    
    /**
     * Builds a list of TreeNode children for a given parent node.
     */
    private Uni<List<TreeNode>> buildChildrenTreeNodes(RedisFilesystemNode parentNode, int currentDepth, int maxDepth) {
        return set().smembers(CHILDREN_PREFIX + parentNode.getId())
            .flatMap(childIds -> {
                if (childIds.isEmpty() || currentDepth >= maxDepth) {
                    return Uni.createFrom().item(Collections.emptyList());
                }
                
                // Load and build tree for each child
                List<Uni<TreeNode>> childTreeUnis = new ArrayList<>();
                for (String childId : childIds) {
                    childTreeUnis.add(
                        loadNode(childId)
                            .flatMap(child -> child != null ?
                                buildTreeNode(child, currentDepth + 1, maxDepth) :
                                Uni.createFrom().nullItem())
                    );
                }
                
                return Uni.combine().all().unis(childTreeUnis)
                    .with(childTrees -> {
                        return childTrees.stream()
                            .filter(Objects::nonNull)
                            .map(obj -> (TreeNode) obj)
                            .sorted((a, b) -> {
                                // Sort folders first, then by name
                                boolean aIsFolder = a.getNode().getType() == Node.NodeType.FOLDER;
                                boolean bIsFolder = b.getNode().getType() == Node.NodeType.FOLDER;
                                if (aIsFolder && !bIsFolder) return -1;
                                if (!aIsFolder && bIsFolder) return 1;
                                return a.getNode().getName().compareTo(b.getNode().getName());
                            })
                            .collect(Collectors.toList());
                    });
            });
    }
    
    @Override
    public Uni<SearchNodesResponse> searchNodes(SearchNodesRequest request) {
        // TODO: Consider integrating OpenSearch for more advanced search capabilities
        // This implementation uses basic Redis key scanning which may not scale well
        // for large datasets. OpenSearch would provide:
        // - Full-text search with relevance scoring
        // - Faceted search and aggregations
        // - Better performance for complex queries
        
        // Validate request
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Search query is required")
                )
            );
        }
        
        String query = request.getQuery().toLowerCase();
        Set<Node.NodeType> filterTypes = new HashSet<>(request.getTypesList());
        List<String> searchPaths = request.getPathsList();
        int limit = request.getPageSize() > 0 ? request.getPageSize() : 100; // Default to 100 results
        
        // Get all node keys
        return keys().keys(NODE_PREFIX + "*")
            .flatMap(nodeKeys -> {
                if (nodeKeys.isEmpty()) {
                    return Uni.createFrom().item(
                        SearchNodesResponse.newBuilder()
                            .setTotalCount(0)
                            .build()
                    );
                }
                
                // Load and filter nodes
                List<Uni<Node>> searchUnis = new ArrayList<>();
                for (String nodeKey : nodeKeys) {
                    String nodeId = nodeKey.substring(NODE_PREFIX.length());
                    
                    searchUnis.add(
                        loadNode(nodeId)
                            .map(node -> {
                                if (node == null) return null;
                                
                                // Check if node matches search criteria
                                boolean matches = true;
                                
                                // Check name match
                                if (!node.getName().toLowerCase().contains(query)) {
                                    // Also check metadata for matches
                                    boolean metadataMatch = false;
                                    for (String value : node.getMetadata().values()) {
                                        if (value.toLowerCase().contains(query)) {
                                            metadataMatch = true;
                                            break;
                                        }
                                    }
                                    if (!metadataMatch) {
                                        matches = false;
                                    }
                                }
                                
                                // Check type filter
                                if (!filterTypes.isEmpty() && 
                                    !filterTypes.contains(Node.NodeType.valueOf(node.getType()))) {
                                    matches = false;
                                }
                                
                                // Check path filter
                                if (!searchPaths.isEmpty()) {
                                    boolean inSearchPath = false;
                                    for (String searchPath : searchPaths) {
                                        if (node.getPath().startsWith(searchPath)) {
                                            inSearchPath = true;
                                            break;
                                        }
                                    }
                                    if (!inSearchPath) {
                                        matches = false;
                                    }
                                }
                                
                                return matches ? toProto(node) : null;
                            })
                    );
                }
                
                return Uni.combine().all().unis(searchUnis)
                    .with(results -> {
                        List<Node> matchingNodes = results.stream()
                            .filter(Objects::nonNull)
                            .map(obj -> (Node) obj)
                            .sorted((a, b) -> {
                                // Sort by relevance (exact match first)
                                boolean aExact = a.getName().equalsIgnoreCase(request.getQuery());
                                boolean bExact = b.getName().equalsIgnoreCase(request.getQuery());
                                if (aExact && !bExact) return -1;
                                if (!aExact && bExact) return 1;
                                
                                // Then by type (folders first)
                                if (a.getType() == Node.NodeType.FOLDER && b.getType() != Node.NodeType.FOLDER) return -1;
                                if (a.getType() != Node.NodeType.FOLDER && b.getType() == Node.NodeType.FOLDER) return 1;
                                
                                // Finally by name
                                return a.getName().compareTo(b.getName());
                            })
                            .limit(limit)
                            .collect(Collectors.toList());
                        
                        return SearchNodesResponse.newBuilder()
                            .addAllNodes(matchingNodes)
                            .setTotalCount(matchingNodes.size())
                            .build();
                    });
            });
    }
}