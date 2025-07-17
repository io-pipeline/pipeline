# Loop Detection Validator Archive

This directory contains archived implementations of loop detection validators from various pipeline projects. These files represent different attempts at implementing circular dependency detection in pipeline configurations.

## Directory Structure

### grpc_validators/
- **GrpcLoopValidator_yappy_models.java** - GRPC loop detection validator from yappy-models project

### inter_pipeline/
- **InterPipelineLoopValidator_gitlab_backup.java** - Backup implementation from GitLab
- **InterPipelineLoopValidator_rokkon_engine_fix.java** - Engine fix implementation
- **InterPipelineLoopValidator_rokkon_old_main.java** - Old main rokkon implementation
- **InterPipelineLoopValidator_yappy_consul.java** - Consul-based implementation (fully implemented)
- **InterPipelineLoopValidator_yappy_models.java** - Models-based implementation
- **InterPipelineLoopValidatorTest_yappy_consul.java** - Test for consul implementation

### intra_pipeline/
- **IntraPipelineLoopValidator_gitlab_backup.java** - Backup implementation from GitLab
- **IntraPipelineLoopValidator_rokkon_engine_fix.java** - Engine fix implementation
- **IntraPipelineLoopValidator_rokkon_old_main.java** - Old main rokkon implementation
- **IntraPipelineLoopValidator_yappy_consul.java** - Consul-based implementation (fully implemented)
- **IntraPipelineLoopValidator_yappy_models.java** - Models-based implementation
- **IntraPipelineLoopValidatorTest_yappy_consul.java** - Test for consul implementation

### test_classes/
- **InterPipelineLoopValidatorIT_rokkon_pristine.java** - Integration test
- **InterPipelineLoopValidatorTestBase_first_try.java** - Base test class
- **InterPipelineLoopValidatorTest_rokkon_pristine.java** - Unit test
- **IntraPipelineLoopValidatorIT_rokkon_pristine.java** - Integration test
- **IntraPipelineLoopValidatorTestBase_first_try.java** - Base test class
- **IntraPipelineLoopValidatorTest_rokkon_pristine.java** - Unit test
- **LoopValidationTest_quarkus.java** - Quarkus Qute template validation test

## Implementation Notes

### Fully Implemented Validators
- **yappy_consul implementations** - These contain complete working implementations using JGraphT library with Johnson's Simple Cycles algorithm for cycle detection
- **GrpcLoopValidator** - Contains complete GRPC-specific loop detection using DFS algorithm

### Key Features in Implemented Validators
- Graph-based cycle detection using JGraphT
- Kafka topic pattern resolution with variable substitution
- Comprehensive error reporting with cycle paths
- Support for both inter-pipeline and intra-pipeline loop detection
- Proper logging and validation result handling

### Archive Purpose
These files were archived to preserve different implementation approaches and prevent loss of development work. The implementations show evolution of the loop detection feature across different projects and attempts.

## Usage
These files are for reference only. They represent historical implementations and development attempts that were consolidated to avoid duplication across multiple projects.