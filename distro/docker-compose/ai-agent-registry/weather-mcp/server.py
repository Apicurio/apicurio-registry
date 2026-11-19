"""A demo MCP server for the AI Agent Registry quickstart: fictional weather tools over the
Streamable HTTP transport, Python standard library only. It lists its tools and nothing else; the
quickstart only imports them into the Registry.

  /mcp     the server as deployed: get_forecast (as in the seeded catalog) and get_alerts
  /mcp-v2  the same server after a breaking change: get_forecast now requires "units"

LOCAL USE ONLY: no authentication.
"""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import uuid

GET_FORECAST = {
    "name": "get_forecast",
    "title": "Get forecast",
    "description": "Daily weather forecast for a city, using fictional data.",
    "inputSchema": {
        "type": "object",
        "properties": {
            "city": {"type": "string", "description": "City name, for example Madrid"},
            "days": {"type": "integer", "minimum": 1, "maximum": 7, "description": "Number of days"},
        },
        "required": ["city"],
    },
}

GET_ALERTS = {
    "name": "get_alerts",
    "title": "Get weather alerts",
    "description": "Active weather alerts for a city, using fictional data.",
    "inputSchema": {
        "type": "object",
        "properties": {"city": {"type": "string", "description": "City name, for example Madrid"}},
        "required": ["city"],
    },
}

# Every existing caller of get_forecast sends only "city": requiring "units" breaks them.
GET_FORECAST_V2 = json.loads(json.dumps(GET_FORECAST))
GET_FORECAST_V2["inputSchema"]["properties"]["units"] = {"type": "string", "enum": ["metric", "imperial"]}
GET_FORECAST_V2["inputSchema"]["required"] = ["city", "units"]

TOOLS = {"/mcp": [GET_FORECAST, GET_ALERTS], "/mcp-v2": [GET_FORECAST_V2, GET_ALERTS]}


class Handler(BaseHTTPRequestHandler):

    def do_POST(self):
        tools = TOOLS.get(self.path)
        if tools is None:
            self.send_error(404)
            return
        message = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
        if "id" not in message:
            # A notification, such as notifications/initialized
            self.send_response(202)
            self.end_headers()
            return
        method = message.get("method")
        headers = {}
        if method == "initialize":
            result = {
                "protocolVersion": message.get("params", {}).get("protocolVersion", "2025-06-18"),
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "demo-weather", "version": "2.0.0" if self.path == "/mcp-v2" else "1.0.0"},
            }
            headers["Mcp-Session-Id"] = str(uuid.uuid4())
        elif method == "tools/list":
            result = {"tools": tools}
        elif method == "ping":
            result = {}
        else:
            self.reply({"jsonrpc": "2.0", "id": message["id"],
                        "error": {"code": -32601, "message": f"Method not found: {method}"}})
            return
        self.reply({"jsonrpc": "2.0", "id": message["id"], "result": result}, headers)

    def do_GET(self):
        # No server-initiated messages: no event stream to open
        self.send_error(405)

    def do_DELETE(self):
        self.send_response(200)
        self.end_headers()

    def reply(self, body, headers=None):
        data = json.dumps(body).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        for name, value in (headers or {}).items():
            self.send_header(name, value)
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, format, *args):
        print(format % args, flush=True)


if __name__ == "__main__":
    print("Demo weather MCP server on port 8000: /mcp and /mcp-v2", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
