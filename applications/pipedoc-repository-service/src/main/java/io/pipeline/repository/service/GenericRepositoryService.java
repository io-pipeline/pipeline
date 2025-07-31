package io.pipeline.repository.service;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.smallrye.mutiny.Uni;

/**
 * Generic repository service that provides both typed and Any-based operations.
 * This allows for type-safe operations when the type is known at compile time,
 * and flexible Any-based operations for dynamic scenarios like filesystem browsers.
 */
public interface GenericRepositoryService {
    
    /**
     * Store any protobuf message wrapped in Any.
     * This is the most flexible method for filesystem browsers and dynamic UIs.
     */
    Uni<String> storeAny(Any message, java.util.Map<String, String> metadata);
    
    /**
     * Store a typed protobuf message (internally wraps in Any).
     * Type-safe version for when you know the message type at compile time.
     */
    <T extends Message> Uni<String> store(T message, java.util.Map<String, String> metadata);
    
    /**
     * Retrieve an Any-wrapped message by ID.
     * Useful for filesystem browsers that need to handle multiple types.
     */
    Uni<Any> getAny(String id);
    
    /**
     * Retrieve and unpack a typed message by ID.
     * Type-safe version that automatically unpacks the Any wrapper.
     */
    <T extends Message> Uni<T> get(String id, Class<T> messageClass);
    
    /**
     * Update a message (Any-wrapped).
     */
    Uni<Boolean> updateAny(String id, Any message, java.util.Map<String, String> metadata);
    
    /**
     * Update a typed message.
     */
    <T extends Message> Uni<Boolean> update(String id, T message, java.util.Map<String, String> metadata);
    
    /**
     * Delete a message by ID.
     */
    Uni<Boolean> delete(String id);
    
    /**
     * List all message IDs of a specific protobuf type.
     * @param messageClass The protobuf message class to filter by
     */
    <T extends Message> Uni<java.util.List<String>> listByType(Class<T> messageClass);
    
    /**
     * List all message IDs of a specific type URL.
     * More flexible than listByType when working with Any messages.
     */
    Uni<java.util.List<String>> listByTypeUrl(String typeUrl);
    
    /**
     * Search messages by metadata.
     */
    Uni<java.util.List<String>> searchByMetadata(java.util.Map<String, String> metadataFilters);
    
    /**
     * Get metadata without loading the payload.
     */
    Uni<java.util.Map<String, String>> getMetadata(String id);
    
    /**
     * Check if a message exists.
     */
    Uni<Boolean> exists(String id);

    Uni<Long> getSize(String id);

    /**
     * Get the type URL of a stored message without loading the payload.
     */
    Uni<String> getTypeUrl(String id);
    
    /**
     * Batch operations for efficiency
     */
    <T extends Message> Uni<java.util.List<String>> batchStore(java.util.List<T> messages, java.util.Map<String, String> commonMetadata);
    
    /**
     * Batch retrieve with automatic type detection
     */
    Uni<java.util.Map<String, Any>> batchGetAny(java.util.List<String> ids);
    
    /**
     * Clear all data from the repository.
     * This will delete all keys under the configured prefix.
     * @param typeUrls Optional list of type URLs to filter deletion. If empty, deletes all types.
     * @return Count of deleted items by type
     */
    Uni<java.util.Map<String, Long>> clearAll(java.util.List<String> typeUrls);
}