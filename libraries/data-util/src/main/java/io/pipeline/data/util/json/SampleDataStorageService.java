package io.pipeline.data.util.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipeline.api.json.ObjectMapperFactory;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@ApplicationScoped
public class SampleDataStorageService {

    ObjectMapper objectMapper;
    private Path outputDir;

    @ConfigProperty(name = "sample.data.dir", defaultValue = "sample-data")
    String baseDataDir;

    public SampleDataStorageService() {
        this.objectMapper = ObjectMapperFactory.createConfiguredMapper();
    }
    
    private Path getOutputDir() {
        if (outputDir == null) {
            outputDir = Paths.get(baseDataDir);
        }
        return outputDir;
    }

    public <T> void saveSampleData(String className, String sampleName, T dataObject) throws IOException {
        Path classDir = getOutputDir().resolve(className);
        Files.createDirectories(classDir);

        // Save the JSON
        Path jsonFile = classDir.resolve(sampleName + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile.toFile(), dataObject);
        System.out.println("Wrote JSON to: " + jsonFile.toAbsolutePath());

        // Save the Java object
        Path objectFile = classDir.resolve(sampleName + ".ser");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(objectFile.toFile()))) {
            oos.writeObject(dataObject);
        }
        System.out.println("Wrote Java Object to: " + objectFile.toAbsolutePath());
    }
}