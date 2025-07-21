# Harvard Case Law Connector Demo

This demo fetches legal documents from Harvard's Case.law API and sends them through our pipeline engine for processing.

## Quick Start

1. **Install dependencies:**
   ```bash
   cd demo/harvard_case_law
   pip install -r requirements.txt
   ```

2. **Make sure the pipeline engine is running:**
   ```bash
   # From project root
   ./gradlew :applications:pipestream-engine:quarkusDev
   ```

3. **Run the connector:**
   ```bash
   python case_law_connector.py
   ```

## What it does

1. **Fetches** legal cases from Harvard Case.law API
2. **Converts** them to PipeDoc format with metadata
3. **Sends** them to our ConnectorEngine via REST
4. **Watches** as they flow through: parser → chunker → embedder

## Sample Output

```
🏛️  Harvard Case Law Connector Demo
📡 Fetching 10 cases from Harvard Case.law API...
🚀 Sending to pipeline engine at localhost:38100
============================================================

📄 Processing case 1/10: SMITH v. JONES...
✅ Sent case case-12345: Document accepted for processing
```

## API Details

- **Source**: Harvard Law School's Case.law project
- **Data**: 6.7+ million US court cases
- **Format**: Full text with rich metadata
- **Rate limits**: No API key required for basic usage

## Configuration

Edit the `CaseLawConnector` constructor to:
- Change the engine host/port
- Add Harvard API key for higher rate limits
- Filter by jurisdiction or date range

## Next Steps

This demo uses REST for simplicity. For production, we'd implement proper gRPC:
1. Generate Python stubs from our .proto files
2. Use gRPC client instead of curl
3. Add proper error handling and retries
4. Implement streaming for bulk processing