
package io.pipeline.data.util.json;


import io.pipeline.api.model.PipelineStepConfig;

public class SampleObjects {

    public static class ProcessorInfo {
        public static PipelineStepConfig.ProcessorInfo createReasonable() {
            return new PipelineStepConfig.ProcessorInfo(null, "simple-processor-bean");
        }

        public static PipelineStepConfig.ProcessorInfo createFull() {
            return new PipelineStepConfig.ProcessorInfo("fully.qualified.grpc.ProcessorService", null);
        }
    }
}
