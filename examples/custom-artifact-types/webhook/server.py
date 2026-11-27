"""Webhook providers for the CSV artifact type (see artifact-types.json).

A CSV schema is the header line of a CSV file: comma-separated column names. The registry POSTs a
JSON request to one path per provider and reads the JSON response; the formats are defined in
common/src/main/resources/META-INF/artifact-type-webhooks.json. Python standard library only.

GET /requests returns the requests received so far (path, and the Authorization header), so that
demo.sh can check what the registry sent.
"""
import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

COLUMN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
received = []


def columns(content):
    header = content.strip().splitlines()[0] if content.strip() else ""
    return [c.strip() for c in header.split(",")] if header else []


def accept(request):
    # The response is a ContentAccepterResponse, as defined by the webhook contract
    content = request["typedContent"]
    return {"accepted": content.get("contentType") == "text/csv"}


def validate(request):
    if request.get("function") == "validateReferences":
        return {"ruleViolations": []}
    cols = columns(request["content"]["content"])
    violations = []
    if not cols:
        violations.append({"description": "A CSV schema needs a header line", "context": "/"})
    for col in cols:
        if not COLUMN.match(col):
            violations.append({"description": f"Invalid column name '{col}'", "context": f"/{col}"})
    duplicates = sorted({c for c in cols if cols.count(c) > 1})
    for col in duplicates:
        violations.append({"description": f"Duplicate column '{col}'", "context": f"/{col}"})
    return {"ruleViolations": violations}


def compatibility(request):
    # BACKWARD: no column removed; FORWARD: no column added; FULL: both. The registry sends every
    # existing version, oldest first: *_TRANSITIVE compares with all of them, the others with the
    # latest only (as the built-in checkers do).
    level = request.get("level", "NONE")
    if level == "NONE":
        return {"incompatibleDifferences": []}
    existing_versions = request.get("existingArtifacts") or []
    if not level.endswith("_TRANSITIVE"):
        existing_versions = existing_versions[-1:]
    proposed = columns(request["proposedArtifact"]["content"])
    differences = []
    for existing in existing_versions:
        old = columns(existing["content"])
        if not level.startswith("FORWARD"):
            differences += [{"description": f"Column '{c}' was removed", "context": f"/{c}"}
                            for c in old if c not in proposed]
        if not level.startswith("BACKWARD"):
            differences += [{"description": f"Column '{c}' was added", "context": f"/{c}"}
                            for c in proposed if c not in old]
    unique = {d["description"]: d for d in differences}
    return {"incompatibleDifferences": list(unique.values())}


def canonicalize(request):
    content = request["content"]
    canonical = ",".join(c.lower() for c in columns(content["content"])) + "\n"
    return {"typedContent": {"content": canonical, "contentType": content["contentType"]}}


ROUTES = {"/accept": accept, "/validate": validate, "/compatibility": compatibility,
          "/canonicalize": canonicalize}


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        route = ROUTES.get(self.path)
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        received.append({"path": self.path, "authorization": self.headers.get("Authorization")})
        if route is None:
            return self.reply(404, {"error": "unknown path"})
        self.reply(200, route(body))

    def do_GET(self):
        if self.path == "/requests":
            return self.reply(200, received)
        self.reply(200 if self.path == "/health" else 404, {})

    def reply(self, status, payload):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("", 8080), Handler).serve_forever()
