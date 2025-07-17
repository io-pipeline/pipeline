package io.pipeline.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Defines the transport mechanism for a pipeline step.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Transport mechanism for pipeline steps")
public enum TransportType {
    KAFKA,
    GRPC,
    INTERNAL // For steps executed directly by the pipeline engine
}