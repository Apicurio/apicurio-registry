"""The registry-clients scenario with the Python SDK. Prints one line per step; demo.sh compares
them with the other clients'.

Usage: python order_scenario.py <registry v3 URL> <schemas directory>
"""

import asyncio
import json
import sys
from pathlib import Path

from kiota_abstractions.api_error import APIError
from kiota_abstractions.authentication.anonymous_authentication_provider import (
    AnonymousAuthenticationProvider,
)
from kiota_abstractions.base_request_configuration import RequestConfiguration
from kiota_http.httpx_request_adapter import HttpxRequestAdapter

from apicurioregistrysdk.client.models.create_artifact import CreateArtifact
from apicurioregistrysdk.client.models.create_rule import CreateRule
from apicurioregistrysdk.client.models.create_version import CreateVersion
from apicurioregistrysdk.client.models.rule_type import RuleType
from apicurioregistrysdk.client.models.version_content import VersionContent
from apicurioregistrysdk.client.registry_client import RegistryClient
from apicurioregistrysdk.client.search.artifacts.artifacts_request_builder import (
    ArtifactsRequestBuilder,
)

GROUP = "clients"
ARTIFACT = "python-sdk-order"


def version(schema: Path) -> CreateVersion:
    content = VersionContent()
    content.content = schema.read_text()
    content.content_type = "application/json"
    created = CreateVersion()
    created.content = content
    return created


async def status(request) -> str:
    """The HTTP status of a request that should fail, or "none" if it succeeded."""
    try:
        await request
        return "none"
    except APIError as e:
        return str(e.response_status_code)


async def main(registry_url: str, schemas: Path) -> None:
    adapter = HttpxRequestAdapter(AnonymousAuthenticationProvider())
    adapter.base_url = registry_url
    client = RegistryClient(adapter)

    # 1. Server information
    info = await client.system.info.get()
    print("info ok" if info.version else "info empty")

    # 2. Create the artifact with its first version
    create_artifact = CreateArtifact()
    create_artifact.artifact_id = ARTIFACT
    create_artifact.artifact_type = "AVRO"
    create_artifact.first_version = version(schemas / "order-v1.avsc")
    created = await client.groups.by_group_id(GROUP).artifacts.post(create_artifact)
    print("created", created.version.version)

    # 3. BACKWARD compatibility rule on the artifact
    artifact = client.groups.by_group_id(GROUP).artifacts.by_artifact_id(ARTIFACT)
    rule = CreateRule()
    rule.rule_type = RuleType.COMPATIBILITY
    rule.config = "BACKWARD"
    await artifact.rules.post(rule)
    got = await artifact.rules.by_rule_type(RuleType.COMPATIBILITY.value).get()
    print("rule", got.config)

    # 4. A compatible version: a new field with a default
    v2 = await artifact.versions.post(version(schemas / "order-v2.avsc"))
    print("version", v2.version)

    # 5. An incompatible version: a new field without a default, rejected by the rule
    print("rejected", await status(artifact.versions.post(version(schemas / "order-v3-incompatible.avsc"))))

    # 6. The latest version and its content
    latest = artifact.versions.by_version_expression("branch=latest")
    print("latest", (await latest.get()).version)
    content = json.loads(await latest.content.get())
    print("fields", ",".join(f["name"] for f in content["fields"]))

    # 7. Search
    query = ArtifactsRequestBuilder.ArtifactsRequestBuilderGetQueryParameters(
        group_id=GROUP, artifact_id=ARTIFACT
    )
    found = await client.search.artifacts.get(RequestConfiguration(query_parameters=query))
    print("search", found.count)

    # 8. Delete, then read it again
    await artifact.delete()
    print("deleted")
    print("missing", await status(artifact.get()))


if __name__ == "__main__":
    asyncio.run(main(sys.argv[1], Path(sys.argv[2])))
