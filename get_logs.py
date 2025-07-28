
import asyncio
import websockets
import json

import time

async def get_logs():
    uri = "ws://localhost:38001/q/dev-ui/json-rpc-ws"
    async with websockets.connect(uri) as websocket:
        await websocket.send(json.dumps({
            "jsonrpc": "2.0",
            "method": "devui-logstream.history",
            "id": 1
        }))

        response = await websocket.recv()
        print(response)
        time.sleep(5)

asyncio.run(get_logs())
