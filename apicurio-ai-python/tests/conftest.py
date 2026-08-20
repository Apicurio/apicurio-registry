import asyncio
import json
import time

import pytest
import requests

from apicurio_ai.core.config import RegistryConfig

REGISTRY_HOST = "localhost"
REGISTRY_PORT = 8080
REGISTRY_URL = f"http://{REGISTRY_HOST}:{REGISTRY_PORT}"
API_URL = f"{REGISTRY_URL}/apis/registry/v3"
MAX_POLL_TIME = 120
POLL_INTERVAL = 1


def poll_for_ready():
    start_time = time.time()
    while True:
        elapsed_time = time.time() - start_time
        if elapsed_time >= MAX_POLL_TIME:
            pytest.fail("Registry did not become ready within timeout")

        try:
            response = requests.get(API_URL)
            if response.status_code == 200:
                return
        except requests.exceptions.ConnectionError:
            pass

        time.sleep(POLL_INTERVAL)


@pytest.fixture(scope="session", autouse=True)
def registry_server():
    poll_for_ready()


@pytest.fixture(scope="session")
def event_loop():
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest.fixture(scope="session")
def registry_config() -> RegistryConfig:
    return RegistryConfig(registry_url=REGISTRY_URL)


def register_artifact(
    group_id: str,
    artifact_id: str,
    artifact_type: str,
    content: str,
    content_type: str = "application/json",
    name: str = None,
) -> None:
    url = f"{API_URL}/groups/{group_id}/artifacts"
    body: dict = {
        "artifactId": artifact_id,
        "artifactType": artifact_type,
        "firstVersion": {
            "content": {
                "content": content,
                "contentType": content_type,
            }
        },
    }
    if name:
        body["name"] = name
    resp = requests.post(url, json=body)
    resp.raise_for_status()
