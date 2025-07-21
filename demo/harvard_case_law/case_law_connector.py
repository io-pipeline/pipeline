#!/usr/bin/env python3
"""
Harvard Case Law Connector

Fetches legal documents from the Harvard Case.law API and sends them
to our pipeline engine for processing.
"""

import requests
import grpc
import time
import json
import uuid
from typing import Iterator, Dict, Any
from datetime import datetime

# Import generated gRPC stubs (we'll need to generate these)
# import engine_service_pb2
# import engine_service_pb2_grpc
# import pipeline_core_types_pb2

class CaseLawConnector:
    def __init__(self, engine_host="localhost:38100", api_key=None):
        self.api_base = "https://api.case.law/v1"
        self.engine_host = engine_host
        self.api_key = api_key
        self.session = requests.Session()
        
        # Set up API headers
        if api_key:
            self.session.headers.update({"Authorization": f"Token {api_key}"})
        
    def fetch_cases(self, limit: int = 10, jurisdiction: str = None) -> Iterator[Dict[str, Any]]:
        """Fetch cases from Harvard Case.law API"""
        
        params = {
            "full_case": "true",  # Get full text
            "page_size": min(limit, 100),  # API max is 100
        }
        
        if jurisdiction:
            params["jurisdiction"] = jurisdiction
            
        url = f"{self.api_base}/cases/"
        
        fetched = 0
        while fetched < limit:
            print(f"Fetching cases from: {url}")
            response = self.session.get(url, params=params)
            
            if response.status_code != 200:
                print(f"API Error: {response.status_code} - {response.text}")
                break
                
            data = response.json()
            
            for case in data.get("results", []):
                if fetched >= limit:
                    break
                    
                yield case
                fetched += 1
                
            # Get next page
            next_url = data.get("next")
            if not next_url or fetched >= limit:
                break
                
            url = next_url
            params = {}  # Next URL already has params
            
    def case_to_pipe_doc(self, case: Dict[str, Any]) -> Dict[str, Any]:
        """Convert a Case.law case to PipeDoc format"""
        
        # Extract case metadata
        case_id = case.get("id", str(uuid.uuid4()))
        name = case.get("name", "Unknown Case")
        decision_date = case.get("decision_date", "")
        court = case.get("court", {}).get("name", "Unknown Court")
        jurisdiction = case.get("jurisdiction", {}).get("name", "Unknown Jurisdiction")
        
        # Get the full case text
        casebody = case.get("casebody", {})
        full_text = ""
        
        if casebody.get("status") == "ok":
            # Try to get text from data
            data = casebody.get("data", {})
            if isinstance(data, dict):
                # Extract text from opinions, headnotes, etc.
                opinions = data.get("opinions", [])
                for opinion in opinions:
                    if isinstance(opinion, dict):
                        text = opinion.get("text", "")
                        if text:
                            full_text += text + "\n\n"
                            
                # Also get headnotes
                head_matter = data.get("head_matter", "")
                if head_matter:
                    full_text = head_matter + "\n\n" + full_text
                    
        # If no structured text, try raw text
        if not full_text.strip():
            full_text = str(casebody.get("data", ""))
            
        # Create PipeDoc-like structure
        pipe_doc = {
            "id": f"case-{case_id}",
            "source_uri": case.get("url", ""),
            "source_mime_type": "application/json",
            "title": name,
            "body": full_text.strip(),
            "keywords": [court, jurisdiction, "legal", "case-law"],
            "document_type": "legal-case",
            "custom_data": {
                "court": court,
                "jurisdiction": jurisdiction,
                "decision_date": decision_date,
                "citations": case.get("citations", []),
                "case_id": case_id
            }
        }
        
        return pipe_doc
        
    def send_to_pipeline(self, pipe_doc: Dict[str, Any]) -> bool:
        """Send document to the pipeline engine via REST (for now)"""
        
        # For demo purposes, let's use the REST endpoint we know works
        # Later we can implement proper gRPC
        
        connector_request = {
            "connector_type": "harvard-case-law",
            "document": pipe_doc,
            "suggested_stream_id": f"case-{pipe_doc['id']}-{int(time.time())}"
        }
        
        try:
            # Using curl for now since we know the REST endpoint works
            import subprocess
            import json
            
            curl_data = json.dumps(connector_request)
            result = subprocess.run([
                "curl", "-X", "POST", 
                f"http://{self.engine_host}/api/connector/process",
                "-H", "Content-Type: application/json",
                "-d", curl_data,
                "--silent"
            ], capture_output=True, text=True)
            
            if result.returncode == 0:
                response = json.loads(result.stdout)
                print(f"✅ Sent case {pipe_doc['id']}: {response.get('message', 'Success')}")
                return True
            else:
                print(f"❌ Failed to send case {pipe_doc['id']}: {result.stderr}")
                return False
                
        except Exception as e:
            print(f"❌ Error sending case {pipe_doc['id']}: {e}")
            return False
            
    def run_demo(self, num_cases: int = 5):
        """Run the demo - fetch cases and send to pipeline"""
        
        print(f"🏛️  Harvard Case Law Connector Demo")
        print(f"📡 Fetching {num_cases} cases from Harvard Case.law API...")
        print(f"🚀 Sending to pipeline engine at {self.engine_host}")
        print("=" * 60)
        
        sent_count = 0
        failed_count = 0
        
        for i, case in enumerate(self.fetch_cases(limit=num_cases), 1):
            print(f"\n📄 Processing case {i}/{num_cases}: {case.get('name', 'Unknown')[:60]}...")
            
            # Convert to PipeDoc
            pipe_doc = self.case_to_pipe_doc(case)
            
            # Send to pipeline
            if self.send_to_pipeline(pipe_doc):
                sent_count += 1
            else:
                failed_count += 1
                
            # Small delay to avoid overwhelming the system
            time.sleep(1)
            
        print("\n" + "=" * 60)
        print(f"✅ Demo completed!")
        print(f"📊 Results: {sent_count} sent, {failed_count} failed")
        
        if sent_count > 0:
            print(f"🔍 Check the pipeline logs to see documents being processed!")
            print(f"🌐 Monitor at: http://localhost:38100/q/dev-ui")

if __name__ == "__main__":
    connector = CaseLawConnector()
    connector.run_demo(num_cases=10)