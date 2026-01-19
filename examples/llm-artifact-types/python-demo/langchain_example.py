#!/usr/bin/env python3
"""
LangChain Integration Example for Apicurio Registry LLM Artifact Types

This example demonstrates how to use Apicurio Registry to manage
versioned prompt templates with LangChain.

Prerequisites:
    1. Start Apicurio Registry: docker compose up -d (from parent directory)
    2. Install dependencies: pip install -r requirements.txt
    3. Set OPENAI_API_KEY environment variable (optional, for LLM calls)

Usage:
    python langchain_example.py
"""

import asyncio
import json
import os
from typing import Optional

# Apicurio Registry SDK
from apicurioregistrysdk.llm import PromptRegistry, ModelRegistry
from apicurioregistrysdk.llm.langchain import ApicurioPromptTemplate

# Registry configuration
REGISTRY_URL = os.getenv("REGISTRY_URL", "http://localhost:8080")
GROUP_ID = "llm-demo"


async def setup_sample_artifacts(registry: PromptRegistry):
    """Create sample prompt templates in the registry."""

    # Sample summarization prompt
    summarization_prompt = {
        "templateId": "summarization-v1",
        "name": "Document Summarization",
        "version": "1.0",
        "template": """You are a helpful assistant that summarizes documents.

Style: {{style}}
Maximum length: {{max_words}} words

Document to summarize:
{{document}}

Please provide a {{style}} summary of the document above.""",
        "variables": {
            "style": {
                "type": "string",
                "enum": ["concise", "detailed", "bullet-points"],
                "default": "concise"
            },
            "max_words": {
                "type": "integer",
                "minimum": 50,
                "maximum": 1000,
                "default": 200
            },
            "document": {
                "type": "string",
                "required": True
            }
        },
        "metadata": {
            "recommendedModels": ["gpt-4-turbo", "claude-3-opus"]
        }
    }

    # Create the artifact using the Kiota client
    # Note: In a real scenario, you'd use the full SDK client
    print(f"Sample prompt template prepared: {summarization_prompt['templateId']}")
    return summarization_prompt


async def demo_prompt_registry():
    """Demonstrate PromptRegistry usage."""
    print("\n=== PromptRegistry Demo ===\n")

    registry = PromptRegistry(REGISTRY_URL, group_id=GROUP_ID)

    # Note: This requires the artifact to exist in the registry
    # Run ../demo.sh first to populate sample data
    try:
        # Fetch a prompt template
        prompt = await registry.get_prompt_async("summarization-v1")
        print(f"Fetched prompt: {prompt.name}")
        print(f"Template ID: {prompt.template_id}")
        print(f"Variables: {list(prompt.variables.keys())}")

        # Render locally
        rendered = prompt.render(
            document="Apicurio Registry is a schema registry for managing API artifacts.",
            style="concise",
            max_words=50
        )
        print(f"\nRendered prompt:\n{rendered}")

        # Server-side rendering with validation
        server_rendered = await registry.render_server_side_async(
            "summarization-v1",
            {
                "document": "Apicurio Registry provides governance for API schemas.",
                "style": "bullet-points",
                "max_words": 100
            }
        )
        print(f"\nServer-rendered prompt:\n{server_rendered}")

    except Exception as e:
        print(f"Error: {e}")
        print("Make sure to run ../demo.sh first to create sample artifacts")


async def demo_model_registry():
    """Demonstrate ModelRegistry usage."""
    print("\n=== ModelRegistry Demo ===\n")

    registry = ModelRegistry(REGISTRY_URL, group_id=GROUP_ID)

    try:
        # Search for models with specific capabilities
        results = await registry.search_async(
            capabilities=["chat", "function_calling"],
            min_context_window=50000
        )
        print(f"Found {len(results)} models with chat + function_calling:")
        for model in results:
            print(f"  - {model.model_id} ({model.provider}): {model.context_window} tokens")

        # Search by provider
        openai_models = await registry.search_async(provider="openai")
        print(f"\nOpenAI models: {len(openai_models)}")

        # Compare models
        if len(results) >= 2:
            comparison = await registry.compare_models_async(
                [results[0].artifact_id, results[1].artifact_id]
            )
            print(f"\nModel comparison: {comparison}")

    except Exception as e:
        print(f"Error: {e}")
        print("Make sure MODEL_SCHEMA artifacts exist in the registry")


async def demo_langchain_integration():
    """Demonstrate LangChain integration."""
    print("\n=== LangChain Integration Demo ===\n")

    # Create an Apicurio-backed prompt template
    prompt = ApicurioPromptTemplate(
        registry_url=REGISTRY_URL,
        group_id=GROUP_ID,
        artifact_id="summarization-v1"
    )

    try:
        # Format the prompt (fetches from registry automatically)
        formatted = prompt.format(
            document="LangChain is a framework for building LLM applications.",
            style="concise",
            max_words=50
        )
        print(f"Formatted prompt from registry:\n{formatted}")

        # Convert to native LangChain template
        lc_template = prompt.to_langchain()
        print(f"\nConverted to LangChain PromptTemplate")
        print(f"Input variables: {lc_template.input_variables}")

    except Exception as e:
        print(f"Error: {e}")
        print("Make sure the registry is running and has sample data")


async def demo_with_openai():
    """Demonstrate using registry prompts with OpenAI (requires API key)."""
    print("\n=== OpenAI Integration Demo ===\n")

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        print("Skipping OpenAI demo - set OPENAI_API_KEY to enable")
        return

    try:
        from langchain_openai import ChatOpenAI
        from langchain_core.output_parsers import StrOutputParser

        # Create the chain
        prompt = ApicurioPromptTemplate(
            registry_url=REGISTRY_URL,
            group_id=GROUP_ID,
            artifact_id="summarization-v1"
        )

        model = ChatOpenAI(model="gpt-4-turbo", temperature=0)
        chain = prompt | model | StrOutputParser()

        # Run the chain
        result = await chain.ainvoke({
            "document": """
            Apicurio Registry is an open-source schema registry that provides
            governance for API artifacts including OpenAPI specs, AsyncAPI specs,
            JSON Schema, Protobuf, Avro, and now LLM-related schemas like
            MODEL_SCHEMA and PROMPT_TEMPLATE.
            """,
            "style": "concise",
            "max_words": 50
        })

        print(f"LLM Response:\n{result}")

    except ImportError:
        print("Install langchain-openai for this demo: pip install langchain-openai")
    except Exception as e:
        print(f"Error: {e}")


async def main():
    """Run all demos."""
    print("=" * 60)
    print("Apicurio Registry LLM Artifact Types - LangChain Demo")
    print("=" * 60)
    print(f"\nRegistry URL: {REGISTRY_URL}")
    print(f"Group ID: {GROUP_ID}")

    await demo_prompt_registry()
    await demo_model_registry()
    await demo_langchain_integration()
    await demo_with_openai()

    print("\n" + "=" * 60)
    print("Demo complete!")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
