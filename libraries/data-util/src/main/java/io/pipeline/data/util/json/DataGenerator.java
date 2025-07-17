
package io.pipeline.data.util.json;


import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DataGenerator {

    public void produceData(String outputDir) throws Exception {
        if (outputDir == null || outputDir.isEmpty()) {
            System.err.println("Output directory cannot be null or empty");
            System.exit(1);
        }
        SampleDataStorageService storageService = new SampleDataStorageService();

        // --- ProcessorInfo ---
        storageService.saveSampleData(
                "ProcessorInfo",
                "01-reasonable",
                SampleObjects.ProcessorInfo.createReasonable()
        );
        storageService.saveSampleData(
                "ProcessorInfo",
                "02-full",
                SampleObjects.ProcessorInfo.createFull()
        );
    }
}
