#!/usr/bin/env python3
"""
Prompt Versioning Example for Apicurio Registry

This example demonstrates how to manage prompt template versions
and implement A/B testing patterns using Apicurio Registry.

Prerequisites:
    1. Start Apicurio Registry: docker compose up -d (from parent directory)
    2. Install dependencies: pip install -r requirements.txt

Usage:
    python prompt_versioning.py
"""

import asyncio
import json
import os
import random
from dataclasses import dataclass
from typing import Optional

from apicurioregistrysdk.llm import PromptRegistry

# Registry configuration
REGISTRY_URL = os.getenv("REGISTRY_URL", "http://localhost:8080")
GROUP_ID = "prompt-versioning-demo"


@dataclass
class PromptVersion:
    """Represents a prompt version with metadata."""
    version: str
    template: str
    weight: float = 1.0  # For A/B testing


class VersionedPromptManager:
    """
    Manages versioned prompts with support for:
    - Fetching specific versions
    - A/B testing between versions
    - Rollback to previous versions
    - Version comparison
    """

    def __init__(self, registry_url: str, group_id: str):
        self.registry = PromptRegistry(registry_url, group_id=group_id)
        self._version_cache: dict[str, list[PromptVersion]] = {}

    async def get_prompt(
        self,
        artifact_id: str,
        version: Optional[str] = None
    ):
        """
        Get a specific prompt version.

        Args:
            artifact_id: The prompt template artifact ID
            version: Specific version (None for latest)
        """
        return await self.registry.get_prompt_async(artifact_id, version=version)

    async def get_prompt_for_ab_test(
        self,
        artifact_id: str,
        versions: list[tuple[str, float]]
    ):
        """
        Get a prompt version based on weighted random selection (A/B testing).

        Args:
            artifact_id: The prompt template artifact ID
            versions: List of (version, weight) tuples

        Example:
            # 80% traffic to v2, 20% to v1
            prompt = await manager.get_prompt_for_ab_test(
                "summarization",
                [("2.0", 0.8), ("1.0", 0.2)]
            )
        """
        total_weight = sum(weight for _, weight in versions)
        rand = random.uniform(0, total_weight)

        cumulative = 0
        selected_version = versions[0][0]
        for version, weight in versions:
            cumulative += weight
            if rand <= cumulative:
                selected_version = version
                break

        prompt = await self.get_prompt(artifact_id, version=selected_version)
        return prompt, selected_version

    async def compare_versions(
        self,
        artifact_id: str,
        version1: str,
        version2: str
    ) -> dict:
        """
        Compare two versions of a prompt template.

        Returns differences in:
        - Template text
        - Variables
        - Metadata
        """
        prompt1 = await self.get_prompt(artifact_id, version=version1)
        prompt2 = await self.get_prompt(artifact_id, version=version2)

        comparison = {
            "artifact_id": artifact_id,
            "versions": [version1, version2],
            "template_changed": prompt1.template != prompt2.template,
            "variables_v1": set(prompt1.variables.keys()),
            "variables_v2": set(prompt2.variables.keys()),
            "variables_added": set(prompt2.variables.keys()) - set(prompt1.variables.keys()),
            "variables_removed": set(prompt1.variables.keys()) - set(prompt2.variables.keys()),
        }

        return comparison

    async def render_with_tracking(
        self,
        artifact_id: str,
        variables: dict,
        version: Optional[str] = None
    ) -> tuple[str, dict]:
        """
        Render a prompt and return tracking metadata.

        Returns:
            Tuple of (rendered_prompt, tracking_info)
        """
        prompt = await self.get_prompt(artifact_id, version=version)
        rendered = prompt.render(**variables)

        tracking_info = {
            "artifact_id": artifact_id,
            "version": prompt.version,
            "template_id": prompt.template_id,
            "variables_used": list(variables.keys()),
        }

        return rendered, tracking_info


async def demo_version_management():
    """Demonstrate prompt version management."""
    print("\n=== Prompt Version Management Demo ===\n")

    manager = VersionedPromptManager(REGISTRY_URL, GROUP_ID)

    try:
        # Get latest version
        print("Fetching latest version...")
        latest = await manager.get_prompt("summarization-v1")
        print(f"Latest version: {latest.version}")
        print(f"Template preview: {latest.template[:100]}...")

        # Get specific version (if exists)
        print("\nFetching specific version 1.0...")
        v1 = await manager.get_prompt("summarization-v1", version="1.0")
        print(f"Version 1.0 template: {v1.template[:100]}...")

    except Exception as e:
        print(f"Error: {e}")
        print("Make sure the registry has sample data (run ../demo.sh)")


async def demo_ab_testing():
    """Demonstrate A/B testing with prompt versions."""
    print("\n=== A/B Testing Demo ===\n")

    manager = VersionedPromptManager(REGISTRY_URL, GROUP_ID)

    # Simulate A/B test: 70% v2, 30% v1
    versions = [("1.0", 0.3), ("1.0", 0.7)]  # Using same version for demo

    selection_counts = {"1.0": 0}

    print("Simulating 100 requests with A/B testing...")
    for _ in range(100):
        try:
            prompt, selected_version = await manager.get_prompt_for_ab_test(
                "summarization-v1",
                versions
            )
            selection_counts[selected_version] = selection_counts.get(selected_version, 0) + 1
        except Exception:
            break

    print("\nA/B Test Results:")
    for version, count in selection_counts.items():
        print(f"  Version {version}: {count}% of traffic")


async def demo_render_with_tracking():
    """Demonstrate rendering with tracking metadata."""
    print("\n=== Render with Tracking Demo ===\n")

    manager = VersionedPromptManager(REGISTRY_URL, GROUP_ID)

    try:
        rendered, tracking = await manager.render_with_tracking(
            "summarization-v1",
            {
                "document": "This is a test document.",
                "style": "concise",
                "max_words": 100
            }
        )

        print("Rendered prompt:")
        print(rendered[:200] + "..." if len(rendered) > 200 else rendered)
        print("\nTracking metadata:")
        print(json.dumps(tracking, indent=2))

    except Exception as e:
        print(f"Error: {e}")


async def demo_version_comparison():
    """Demonstrate comparing prompt versions."""
    print("\n=== Version Comparison Demo ===\n")

    manager = VersionedPromptManager(REGISTRY_URL, GROUP_ID)

    try:
        # Note: This requires multiple versions to exist
        comparison = await manager.compare_versions(
            "summarization-v1",
            "1.0",
            "1.0"  # Using same version for demo
        )

        print("Version Comparison:")
        print(f"  Artifact: {comparison['artifact_id']}")
        print(f"  Versions: {comparison['versions']}")
        print(f"  Template changed: {comparison['template_changed']}")
        print(f"  Variables in v1: {comparison['variables_v1']}")
        print(f"  Variables in v2: {comparison['variables_v2']}")
        print(f"  Variables added: {comparison['variables_added']}")
        print(f"  Variables removed: {comparison['variables_removed']}")

    except Exception as e:
        print(f"Error: {e}")


async def main():
    """Run all version management demos."""
    print("=" * 60)
    print("Apicurio Registry - Prompt Versioning Demo")
    print("=" * 60)
    print(f"\nRegistry URL: {REGISTRY_URL}")
    print(f"Group ID: {GROUP_ID}")

    await demo_version_management()
    await demo_ab_testing()
    await demo_render_with_tracking()
    await demo_version_comparison()

    print("\n" + "=" * 60)
    print("Demo complete!")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
