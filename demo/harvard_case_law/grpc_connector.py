#!/usr/bin/env python3
"""
Harvard Case Law gRPC Connector

Proper gRPC client that sends legal documents to our PipeStreamEngine
"""

import grpc
import time
import uuid
import requests
import json
from typing import Iterator, Dict, Any, Optional

# Import the generated gRPC stubs
import connector_service_simple_pb2 as connector_service_pb2
import connector_service_simple_pb2_grpc as connector_service_pb2_grpc
import pipeline_core_types_pb2
from google.protobuf.timestamp_pb2 import Timestamp

class CaseLawGrpcConnector:
    def __init__(self, consul_host="localhost:8500", engine_service="pipestream-engine"):
        self.consul_host = consul_host
        self.engine_service = engine_service
        self.engine_host = "localhost:38100"  # PipeStreamEngine runs on fixed port
        
    def discover_engine_service(self) -> Optional[str]:
        """Discover Registration Service via Consul"""
        try:
            print(f"🔍 Discovering {self.engine_service} service from Consul at {self.consul_host}")
            
            # Query Consul for healthy service instances
            url = f"http://{self.consul_host}/v1/health/service/{self.engine_service}"
            response = requests.get(url, timeout=5)
            
            if response.status_code != 200:
                print(f"❌ Consul query failed: {response.status_code}")
                return None
                
            services = response.json()
            
            if not services:
                print(f"❌ No healthy instances found for service: {self.engine_service}")
                return None
                
            # Get the first healthy instance
            service = services[0]
            address = service['Service']['Address']
            port = service['Service']['Port']
            
            # Handle cases where address might be hostname vs IP
            if address in ['krick', 'localhost'] or address.startswith('127.'):
                address = 'localhost'
                
            engine_host = f"{address}:{port}"
            print(f"✅ Discovered {self.engine_service} at: {engine_host}")
            
            return engine_host
            
        except Exception as e:
            print(f"❌ Service discovery failed: {e}")
            return None
        
    def create_pipe_doc(self, case_data: Dict[str, Any]):
        """Convert case data to PipeDoc protobuf"""
        
        # Extract case info
        case_id = case_data.get("id", str(uuid.uuid4()))
        name = case_data.get("name", "Unknown Case")
        decision_date = case_data.get("decision_date", "")
        court = case_data.get("court", {}).get("name", "Unknown Court")
        jurisdiction = case_data.get("jurisdiction", {}).get("name", "Unknown Jurisdiction")
        
        # Get full text from casebody
        casebody = case_data.get("casebody", {})
        full_text = ""
        
        if casebody.get("status") == "ok":
            data = casebody.get("data", {})
            if isinstance(data, dict):
                opinions = data.get("opinions", [])
                for opinion in opinions:
                    if isinstance(opinion, dict):
                        text = opinion.get("text", "")
                        if text:
                            full_text += text + "\n\n"
                            
                head_matter = data.get("head_matter", "")
                if head_matter:
                    full_text = head_matter + "\n\n" + full_text
                    
        # Create PipeDoc protobuf
        pipe_doc = pipeline_core_types_pb2.PipeDoc(
            id=f"case-{case_id}",
            source_uri=case_data.get("url", ""),
            source_mime_type="application/json",
            title=name,
            body=full_text.strip(),
            keywords=[court, jurisdiction, "legal", "case-law"],
            document_type="legal-case"
        )
        
        return pipe_doc
        
    def send_to_pipeline(self, pipe_doc) -> bool:
        """Send document via gRPC ConnectorEngine"""
        
        # PipeStreamEngine runs on fixed port, no discovery needed
        print(f"🔗 Using PipeStreamEngine at: {self.engine_host}")
        
        try:
            # Create gRPC channel with timeout options
            options = [
                ('grpc.keepalive_time_ms', 30000),
                ('grpc.keepalive_timeout_ms', 10000),
                ('grpc.keepalive_permit_without_calls', True),
                ('grpc.http2.max_pings_without_data', 0),
                ('grpc.http2.min_time_between_pings_ms', 10000),
                ('grpc.http2.min_ping_interval_without_data_ms', 300000)
            ]
            
            with grpc.insecure_channel(self.engine_host, options=options) as channel:
                stub = connector_service_pb2_grpc.ConnectorEngineStub(channel)
                
                # Create ConnectorRequest
                # connector_id references registered connector in Consul
                connector_request = connector_service_pb2.ConnectorRequest(
                    connector_type="harvard-case-law",
                    connector_id="harvard-demo-connector-001",
                    document=pipe_doc,
                    tags=["legal", "case-law", "harvard", "demo"],
                    context_params={
                        "source": "harvard_case_law_demo",
                        "demo_run": "true",
                        "cluster": "dev",
                        "pipeline": "test-pipeline",
                        "target_step": "parse-docs"
                    }
                )
                
                # Make gRPC call with timeout
                print(f"🚀 Sending ConnectorRequest to {self.engine_host}...")
                response = stub.processConnectorDoc(connector_request, timeout=30.0)
                
                print(f"✅ ConnectorEngine processed case {pipe_doc.id}")
                print(f"   Stream ID: {response.stream_id}")
                print(f"   Accepted: {response.accepted}")
                print(f"   Message: {response.message}")
                
                return response.accepted
                
        except grpc.RpcError as e:
            print(f"❌ gRPC error for case {pipe_doc.id}: {e.code()} - {e.details()}")
            return False
        except Exception as e:
            print(f"❌ Error sending case {pipe_doc.id}: {e}")
            return False
            
    def generate_sample_cases(self, num_cases: int = 5) -> Iterator[Dict[str, Any]]:
        """Generate sample legal cases"""
        
        sample_cases = [
            {
                "id": "12345",
                "name": "Smith v. Johnson Industries",
                "decision_date": "2023-05-15",
                "court": {"name": "Supreme Court of California"},
                "jurisdiction": {"name": "California"},
                "url": "https://example.com/cases/12345",
                "casebody": {
                    "status": "ok",
                    "data": {
                        "opinions": [{
                            "text": """SUPREME COURT OF CALIFORNIA
                            
SMITH v. JOHNSON INDUSTRIES
Employment Discrimination Case

This case involves allegations of gender and age discrimination in violation of Title VII and the California Fair Employment and Housing Act (FEHA).

FACTS:
The plaintiff worked as a senior engineer from 2018-2022 and received consistently positive reviews. After a management change, she experienced discriminatory treatment including inappropriate comments about her age and gender, and was passed over for promotion in favor of a less qualified younger male colleague.

HOLDING:
The court finds the plaintiff established a prima facie case of discrimination. The defendant's stated reasons for adverse employment actions are pretextual. Judgment for plaintiff with $150,000 in damages."""
                        }],
                        "head_matter": "Employment Law - Discrimination - Title VII - FEHA"
                    }
                }
            },
            {
                "id": "12346", 
                "name": "People v. Rodriguez",
                "decision_date": "2023-07-22",
                "court": {"name": "Court of Appeals, Second District"}, 
                "jurisdiction": {"name": "California"},
                "casebody": {
                    "status": "ok",
                    "data": {
                        "opinions": [{
                            "text": """COURT OF APPEALS OF CALIFORNIA

THE PEOPLE v. RODRIGUEZ
Criminal Law - Fourth Amendment

This appeal concerns a vehicle search during a traffic stop. Officer claimed to smell marijuana and searched the vehicle, finding illegal narcotics.

ISSUE:
Whether the smell of marijuana provides probable cause for vehicle search under current California law.

HOLDING:
Given marijuana legalization, the smell alone does not provide probable cause. Motion to suppress granted. Case remanded."""
                        }],
                        "head_matter": "Criminal Law - Fourth Amendment - Search and Seizure"
                    }
                }
            }
        ]
        
        for i in range(num_cases):
            case_index = i % len(sample_cases)
            case = sample_cases[case_index].copy()
            case["id"] = f"{case['id']}-{i+1}"
            case["name"] = f"{case['name']} (Case #{i+1})"
            yield case
            
    def run_demo(self, num_cases: int = 5):
        """Run the gRPC demo"""
        
        print(f"🏛️  Harvard Case Law gRPC Connector Demo")
        print(f"🚀 Sending {num_cases} cases via gRPC to {self.engine_host}")
        print("=" * 60)
        
        sent_count = 0
        failed_count = 0
        
        for i, case_data in enumerate(self.generate_sample_cases(num_cases), 1):
            print(f"\n📄 Processing case {i}/{num_cases}: {case_data['name'][:50]}...")
            
            # Convert to PipeDoc
            pipe_doc = self.create_pipe_doc(case_data)
            
            # Send via gRPC
            if self.send_to_pipeline(pipe_doc):
                sent_count += 1
            else:
                failed_count += 1
                
            # Small delay between requests
            time.sleep(1)
            
        print("\n" + "=" * 60)
        print(f"✅ gRPC Demo completed!")
        print(f"📊 Results: {sent_count} sent, {failed_count} failed")

if __name__ == "__main__":
    connector = CaseLawGrpcConnector()
    connector.run_demo(num_cases=3)