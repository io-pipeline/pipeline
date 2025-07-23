
package io.pipeline.test.support;

import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.model.StepType;

import java.util.Map;

public class PipelineConfigFactory {

    public static PipelineConfig createSimpleLinearPipeline(String serviceA, String serviceB) {
        return new PipelineConfig("simple-linear-pipeline", Map.of(
                "step-a",
                new PipelineStepConfig(
                        "step-a",
                        StepType.PIPELINE,
                        new PipelineStepConfig.ProcessorInfo(serviceA)
                ),
                "step-b",
                new PipelineStepConfig(
                        "step-b",
                        StepType.PIPELINE,
                        new PipelineStepConfig.ProcessorInfo(serviceB)
                )
        ));
    }
}
