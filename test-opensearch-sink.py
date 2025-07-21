#!/usr/bin/env python3
"""
Test script for OpenSearch sink module
Creates sample embedded documents and sends them to the sink
"""

import grpc
import sys
import time
from google.protobuf.timestamp_pb2 import Timestamp

# Add the demo directory gRPC stubs to the path (they exist there)
sys.path.append('demo/harvard_case_law')

from io.pipeline.data.module_pb2 import (
    ModuleProcessRequest, 
    ServiceMetadata, 
    ProcessConfiguration
)
from io.pipeline.data.module_pb2_grpc import PipeStepProcessorStub
from io.pipeline.data.model_pb2 import (
    PipeDoc, 
    SemanticProcessingResult, 
    SemanticChunk, 
    ChunkEmbedding,
    Embedding
)

def create_test_document_with_embeddings():
    """Create a test document with embeddings for OpenSearch indexing"""
    
    # Create embedding info for a chunk
    chunk_embedding = ChunkEmbedding.newBuilder()
    chunk_embedding.setTextContent("This is a sample legal document chunk about contract law.")
    chunk_embedding.setModelId("ALL_MINILM_L6_V2")
    
    # Add sample 384-dimensional vector (normally from embedder module)
    sample_vector = [0.1] * 384  # Simple test vector
    for val in sample_vector:
        chunk_embedding.addVector(val)
    
    # Create semantic chunk
    chunk = SemanticChunk.newBuilder()
    chunk.setChunkId("chunk_001")
    chunk.setChunkNumber(0)
    chunk.setEmbeddingInfo(chunk_embedding.build())
    
    # Create semantic processing result
    semantic_result = SemanticProcessingResult.newBuilder()
    semantic_result.setResultId("embedding_result_001")
    semantic_result.setResultSetName("contract_chunks")
    semantic_result.setEmbeddingConfigId("ALL_MINILM_L6_V2")
    semantic_result.addChunks(chunk.build())
    
    # Create main document
    doc = PipeDoc.newBuilder()
    doc.setId("test_doc_001")
    doc.setTitle("Sample Contract Document")
    doc.setBody("This is a sample legal document for testing OpenSearch indexing.")
    doc.setSourceUri("file:///test/sample_contract.pdf")
    doc.setDocumentType("contract")
    
    # Add creation timestamp
    timestamp = Timestamp()
    timestamp.GetCurrentTime()
    doc.setCreationDate(timestamp)
    
    # Add metadata
    doc.putMetadata("source", "test_harness")
    doc.putMetadata("category", "legal")
    doc.putMetadata("action_type", "CREATE")
    
    # Add semantic results
    doc.addSemanticResults(semantic_result.build())
    
    return doc.build()

def test_opensearch_sink():
    """Test the OpenSearch sink module"""
    
    print("🧪 Testing OpenSearch Sink Module")
    print("=" * 50)
    
    # Connect to the opensearch-sink module
    channel = grpc.insecure_channel('localhost:39104')
    stub = PipeStepProcessorStub(channel)
    
    try:
        # Create test document with embeddings
        print("📄 Creating test document with embeddings...")
        test_doc = create_test_document_with_embeddings()
        print(f"✅ Created document: {test_doc.getId()}")
        print(f"   - Title: {test_doc.getTitle()}")
        print(f"   - Semantic results: {test_doc.getSemanticResultsCount()}")
        print(f"   - Chunks: {test_doc.getSemanticResults(0).getChunksCount()}")
        
        # Create service metadata
        metadata = ServiceMetadata.newBuilder()
        metadata.setStreamId("test_stream_001")
        metadata.setPipeStepName("opensearch-sink")
        metadata.setPipelineName("test_pipeline")
        metadata.putContextParams("action_type", "CREATE")
        
        # Create process configuration (empty for this test)
        config = ProcessConfiguration.newBuilder()
        
        # Create the request
        request = ModuleProcessRequest.newBuilder()
        request.setDocument(test_doc)
        request.setMetadata(metadata.build())
        request.setConfig(config.build())
        
        print("\n🚀 Sending document to OpenSearch sink...")
        
        # Send the request
        response = stub.processData(request.build())
        
        print(f"✅ Response received!")
        print(f"   - Success: {response.getSuccess()}")
        print(f"   - Logs: {len(response.getProcessorLogsList())} entries")
        
        for log in response.getProcessorLogsList():
            print(f"   📝 {log}")
            
        if response.hasErrorDetails():
            print(f"   ❌ Error details: {response.getErrorDetails()}")
            
        return response.getSuccess()
        
    except grpc.RpcError as e:
        print(f"❌ gRPC Error: {e}")
        return False
    except Exception as e:
        print(f"❌ Error: {e}")
        return False
    finally:
        channel.close()

def test_health_check():
    """Test the health check endpoint"""
    print("\n🏥 Testing health check...")
    
    channel = grpc.insecure_channel('localhost:39104')
    stub = PipeStepProcessorStub(channel)
    
    try:
        from io.pipeline.data.module_pb2 import RegistrationRequest
        
        request = RegistrationRequest.newBuilder().build()
        response = stub.getServiceRegistration(request)
        
        print(f"✅ Health check response:")
        print(f"   - Module: {response.getModuleName()}")
        print(f"   - Health: {response.getHealthCheckPassed()}")
        print(f"   - Message: {response.getHealthCheckMessage()}")
        
        return response.getHealthCheckPassed()
        
    except Exception as e:
        print(f"❌ Health check error: {e}")
        return False
    finally:
        channel.close()

if __name__ == "__main__":
    print("OpenSearch Sink Module Test")
    print("===========================\n")
    
    # Test health first
    health_ok = test_health_check()
    
    if health_ok:
        # Test processing
        process_ok = test_opensearch_sink()
        
        if process_ok:
            print("\n🎉 All tests passed!")
        else:
            print("\n❌ Processing test failed")
    else:
        print("\n❌ Health check failed")