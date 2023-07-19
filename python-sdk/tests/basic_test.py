import asyncio
from dataclasses import dataclass
from typing import Optional
from httpx import QueryParams
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
from apicurioregistrysdk.client.groups.item.artifacts.artifacts_request_builder import (
    ArtifactsRequestBuilder,
)
from apicurioregistrysdk.client.models.artifact_meta_data import ArtifactMetaData
from apicurioregistrysdk.client.registry_client import RegistryClient
from apicurioregistrysdk.client.models.artifact_content import ArtifactContent

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


# workaround: https://stackoverflow.com/a/72104554
@pytest.fixture(scope="session", autouse=True)
def event_loop():
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
    yield loop
    loop.close()


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
    create_artifact = await client.groups.by_group_id("default").artifacts.post(payload)
    assert create_artifact.id is not None

    return_artifact = (
        await client.groups.by_group_id("default")
        .artifacts.by_artifact_id(create_artifact.id)
        .get()
    )
    print(str(return_artifact, "utf-8"))
    assert json.loads(return_artifact) == json.loads(payload.content)


@pytest.mark.asyncio
async def test_issue_3465():
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

    query_params = ArtifactsRequestBuilder.ArtifactsRequestBuilderPostQueryParameters(
        canonical=True, if_exists="RETURN_OR_UPDATE"
    )

    request_configuration = (
        ArtifactsRequestBuilder.ArtifactsRequestBuilderPostRequestConfiguration(
            headers={"X-Registry-ArtifactId": "foo"}, query_parameters=query_params
        )
    )

    create_artifact = await client.groups.by_group_id("default").artifacts.post(
        payload, request_configuration=request_configuration
    )
    assert create_artifact.id == "foo"

    # check the return or update functionality
    create_artifact = await client.groups.by_group_id("default").artifacts.post(
        payload, request_configuration=request_configuration
    )
    assert create_artifact.id == "foo"
