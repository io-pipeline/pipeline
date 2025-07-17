package io.pipeline.data.util.proto;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for working with Protocol Buffer messages.
 */
public class ProtobufUtils {
    private static final Logger logger = Logger.getLogger(ProtobufUtils.class);

    /**
     * Saves a protobuf message to a binary file.
     */
    public static void saveMessageToBinaryFile(Message message, Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            message.writeTo(fos);
        }
        logger.infof("Saved message to binary file: %s", filePath);
    }

    /**
     * Loads a protobuf message from a binary file.
     */
    public static <T extends Message> T loadMessageFromBinaryFile(Path filePath, T defaultInstance) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            @SuppressWarnings("unchecked")
            T message = (T) defaultInstance.newBuilderForType().mergeFrom(fis).build();
            return message;
        }
    }

    /**
     * Converts a protobuf message to JSON string.
     */
    public static String toJson(Message message) {
        try {
            return JsonFormat.printer()
                    .includingDefaultValueFields()
                    .preservingProtoFieldNames()
                    .print(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert message to JSON", e);
        }
    }

    /**
     * Converts a protobuf message to pretty-printed JSON string.
     */
    public static String toPrettyJson(Message message) {
        try {
            return JsonFormat.printer()
                    .includingDefaultValueFields()
                    .preservingProtoFieldNames()
                    .print(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert message to pretty JSON", e);
        }
    }

    /**
     * Creates a deep copy of a protobuf message.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Message> T deepCopy(T message) {
        return (T) message.toBuilder().build();
    }

    /**
     * Loads multiple protobuf messages from binary files in a directory.
     */
    public static <T extends Message> List<T> loadMessagesFromDirectory(Path directory, T defaultInstance) throws IOException {
        List<T> messages = new ArrayList<>();
        
        if (!Files.exists(directory)) {
            logger.warnf("Directory does not exist: %s", directory);
            return messages;
        }

        Files.list(directory)
                .filter(path -> path.toString().endsWith(".bin"))
                .sorted()
                .forEach(path -> {
                    try {
                        T message = loadMessageFromBinaryFile(path, defaultInstance);
                        messages.add(message);
                        logger.debugf("Loaded message from: %s", path);
                    } catch (IOException e) {
                        logger.errorf(e, "Failed to load message from: %s", path);
                    }
                });

        logger.infof("Loaded %d messages from directory: %s", messages.size(), directory);
        return messages;
    }
}