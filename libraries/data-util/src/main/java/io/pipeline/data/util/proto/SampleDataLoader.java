package io.pipeline.data.util.proto;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.module.ModuleProcessResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for loading sample data from resources.
 */
@Singleton
public class SampleDataLoader {
    private static final Logger logger = Logger.getLogger(SampleDataLoader.class);

    @Inject
    SampleDataCreator sampleDataCreator;


    private static final String SAMPLE_PATH = "/sample-data/";
    private static final String DEFAULT_SAMPLE = "sample-pipestream-1.bin";

    /**
     * Loads a resource file as a string.
     */
    public String loadResourceAsString(String resourcePath) {
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
    public <T extends Message.Builder> T loadProtobufFromJson(String resourcePath, T builder) {
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
    public <T extends Message.Builder> List<T> loadProtobufListFromJson(
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
                        logger.error("Failed to parse object: " + currentObject, e);
                    }
                    inObject = false;
                }
            } else if (inObject) {
                currentObject.append(line).append("\n");
            }
        }
        
        return results;
    }


    /**
     * Loads a ProcessResponse from a sample file.
     * @param filename the filename (e.g., "sample-pipestream-1.bin")
     * @return ProcessResponse object
     * @throws IOException if the resource cannot be read
     */
    public ModuleProcessResponse loadSampleProcessResponse(String filename) throws IOException {
        try (InputStream is = SampleDataLoader.class.getResourceAsStream(SAMPLE_PATH + filename)) {
            if (is == null) {
                throw new IOException("Sample file not found: " + filename);
            }
            return ModuleProcessResponse.parseFrom(is);
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse ProcessResponse from " + filename, e);
        }
    }

    /**
     * Loads the default sample ProcessResponse from resources.
     * @return ProcessResponse object
     * @throws IOException if the resource cannot be read
     */
    public ModuleProcessResponse loadDefaultSampleProcessResponse() throws IOException {
        return loadSampleProcessResponse(DEFAULT_SAMPLE);
    }

    /**
     * Gets the PipeDoc from a ProcessResponse sample file.
     * @param filename the filename (e.g., "sample-pipestream-1.bin")
     * @return PipeDoc object, creates a default sample if the ProcessResponse doesn't contain an output document
     * @throws IOException if the resource cannot be read
     */
    public PipeDoc loadSamplePipeDocFromResponse(String filename) throws IOException {
        try {
            ModuleProcessResponse response = loadSampleProcessResponse(filename);
            if (response.hasOutputDoc()) {
                return response.getOutputDoc();
            }
        } catch (IOException e) {
            // If we can't load the file or it's not a ProcessResponse, fall back to created sample
        }

        // If no document found in file, create a sample document
        return sampleDataCreator.createDefaultSamplePipeDoc();
    }

    /**
     * Loads a specific sample PipeStream by filename.
     * @param filename the filename (e.g., "sample-pipestream-1.bin")
     * @return PipeStream object
     * @throws IOException if the resource cannot be read
     */
    public PipeStream loadSamplePipeStream(String filename) throws IOException {
        try (InputStream is = SampleDataLoader.class.getResourceAsStream(SAMPLE_PATH + filename)) {
            if (is == null) {
                throw new IOException("Sample file not found: " + filename);
            }
            return PipeStream.parseFrom(is);
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse PipeStream from " + filename, e);
        }
    }


}