// GENERATED CODE -- DO NOT EDIT!

'use strict';
var grpc = require('@grpc/grpc-js');
var module_registration_pb = require('./module_registration_pb.js');
var google_protobuf_timestamp_pb = require('google-protobuf/google/protobuf/timestamp_pb.js');
var google_protobuf_empty_pb = require('google-protobuf/google/protobuf/empty_pb.js');

function serialize_google_protobuf_Empty(arg) {
  if (!(arg instanceof google_protobuf_empty_pb.Empty)) {
    throw new Error('Expected argument of type google.protobuf.Empty');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_google_protobuf_Empty(buffer_arg) {
  return google_protobuf_empty_pb.Empty.deserializeBinary(new Uint8Array(buffer_arg));
}

function serialize_io_pipeline_registration_HeartbeatAck(arg) {
  if (!(arg instanceof module_registration_pb.HeartbeatAck)) {
    throw new Error('Expected argument of type io.pipeline.registration.HeartbeatAck');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_registration_HeartbeatAck(buffer_arg) {
  return module_registration_pb.HeartbeatAck.deserializeBinary(new Uint8Array(buffer_arg));
}

function serialize_io_pipeline_registration_ModuleHealthStatus(arg) {
  if (!(arg instanceof module_registration_pb.ModuleHealthStatus)) {
    throw new Error('Expected argument of type io.pipeline.registration.ModuleHealthStatus');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_registration_ModuleHealthStatus(buffer_arg) {
  return module_registration_pb.ModuleHealthStatus.deserializeBinary(new Uint8Array(buffer_arg));
}

function serialize_io_pipeline_registration_ModuleHeartbeat(arg) {
  if (!(arg instanceof module_registration_pb.ModuleHeartbeat)) {
    throw new Error('Expected argument of type io.pipeline.registration.ModuleHeartbeat');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_registration_ModuleHeartbeat(buffer_arg) {
  return module_registration_pb.ModuleHeartbeat.deserializeBinary(new Uint8Array(buffer_arg));
}

function serialize_io_pipeline_registration_ModuleId(arg) {
  if (!(arg instanceof module_registration_pb.ModuleId)) {
    throw new Error('Expected argument of type io.pipeline.registration.ModuleId');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_registration_ModuleId(buffer_arg) {
  return module_registration_pb.ModuleId.deserializeBinary(new Uint8Array(buffer_arg));
}

function serialize_io_pipeline_registration_ModuleInfo(arg) {
  if (!(arg instanceof module_registration_pb.ModuleInfo)) {
    throw new Error('Expected argument of type io.pipeline.registration.ModuleInfo');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_registration_ModuleInfo(buffer_arg) {
  return module_registration_pb.ModuleInfo.deserializeBinary(new Uint8Array(buffer_arg));
}

function serialize_io_pipeline_registration_ModuleList(arg) {
  if (!(arg instanceof module_registration_pb.ModuleList)) {
    throw new Error('Expected argument of type io.pipeline.registration.ModuleList');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_registration_ModuleList(buffer_arg) {
  return module_registration_pb.ModuleList.deserializeBinary(new Uint8Array(buffer_arg));
}

function serialize_io_pipeline_registration_RegistrationStatus(arg) {
  if (!(arg instanceof module_registration_pb.RegistrationStatus)) {
    throw new Error('Expected argument of type io.pipeline.registration.RegistrationStatus');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_registration_RegistrationStatus(buffer_arg) {
  return module_registration_pb.RegistrationStatus.deserializeBinary(new Uint8Array(buffer_arg));
}

function serialize_io_pipeline_registration_UnregistrationStatus(arg) {
  if (!(arg instanceof module_registration_pb.UnregistrationStatus)) {
    throw new Error('Expected argument of type io.pipeline.registration.UnregistrationStatus');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_io_pipeline_registration_UnregistrationStatus(buffer_arg) {
  return module_registration_pb.UnregistrationStatus.deserializeBinary(new Uint8Array(buffer_arg));
}


// Service for module registration with the engine
var ModuleRegistrationService = exports.ModuleRegistrationService = {
  // Register a module with the engine
registerModule: {
    path: '/io.pipeline.registration.ModuleRegistration/RegisterModule',
    requestStream: false,
    responseStream: false,
    requestType: module_registration_pb.ModuleInfo,
    responseType: module_registration_pb.RegistrationStatus,
    requestSerialize: serialize_io_pipeline_registration_ModuleInfo,
    requestDeserialize: deserialize_io_pipeline_registration_ModuleInfo,
    responseSerialize: serialize_io_pipeline_registration_RegistrationStatus,
    responseDeserialize: deserialize_io_pipeline_registration_RegistrationStatus,
  },
  // Unregister a module
unregisterModule: {
    path: '/io.pipeline.registration.ModuleRegistration/UnregisterModule',
    requestStream: false,
    responseStream: false,
    requestType: module_registration_pb.ModuleId,
    responseType: module_registration_pb.UnregistrationStatus,
    requestSerialize: serialize_io_pipeline_registration_ModuleId,
    requestDeserialize: deserialize_io_pipeline_registration_ModuleId,
    responseSerialize: serialize_io_pipeline_registration_UnregistrationStatus,
    responseDeserialize: deserialize_io_pipeline_registration_UnregistrationStatus,
  },
  // Heartbeat from module to engine
heartbeat: {
    path: '/io.pipeline.registration.ModuleRegistration/Heartbeat',
    requestStream: false,
    responseStream: false,
    requestType: module_registration_pb.ModuleHeartbeat,
    responseType: module_registration_pb.HeartbeatAck,
    requestSerialize: serialize_io_pipeline_registration_ModuleHeartbeat,
    requestDeserialize: deserialize_io_pipeline_registration_ModuleHeartbeat,
    responseSerialize: serialize_io_pipeline_registration_HeartbeatAck,
    responseDeserialize: deserialize_io_pipeline_registration_HeartbeatAck,
  },
  // Get health status of a specific module
getModuleHealth: {
    path: '/io.pipeline.registration.ModuleRegistration/GetModuleHealth',
    requestStream: false,
    responseStream: false,
    requestType: module_registration_pb.ModuleId,
    responseType: module_registration_pb.ModuleHealthStatus,
    requestSerialize: serialize_io_pipeline_registration_ModuleId,
    requestDeserialize: deserialize_io_pipeline_registration_ModuleId,
    responseSerialize: serialize_io_pipeline_registration_ModuleHealthStatus,
    responseDeserialize: deserialize_io_pipeline_registration_ModuleHealthStatus,
  },
  // List all registered modules
listModules: {
    path: '/io.pipeline.registration.ModuleRegistration/ListModules',
    requestStream: false,
    responseStream: false,
    requestType: google_protobuf_empty_pb.Empty,
    responseType: module_registration_pb.ModuleList,
    requestSerialize: serialize_google_protobuf_Empty,
    requestDeserialize: deserialize_google_protobuf_Empty,
    responseSerialize: serialize_io_pipeline_registration_ModuleList,
    responseDeserialize: deserialize_io_pipeline_registration_ModuleList,
  },
};

exports.ModuleRegistrationClient = grpc.makeGenericClientConstructor(ModuleRegistrationService, 'ModuleRegistration');
