"""
LlamaIndex integration for Apicurio Registry.

This module provides LlamaIndex-compatible components for working with
prompt templates stored in Apicurio Registry.

Example usage:
    from apicurioregistrysdk.llm.llama_index import ApicurioPromptTemplate

    # Use as a LlamaIndex PromptTemplate
    prompt = ApicurioPromptTemplate(
        registry_url="http://localhost:8080",
        artifact_id="summarization-v1"
    )
"""

from .prompt import ApicurioPromptTemplate

__all__ = [
    "ApicurioPromptTemplate",
]
