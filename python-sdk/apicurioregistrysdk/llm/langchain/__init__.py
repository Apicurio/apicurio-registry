"""
LangChain integration for Apicurio Registry.

This module provides LangChain-compatible components for working with
prompt templates and model schemas stored in Apicurio Registry.

Example usage:
    from apicurioregistrysdk.llm.langchain import (
        ApicurioPromptTemplate,
        ApicurioPromptLookupTool,
    )

    # Use as a LangChain PromptTemplate
    prompt = ApicurioPromptTemplate(
        registry_url="http://localhost:8080",
        artifact_id="summarization-v1"
    )
    result = prompt.format(document="...", style="concise")

    # Use as a LangChain Tool
    tool = ApicurioPromptLookupTool(registry_url="http://localhost:8080")
    # Can be added to LangChain agents
"""

from .prompt_template import ApicurioPromptTemplate
from .tools import ApicurioPromptLookupTool, ApicurioModelLookupTool

__all__ = [
    "ApicurioPromptTemplate",
    "ApicurioPromptLookupTool",
    "ApicurioModelLookupTool",
]
