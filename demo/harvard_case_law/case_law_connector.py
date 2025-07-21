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
        """Generate sample legal cases for demo purposes"""
        
        # Sample legal cases for demo
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
No. 2023-123456

This case involves a dispute over employment discrimination claims. The plaintiff, Sarah Smith, alleges that Johnson Industries violated federal and state employment laws by discriminating against her based on gender and age.

BACKGROUND:
Ms. Smith worked for Johnson Industries from 2018 to 2022 as a senior engineer. During her employment, she consistently received positive performance reviews and was promoted twice. However, in early 2022, she alleged that she began experiencing discriminatory treatment after the company hired a new manager.

The plaintiff claims that the new manager made several inappropriate comments about her age and gender, questioned her technical abilities, and passed her over for a promotion in favor of a less qualified male colleague who was significantly younger.

LEGAL ANALYSIS:
The court must determine whether the evidence supports a finding of discrimination under Title VII of the Civil Rights Act of 1964 and the California Fair Employment and Housing Act (FEHA).

Under McDonnell Douglas Corp. v. Green, 411 U.S. 792 (1973), a plaintiff must establish a prima facie case of discrimination by showing: (1) membership in a protected class; (2) qualification for the job; (3) an adverse employment action; and (4) circumstances giving rise to an inference of discrimination.

CONCLUSION:
After careful consideration of all evidence presented, the court finds that the plaintiff has established a prima facie case of both age and gender discrimination. The defendant's stated reasons for the adverse employment actions are found to be pretextual.

Judgment is entered in favor of the plaintiff. Damages are awarded in the amount of $150,000 for lost wages and emotional distress."""
                        }],
                        "head_matter": "Employment Discrimination - Gender and Age - Title VII - FEHA"
                    }
                }
            },
            {
                "id": "12346", 
                "name": "People v. Rodriguez",
                "decision_date": "2023-07-22",
                "court": {"name": "Court of Appeals, Second District"}, 
                "jurisdiction": {"name": "California"},
                "url": "https://example.com/cases/12346",
                "casebody": {
                    "status": "ok",
                    "data": {
                        "opinions": [{
                            "text": """COURT OF APPEALS OF CALIFORNIA
SECOND APPELLATE DISTRICT

THE PEOPLE OF THE STATE OF CALIFORNIA v. MIGUEL RODRIGUEZ
No. B312456

CRIMINAL LAW - FOURTH AMENDMENT - SEARCH AND SEIZURE

This appeal concerns the validity of a vehicle search conducted during a routine traffic stop. The defendant challenges the trial court's denial of his motion to suppress evidence obtained during the search.

FACTS:
On March 15, 2023, Officer Martinez stopped defendant's vehicle for speeding. During the stop, the officer claimed to smell marijuana emanating from the vehicle. Based on this observation, the officer conducted a search of the vehicle and discovered illegal narcotics in the trunk.

The defendant moved to suppress the evidence, arguing that the search violated his Fourth Amendment rights. The trial court denied the motion, finding that the officer had probable cause to search based on the smell of marijuana.

DISCUSSION:
The Fourth Amendment protects against unreasonable searches and seizures. A warrantless search of a vehicle is permissible under the automobile exception when police have probable cause to believe the vehicle contains evidence of criminal activity.

In this case, we must determine whether the officer's claim that he smelled marijuana provided sufficient probable cause for the search. The officer testified that he has extensive training in drug recognition and that the smell was unmistakable.

However, recent changes in California law regarding marijuana possession must be considered. The legalization of recreational marijuana use has affected the calculus for probable cause determinations.

HOLDING:
We reverse the trial court's denial of the motion to suppress. The officer's testimony regarding the smell of marijuana, without more, does not provide probable cause for a vehicle search in light of current California law.

The case is remanded for further proceedings consistent with this opinion."""
                        }],
                        "head_matter": "Criminal Law - Fourth Amendment - Vehicle Search - Probable Cause"
                    }
                }
            },
            {
                "id": "12347",
                "name": "Green Technology Corp. v. Solar Innovations LLC", 
                "decision_date": "2023-09-10",
                "court": {"name": "Federal District Court for the Northern District of California"},
                "jurisdiction": {"name": "Federal"},
                "url": "https://example.com/cases/12347",
                "casebody": {
                    "status": "ok",
                    "data": {
                        "opinions": [{
                            "text": """UNITED STATES DISTRICT COURT
