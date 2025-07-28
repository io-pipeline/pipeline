

import asyncio
import websockets
import json
import sys

async def listen_to_logs(port, prefix):
    """A coroutine that connects to a specific port and streams logs."""
    uri = f"ws://localhost:{port}/q/dev-ui/json-rpc-ws"
    while True:
        try:
            async with websockets.connect(uri) as websocket:
                print(f"[{prefix}] INFO: Connected to WebSocket at {uri}")

                # Request the history
                await websocket.send(json.dumps({
                    "jsonrpc": "2.0",
                    "method": "devui-logstream.history",
                    "id": 1
                }))

                # Subscribe to the live stream
                await websocket.send(json.dumps({
                    "jsonrpc": "2.0",
                    "method": "devui-logstream.streamLog",
                    "id": 2
                }))

                # Process incoming messages
                async for message in websocket:
                    try:
                        data = json.loads(message)
                        if 'result' in data and 'object' in data['result']:
                            log_entries = data['result']['object']
                            if not isinstance(log_entries, list):
                                log_entries = [log_entries]
                            
                            for entry in log_entries:
                                if 'formattedMessage' in entry:
                                    print(f"[{prefix}] {entry.get('level', 'LOG')}: {entry.get('formattedMessage', '')}")
                                else:
                                    # Fallback for non-standard entries
                                    print(f"[{prefix}] RAW: {json.dumps(entry)}")

                    except json.JSONDecodeError:
                        print(f"[{prefix}] FAILED TO PARSE: {message}")

        except (websockets.exceptions.ConnectionClosed, ConnectionRefusedError):
            # Non-critical error, just wait and retry
            await asyncio.sleep(2)
        except Exception as e:
            print(f"[{prefix}] ERROR: An unexpected error occurred: {e}. Retrying in 5 seconds...")
            await asyncio.sleep(5)

async def main():
    """Sets up and runs the concurrent log listeners."""
    if len(sys.argv) < 2:
        print("Usage: python get_all_logs.py <timeout_seconds> <port1> <port2> ...")
        print("Example: python get_all_logs.py 60 38001 39101")
        return

    try:
        timeout = int(sys.argv[1])
        ports = sys.argv[2:]
        if not ports:
            raise ValueError("No ports provided.")
    except (ValueError, IndexError):
        print("Invalid arguments.")
        print("Usage: python get_all_logs.py <timeout_seconds> <port1> <port2> ...")
        print("Example: python get_all_logs.py 60 38001 39101")
        return

    tasks = [listen_to_logs(port, f"port-{port}") for port in ports]
    
    print(f"Starting log listeners for ports: {', '.join(ports)} for {timeout} seconds...")
    try:
        await asyncio.wait_for(asyncio.gather(*tasks), timeout=timeout)
    except asyncio.TimeoutError:
        print(f"\nINFO: Timeout of {timeout} seconds reached. Stopping log listeners.")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nStopping log listeners.")

