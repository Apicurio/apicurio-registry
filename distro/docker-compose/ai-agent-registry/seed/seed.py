"""Loads the demo catalog into a Registry. Safe to run again: existing artifacts are left as they are.

Uses only the Python standard library, so it runs in a plain python image.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

API = os.environ.get("REGISTRY_URL", "http://apicurio-registry:8080").rstrip("/") + "/apis/registry/v3"
GROUP = os.environ.get("SEED_GROUP", "demo")
HERE = os.path.dirname(os.path.abspath(__file__))

# Created in order: the tool exists before the server version that references it.
ARTIFACTS = [
    {
        "artifactId": "weather.get_forecast",
        "artifactType": "MCP_TOOL",
        "name": "Get forecast",
        "file": "weather-get-forecast-tool.json",
        "contentType": "application/json",
        # The rule the quickstart's rejection step runs into.
        "rules": {"COMPATIBILITY": "BACKWARD"},
    },
    {
        "artifactId": "weather",
        "artifactType": "MCP_SERVER",
        "name": "Weather",
        "file": "weather-server.json",
        "contentType": "application/json",
        "references": [{"name": "get_forecast", "artifactId": "weather.get_forecast", "version": "1"}],
    },
    {
        "artifactId": "translator-agent",
        "artifactType": "AGENT_CARD",
        "name": "Translation Agent",
        "file": "translator-agent.json",
        "contentType": "application/json",
    },
    {
        "artifactId": "sentiment-agent",
        "artifactType": "AGENT_CARD",
        "name": "Sentiment Analysis Agent",
        "file": "sentiment-agent.json",
        "contentType": "application/json",
    },
    {
        "artifactId": "support-reply",
        "artifactType": "PROMPT_TEMPLATE",
        "name": "Support reply",
        "file": "support-reply-prompt.yaml",
        "contentType": "application/x-yaml",
    },
]


def request(method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(API + path, data=data, method=method,
                                 headers={"Content-Type": "application/json", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return 404
        raise SystemExit(f"{method} {path} failed with HTTP {e.code}: {e.read().decode(errors='replace')}")


def wait_for_registry(timeout_seconds=180):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        try:
            if request("GET", "/system/info") == 200:
                return
        except (urllib.error.URLError, ConnectionError, OSError):
            pass
        time.sleep(2)
    raise SystemExit(f"Registry at {API} did not become ready in {timeout_seconds} seconds")


def seed(artifact):
    path = f"/groups/{GROUP}/artifacts/{artifact['artifactId']}"
    if request("GET", path) == 200:
        print(f"exists   {GROUP}/{artifact['artifactId']}")
    else:
        create(artifact)
    # Checked separately, so a run interrupted between the two steps still ends with the rule.
    for rule_type, config in artifact.get("rules", {}).items():
        if request("GET", f"{path}/rules/{rule_type}") == 404:
            request("POST", path + "/rules", {"ruleType": rule_type, "config": config})
            print(f"rule     {GROUP}/{artifact['artifactId']} {rule_type}={config}")


def create(artifact):
    with open(os.path.join(HERE, artifact["file"]), encoding="utf-8") as f:
        content = f.read()
    references = [dict(ref, groupId=GROUP) for ref in artifact.get("references", [])]
    request("POST", f"/groups/{GROUP}/artifacts", {
        "artifactId": artifact["artifactId"],
        "artifactType": artifact["artifactType"],
        "name": artifact["name"],
        "firstVersion": {
            "version": "1",
            "content": {"content": content, "contentType": artifact["contentType"], "references": references},
        },
    })
    print(f"created  {GROUP}/{artifact['artifactId']} ({artifact['artifactType']})")


def main():
    wait_for_registry()
    for artifact in ARTIFACTS:
        seed(artifact)
    print(f"Demo catalog ready in group '{GROUP}'.")


if __name__ == "__main__":
    sys.exit(main())