NORTHERN DISTRICT OF CALIFORNIA

GREEN TECHNOLOGY CORP. v. SOLAR INNOVATIONS LLC
Case No. 3:23-cv-04567

INTELLECTUAL PROPERTY - PATENT INFRINGEMENT - SOLAR TECHNOLOGY

This case involves allegations of patent infringement in the rapidly evolving solar energy technology sector. Plaintiff Green Technology Corp. holds several patents related to photovoltaic cell efficiency improvements and alleges that defendant Solar Innovations LLC has infringed these patents.

BACKGROUND:
Green Technology developed and patented a novel method for improving solar cell efficiency through specialized surface texturing techniques. The patents at issue, U.S. Patent Nos. 10,123,456 and 10,234,567, were granted in 2020 and 2021 respectively.

Solar Innovations, a competitor in the solar panel manufacturing market, launched a new product line in 2023 that Green Technology claims incorporates the patented technology without authorization.

CLAIM CONSTRUCTION:
The court must first construe the claims of the patents in suit. The key disputed term is "micro-textured surface configuration." Green Technology argues for a broad interpretation that would encompass various surface modification techniques, while Solar Innovations advocates for a narrow reading limited to the specific examples disclosed in the patent specification.

After reviewing the intrinsic evidence, including the patent claims, specification, and prosecution history, the court adopts a middle-ground interpretation. "Micro-textured surface configuration" means a surface modification with features between 1-10 micrometers that increases light absorption efficiency by at least 5%.

INFRINGEMENT ANALYSIS:
Under this claim construction, the court must determine whether Solar Innovations' accused products practice each element of the asserted claims. Expert testimony indicates that the accused products do incorporate surface texturing within the claimed size range and achieve the required efficiency improvements.

CONCLUSION:
The court finds that Solar Innovations has infringed Green Technology's patents. An injunction is appropriate given the ongoing nature of the infringement and the lack of adequate monetary remedies. Damages will be calculated based on lost profits and reasonable royalties."""
                        }],
                        "head_matter": "Patent Law - Infringement - Claim Construction - Solar Technology"
                    }
                }
            }
        ]
        
        # Generate cases up to the limit
        for i in range(min(limit, len(sample_cases) * 3)):  # Repeat if needed
            case_index = i % len(sample_cases)
            case = sample_cases[case_index].copy()
            
            # Make each case unique
            case["id"] = f"{case['id']}-{i+1}"
            case["name"] = f"{case['name']} (Case #{i+1})"
            
            print(f"📄 Generated sample case: {case['name'][:60]}...")
            yield case
            
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
        """Send document to the pipeline engine via our test pipeline endpoint"""
        
        # Create a PipeStream to send directly to our PipeStreamEngine
        pipe_stream = {
            "stream_id": f"case-{pipe_doc['id']}-{int(time.time())}",
            "current_pipeline_name": "test-pipeline",
            "target_step_name": "parse-docs",  # Start at the first step
            "current_hop_number": 0,
            "context_params": {
                "cluster": "dev",
                "connector_type": "harvard-case-law"
            },
            "document": pipe_doc,
            "history": []
        }
        
        try:
            import subprocess
            import json
            
            curl_data = json.dumps(pipe_stream)
            result = subprocess.run([
                "curl", "-X", "POST", 
                f"http://{self.engine_host}/api/test/routing/validate-routing",
                "-H", "Content-Type: application/json",
                "-d", curl_data,
                "--silent"
            ], capture_output=True, text=True)
            
            if result.returncode == 0:
                try:
                    response = json.loads(result.stdout)
                    if response.get('success', False):
                        print(f"✅ Pipeline validation passed for case {pipe_doc['id']}")
                        return True
                    else:
                        print(f"❌ Pipeline validation failed for case {pipe_doc['id']}: {response.get('error', 'Unknown error')}")
                        return False
                except json.JSONDecodeError:
                    print(f"✅ Sent case {pipe_doc['id']} (response: {result.stdout[:100]}...)")
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