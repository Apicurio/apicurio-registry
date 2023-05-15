import pytest
import subprocess
import time
import os
import sys
import requests
import json
from kiota_abstractions.authentication.anonymous_authentication_provider import (
    AnonymousAuthenticationProvider,
)
from kiota_http.httpx_request_adapter import HttpxRequestAdapter
from client.registry_client import RegistryClient
from client.models.artifact_content import ArtifactContent

REGISTRY_HOST = "localhost"
REGISTRY_PORT = 8080
REGISTRY_URL = f"http://{REGISTRY_HOST}:{REGISTRY_PORT}/apis/registry/v2"
MAX_POLL_TIME = 120
POLL_INTERVAL = 1
start_time = time.time()


def poll_for_ready():
    while True:
        elapsed_time = time.time() - start_time
        if elapsed_time >= MAX_POLL_TIME:
            print("Polling timed out.")
            break

        print("Attempt to connect")
        try:
            response = requests.get(REGISTRY_URL)
            if response.status_code == 200:
                print("Server is up!")
                break
        except requests.exceptions.ConnectionError:
            pass

        # Wait for the specified poll interval before trying again
        time.sleep(POLL_INTERVAL)


@pytest.fixture(scope="session", autouse=True)
def registry_server(request):
    registry_jar = os.path.join(
        sys.path[0], "..", "..", "app", "target", "apicurio-registry-app-*-runner.jar"
    )
    print(f" Starting Registry from jar {registry_jar}")
    p = subprocess.Popen(f"java -jar {registry_jar}", shell=True)
    request.addfinalizer(p.kill)
    poll_for_ready()


@pytest.mark.asyncio
async def test_basic_upload_download():
    auth_provider = AnonymousAuthenticationProvider()
    request_adapter = HttpxRequestAdapter(auth_provider)
    request_adapter.base_url = REGISTRY_URL
    client = RegistryClient(request_adapter)

    payload = ArtifactContent()
    payload.content = """{
        "openapi": "3.0.0",
        "info": {
            "title": "My API",
            "version": "1.0.0"
        },
        "paths": {}
    }"""
    create_artifact = await client.groups_by_id("default").artifacts.post(payload)
    assert create_artifact.id is not None

    return_artifact = (
        await client.groups_by_id("default").artifacts_by_id(create_artifact.id).get()
    )
    print(str(return_artifact, "utf-8"))
    # TODO: remove the workaround ".replace("'", '"')" when updating the dependency after this PR gets merged: https://github.com/microsoft/kiota-serialization-json-python/pull/77
    assert json.loads(str(return_artifact, "utf-8").replace("'", '"')) == json.loads(
        payload.content
    )
