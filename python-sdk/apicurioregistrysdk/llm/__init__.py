"""
Apicurio Registry LLM Integration Module.

This module provides high-level APIs for working with LLM artifacts
(MODEL_SCHEMA and PROMPT_TEMPLATE) stored in Apicurio Registry,
with integrations for popular LLM frameworks like LangChain and LlamaIndex.

Example usage:
    from apicurioregistrysdk.llm import PromptRegistry, ModelRegistry

    # Initialize registries
    prompt_registry = PromptRegistry("http://localhost:8080")
    model_registry = ModelRegistry("http://localhost:8080")

    # Fetch and use a prompt template
    prompt = await prompt_registry.get_prompt("summarization-v1")
    rendered = prompt.render(document="...", style="concise", max_words=200)

    # Convert to LangChain
    lc_prompt = prompt.to_langchain()

    # Search for models by capability
    models = await model_registry.search(capabilities=["vision", "tool_use"])
"""

from .prompt_registry import PromptRegistry, PromptTemplate
from .model_registry import ModelRegistry, ModelSchema

__all__ = [
    "PromptRegistry",
    "PromptTemplate",
    "ModelRegistry",
    "ModelSchema",
]
