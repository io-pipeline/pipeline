package io.pipeline.repository.service;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.hash.ReactiveHashCommands;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.set.ReactiveSetCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis implementation of the generic repository.
 * Uses claim check pattern for efficient storage of large protobuf messages.
 * Provides both typed and Any-based operations for maximum flexibility.
 */
@ApplicationScoped
public class RedisGenericRepository implements GenericRepositoryService {
    
    private static final Logger LOG = Logger.getLogger(RedisGenericRepository.class);
    private static final String METADATA_PREFIX = "meta:";
    private static final String PAYLOAD_PREFIX = "payload:";
    private static final String TYPE_INDEX_PREFIX = "type:";
    private static final String ALL_IDS_SET = "all:ids";
    
    @Inject
    ReactiveRedisDataSource redis;
    
    private ReactiveHashCommands<String, String, String> hash() {
        return redis.hash(String.class, String.class, String.class);
    }
    
    private ReactiveValueCommands<String, byte[]> value() {
        return redis.value(String.class, byte[].class);
    }
    
    private ReactiveSetCommands<String, String> set() {
        return redis.set(String.class, String.class);
    }
    
    private ReactiveKeyCommands<String> keys() {
        return redis.key(String.class);
    }
    
    @Override
    public Uni<String> storeAny(Any message, Map<String, String> metadata) {
        String id = UUID.randomUUID().toString();
        return storeAnyWithId(id, message, metadata);
    }
    
    @Override
    public <T extends Message> Uni<String> store(T message, Map<String, String> metadata) {
        return storeAny(Any.pack(message), metadata);
    }
    
    private Uni<String> storeAnyWithId(String id, Any message, Map<String, String> metadata) {
        return Uni.createFrom().item(() -> {
            // Prepare metadata
            Map<String, String> fullMetadata = new HashMap<>(metadata != null ? metadata : new HashMap<>());
            fullMetadata.put("_id", id);
            fullMetadata.put("_typeUrl", message.getTypeUrl());
            fullMetadata.put("_size", String.valueOf(message.getSerializedSize()));
            fullMetadata.put("_createdAt", Instant.now().toString());
            fullMetadata.put("_updatedAt", Instant.now().toString());
            
            return fullMetadata;
        })
        .flatMap(fullMetadata -> {
            // Store payload - store the wrapped message's value, not the Any wrapper
            String payloadKey = PAYLOAD_PREFIX + id;
            return value().set(payloadKey, message.getValue().toByteArray())
                .flatMap(v -> {
                    // Store metadata
                    String metaKey = METADATA_PREFIX + id;
                    return hash().hset(metaKey, fullMetadata);
                })
                .flatMap(v -> {
                    // Add to type index
                    String typeKey = TYPE_INDEX_PREFIX + fullMetadata.get("_typeUrl");
                    return set().sadd(typeKey, id);
                })
                .flatMap(v -> {
                    // Add to all IDs set
                    return set().sadd(ALL_IDS_SET, id);
                })
                .map(v -> id);
        });
    }
    
    @Override
    public <T extends Message> Uni<T> get(String id, Class<T> messageClass) {
        return getAny(id)
            .map(any -> {
                if (any == null) {
                    return null;
                }
                try {
                    // Use the Any's unpack method with the provided class
                    return any.unpack(messageClass);
                } catch (InvalidProtocolBufferException e) {
                    LOG.error("Failed to unpack message", e);
                    return null;
                }
            });
    }
    
    @Override
    public Uni<Any> getAny(String id) {
        String payloadKey = PAYLOAD_PREFIX + id;
        String metaKey = METADATA_PREFIX + id;
        
        // First get metadata to know the type
        return hash().hget(metaKey, "_typeUrl")
            .flatMap(typeUrl -> {
                if (typeUrl == null) {
                    return Uni.createFrom().nullItem();
                }
                
                // Then get the payload
                return value().get(payloadKey)
                    .map(bytes -> {
                        if (bytes == null) {
                            return null;
                        }
                        
                        // Wrap in Any
                        return Any.newBuilder()
                            .setTypeUrl(typeUrl)
                            .setValue(com.google.protobuf.ByteString.copyFrom(bytes))
                            .build();
                    });
            });
    }
    
    @Override
    public Uni<Boolean> updateAny(String id, Any message, Map<String, String> metadata) {
        return exists(id)
            .flatMap(exists -> {
                if (!exists) {
                    return Uni.createFrom().item(false);
                }
                
                // Get existing metadata
                String metaKey = METADATA_PREFIX + id;
                return hash().hgetall(metaKey)
                    .flatMap(existingMeta -> {
                        // Update metadata
                        Map<String, String> updatedMeta = new HashMap<>(existingMeta);
                        if (metadata != null) {
                            updatedMeta.putAll(metadata);
                        }
                        updatedMeta.put("_updatedAt", Instant.now().toString());
                        updatedMeta.put("_size", String.valueOf(message.getSerializedSize()));
                        
                        // Update payload - store the wrapped message's value, not the Any wrapper
                        String payloadKey = PAYLOAD_PREFIX + id;
                        return value().set(payloadKey, message.getValue().toByteArray())
                            .flatMap(v -> hash().hset(metaKey, updatedMeta))
                            .map(v -> true);
                    });
            });
    }
    
