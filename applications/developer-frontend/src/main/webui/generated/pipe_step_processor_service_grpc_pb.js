// GENERATED CODE -- DO NOT EDIT!

'use strict';
var grpc = require('@grpc/grpc-js');
var pipe_step_processor_service_pb = require('./pipe_step_processor_service_pb.js');
var pipeline_core_types_pb = require('./pipeline_core_types_pb.js');
var google_protobuf_struct_pb = require('google-protobuf/google/protobuf/struct_pb.js');
var google_protobuf_timestamp_pb = require('google-protobuf/google/protobuf/timestamp_pb.js');

function serialize_io_pipeline_search_model_ModuleProcessRequest(arg) {
  if (!(arg instanceof pipe_step_processor_service_pb.ModuleProcessRequest)) {
    throw new Error('Expected argument of type io.pipeline.search.model.ModuleProcessRequest');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_search_model_ModuleProcessRequest(buffer_arg) {
  return pipe_step_processor_service_pb.ModuleProcessRequest.deserializeBinary(new Uint8Array(buffer_arg));
}

function serialize_io_pipeline_search_model_ModuleProcessResponse(arg) {
  if (!(arg instanceof pipe_step_processor_service_pb.ModuleProcessResponse)) {
    throw new Error('Expected argument of type io.pipeline.search.model.ModuleProcessResponse');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_search_model_ModuleProcessResponse(buffer_arg) {
  return pipe_step_processor_service_pb.ModuleProcessResponse.deserializeBinary(new Uint8Array(buffer_arg));
}

function serialize_io_pipeline_search_model_RegistrationRequest(arg) {
  if (!(arg instanceof pipe_step_processor_service_pb.RegistrationRequest)) {
    throw new Error('Expected argument of type io.pipeline.search.model.RegistrationRequest');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_search_model_RegistrationRequest(buffer_arg) {
  return pipe_step_processor_service_pb.RegistrationRequest.deserializeBinary(new Uint8Array(buffer_arg));
}

function serialize_io_pipeline_search_model_ServiceRegistrationResponse(arg) {
  if (!(arg instanceof pipe_step_processor_service_pb.ServiceRegistrationResponse)) {
    throw new Error('Expected argument of type io.pipeline.search.model.ServiceRegistrationResponse');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_search_model_ServiceRegistrationResponse(buffer_arg) {
  return pipe_step_processor_service_pb.ServiceRegistrationResponse.deserializeBinary(new Uint8Array(buffer_arg));
}


// Service definition for a pipeline step processor.
// This interface is implemented by developer-created gRPC modules/services.
var PipeStepProcessorService = exports.PipeStepProcessorService = {
  // Processes a document according to the step's configuration and logic.
processData: {
    path: '/io.pipeline.search.model.PipeStepProcessor/ProcessData',
    requestStream: false,
    responseStream: false,
    requestType: pipe_step_processor_service_pb.ModuleProcessRequest,
    responseType: pipe_step_processor_service_pb.ModuleProcessResponse,
    requestSerialize: serialize_io_pipeline_search_model_ModuleProcessRequest,
    requestDeserialize: deserialize_io_pipeline_search_model_ModuleProcessRequest,
    responseSerialize: serialize_io_pipeline_search_model_ModuleProcessResponse,
    responseDeserialize: deserialize_io_pipeline_search_model_ModuleProcessResponse,
  },
  // Test version of ProcessData that doesn't count as a legitimate call
// but follows the same processing logic
testProcessData: {
    path: '/io.pipeline.search.model.PipeStepProcessor/TestProcessData',
    requestStream: false,
    responseStream: false,
    requestType: pipe_step_processor_service_pb.ModuleProcessRequest,
    responseType: pipe_step_processor_service_pb.ModuleProcessResponse,
    requestSerialize: serialize_io_pipeline_search_model_ModuleProcessRequest,
    requestDeserialize: deserialize_io_pipeline_search_model_ModuleProcessRequest,
    responseSerialize: serialize_io_pipeline_search_model_ModuleProcessResponse,
    responseDeserialize: deserialize_io_pipeline_search_model_ModuleProcessResponse,
  },
  // Returns static registration information about this module with optional health check
getServiceRegistration: {
    path: '/io.pipeline.search.model.PipeStepProcessor/GetServiceRegistration',
    requestStream: false,
    responseStream: false,
    requestType: pipe_step_processor_service_pb.RegistrationRequest,
    responseType: pipe_step_processor_service_pb.ServiceRegistrationResponse,
    requestSerialize: serialize_io_pipeline_search_model_RegistrationRequest,
    requestDeserialize: deserialize_io_pipeline_search_model_RegistrationRequest,
    responseSerialize: serialize_io_pipeline_search_model_ServiceRegistrationResponse,
    responseDeserialize: deserialize_io_pipeline_search_model_ServiceRegistrationResponse,
  },
};

exports.PipeStepProcessorClient = grpc.makeGenericClientConstructor(PipeStepProcessorService, 'PipeStepProcessor');
