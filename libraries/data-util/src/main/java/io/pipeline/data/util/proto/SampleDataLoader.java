package io.pipeline.data.util.proto;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for loading sample data from resources.
 */
public class SampleDataLoader {
    private static final Logger logger = Logger.getLogger(SampleDataLoader.class);

    /**
     * Loads a resource file as a string.
     */
    public static String loadResourceAsString(String resourcePath) {
        try (InputStream is = SampleDataLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + resourcePath, e);
        }
    }

    /**
     * Loads a protobuf message from a JSON resource file.
     */
    public static <T extends Message.Builder> T loadProtobufFromJson(String resourcePath, T builder) {
        String json = loadResourceAsString(resourcePath);
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
            return builder;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse protobuf from JSON: " + resourcePath, e);
        }
    }

    /**
     * Loads a list of protobuf messages from a JSON array resource file.
     */
    public static <T extends Message.Builder> List<T> loadProtobufListFromJson(
            String resourcePath, 
            java.util.function.Supplier<T> builderSupplier) {
        String json = loadResourceAsString(resourcePath);
        List<T> results = new ArrayList<>();
        
        // Simple JSON array parsing - assumes each object is on its own line
        String[] lines = json.split("\n");
        StringBuilder currentObject = new StringBuilder();
        boolean inObject = false;
        
        for (String line : lines) {
            if (line.trim().equals("{")) {
                inObject = true;
                currentObject = new StringBuilder();
                currentObject.append(line).append("\n");
            } else if (line.trim().equals("},") || line.trim().equals("}")) {
                if (inObject) {
                    currentObject.append("}");
                    T builder = builderSupplier.get();
                    try {
                        JsonFormat.parser().ignoringUnknownFields().merge(currentObject.toString(), builder);
                        results.add(builder);
                    } catch (Exception e) {
                        logger.error("Failed to parse object: " + currentObject.toString(), e);
                    }
                    inObject = false;
                }
            } else if (inObject) {
                currentObject.append(line).append("\n");
            }
        }
        
        return results;
    }
}