    @Override
    public Uni<Boolean> delete(String id) {
        String payloadKey = PAYLOAD_PREFIX + id;
        String metaKey = METADATA_PREFIX + id;
        
        // Get type for index cleanup
        return hash().hget(metaKey, "_typeUrl")
            .flatMap(typeUrl -> {
                if (typeUrl == null) {
                    return Uni.createFrom().item(false);
                }
                
                // Delete from type index
                String typeKey = TYPE_INDEX_PREFIX + typeUrl;
                return set().srem(typeKey, id)
                    .flatMap(v -> set().srem(ALL_IDS_SET, id))
                    .flatMap(v -> keys().del(payloadKey, metaKey))
                    .map(deletedCount -> deletedCount > 0);
            });
    }
    
    @Override
    public <T extends Message> Uni<Boolean> update(String id, T message, Map<String, String> metadata) {
        return updateAny(id, Any.pack(message), metadata);
    }
    
    @Override
    public <T extends Message> Uni<List<String>> listByType(Class<T> messageClass) {
        // Get the type URL for this class by creating a dummy instance
        String typeUrl = Any.pack(getDefaultInstance(messageClass)).getTypeUrl();
        return listByTypeUrl(typeUrl);
    }
    
    @Override
    public Uni<List<String>> listByTypeUrl(String typeUrl) {
        String typeKey = TYPE_INDEX_PREFIX + typeUrl;
        return set().smembers(typeKey)
            .map(members -> new ArrayList<>(members));
    }
    
    @Override
    public Uni<List<String>> searchByMetadata(Map<String, String> metadataFilters) {
        // Get all IDs and filter by metadata
        return set().smembers(ALL_IDS_SET)
            .flatMap(ids -> {
                // Check each ID's metadata
                List<Uni<String>> checks = ids.stream()
                    .map(id -> {
                        String metaKey = METADATA_PREFIX + id;
                        return hash().hgetall(metaKey)
                            .map(meta -> {
                                // Check if all filters match
                                for (Map.Entry<String, String> filter : metadataFilters.entrySet()) {
                                    if (!filter.getValue().equals(meta.get(filter.getKey()))) {
                                        return null;
                                    }
                                }
                                return id;
                            });
                    })
                    .collect(Collectors.toList());
                
                return Uni.combine().all().unis(checks).with(results -> results.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(Collectors.toList()));
            });
    }
    
    @Override
    public Uni<Map<String, String>> getMetadata(String id) {
        String metaKey = METADATA_PREFIX + id;
        return hash().hgetall(metaKey);
    }
    
    @Override
    public Uni<Boolean> exists(String id) {
        String metaKey = METADATA_PREFIX + id;
        return keys().exists(metaKey);
    }
    
    @Override
    public Uni<Long> getSize(String id) {
        String metaKey = METADATA_PREFIX + id;
        return hash().hget(metaKey, "_size")
            .map(size -> size != null ? Long.parseLong(size) : -1L);
    }
    
    @Override
    public Uni<String> getTypeUrl(String id) {
        String metaKey = METADATA_PREFIX + id;
        return hash().hget(metaKey, "_typeUrl");
    }
    
    @Override
    public <T extends Message> Uni<List<String>> batchStore(List<T> messages, Map<String, String> commonMetadata) {
        List<Uni<String>> storeOps = messages.stream()
            .map(msg -> store(msg, commonMetadata))
            .collect(Collectors.toList());
        
        return Uni.combine().all().unis(storeOps).with(results -> results.stream()
                .map(Object::toString)
                .collect(Collectors.toList()));
    }
    
    @Override
    public Uni<Map<String, Any>> batchGetAny(List<String> ids) {
        List<Uni<Any>> getOps = ids.stream()
            .map(this::getAny)
            .collect(Collectors.toList());
        
        return Uni.combine().all().unis(getOps).with(results -> {
            Map<String, Any> resultMap = new HashMap<>();
            for (int i = 0; i < ids.size(); i++) {
                Any value = (Any) results.get(i);
                if (value != null) {
                    resultMap.put(ids.get(i), value);
                }
            }
            return resultMap;
        });
    }
    
    /**
     * Helper method to get default instance of a protobuf message class.
     * Uses the static getDefaultInstance() method that all protobuf messages have.
     */
    @SuppressWarnings("unchecked")
    private <T extends Message> T getDefaultInstance(Class<T> messageClass) {
        try {
            java.lang.reflect.Method method = messageClass.getMethod("getDefaultInstance");
            return (T) method.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get default instance for " + messageClass.getName(), e);
        }
    }
}