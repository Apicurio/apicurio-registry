from typing import Any, Optional

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class _BaseModel(BaseModel):
    model_config = ConfigDict(
        extra="allow",
        populate_by_name=True,
        alias_generator=to_camel,
    )


class AgentProvider(_BaseModel):
    organization: Optional[str] = None
    url: Optional[str] = None


class AgentExtension(_BaseModel):
    uri: Optional[str] = None
    description: Optional[str] = None
    required: Optional[bool] = None
    params: Optional[dict[str, Any]] = None


class AgentCapabilities(_BaseModel):
    streaming: Optional[bool] = None
    push_notifications: Optional[bool] = None
    extended_agent_card: Optional[bool] = None
    extensions: Optional[list["AgentExtension"]] = None


class SecurityScheme(_BaseModel):
    type: Optional[str] = None
    description: Optional[str] = None
    location: Optional[str] = None
    name: Optional[str] = None
    scheme: Optional[str] = None
    bearer_format: Optional[str] = None
    flows: Optional[dict[str, Any]] = None
    oauth2_metadata_url: Optional[str] = None
    open_id_connect_url: Optional[str] = None


class SecurityRequirement(_BaseModel):
    schemes: Optional[dict[str, list[str]]] = None


class AgentCardSignature(_BaseModel):
    protected_header: Optional[str] = Field(default=None, alias="protected")
    signature: Optional[str] = None
    header: Optional[dict[str, Any]] = None


class AgentSkill(_BaseModel):
    id: Optional[str] = None
    name: Optional[str] = None
    description: Optional[str] = None
    tags: Optional[list[str]] = None
    examples: Optional[list[str]] = None
    input_modes: Optional[list[str]] = None
    output_modes: Optional[list[str]] = None
    security_requirements: Optional[list[SecurityRequirement]] = None


class AgentInterface(_BaseModel):
    url: Optional[str] = None
    protocol_binding: Optional[str] = None
    protocol_version: Optional[str] = None
    tenant: Optional[str] = None


class AgentCard(_BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    version: Optional[str] = None
    protocol_version: Optional[str] = None
    provider: Optional[AgentProvider] = None
    capabilities: Optional[AgentCapabilities] = None
    skills: Optional[list[AgentSkill]] = None
    default_input_modes: Optional[list[str]] = None
    default_output_modes: Optional[list[str]] = None
    supported_interfaces: Optional[list[AgentInterface]] = None
    security_schemes: Optional[dict[str, SecurityScheme]] = None
    security_requirements: Optional[list[SecurityRequirement]] = None
    icon_url: Optional[str] = None
    documentation_url: Optional[str] = None
    signatures: Optional[list[AgentCardSignature]] = None


class AgentSearchResult(_BaseModel):
    group_id: Optional[str] = None
    artifact_id: Optional[str] = None
    name: Optional[str] = None
    description: Optional[str] = None
    version: Optional[str] = None
    supported_interfaces: Optional[list[AgentInterface]] = None
    skills: Optional[list[str]] = None
    capabilities: Optional[AgentCapabilities] = None
    created_on: Optional[int] = None
    owner: Optional[str] = None


class AgentSearchResults(_BaseModel):
    count: int = 0
    agents: list[AgentSearchResult] = []


class AgentSearchFilters(_BaseModel):
    capabilities: Optional[dict[str, bool]] = None
    skills: Optional[list[str]] = None
    labels: Optional[dict[str, str]] = None
    input_modes: Optional[list[str]] = None
    output_modes: Optional[list[str]] = None
    protocol_bindings: Optional[list[str]] = None


class AgentSearchRequest(_BaseModel):
    query: Optional[str] = None
    filters: Optional[AgentSearchFilters] = None
    limit: int = 20
    offset: int = 0


class McpToolSearchResult(_BaseModel):
    group_id: Optional[str] = None
    artifact_id: Optional[str] = None
    name: Optional[str] = None
    title: Optional[str] = None
    description: Optional[str] = None
    owner: Optional[str] = None
    created_on: Optional[int] = None
    parameters: Optional[list[str]] = None


class McpToolSearchResults(_BaseModel):
    count: int = 0
    tools: list[McpToolSearchResult] = []


class RenderValidationError(_BaseModel):
    variable_name: str
    message: str
    expected_type: Optional[str] = None
    actual_type: Optional[str] = None


class RenderPromptResponse(_BaseModel):
    rendered: str
    group_id: Optional[str] = None
    artifact_id: Optional[str] = None
    version: Optional[str] = None
    validation_errors: Optional[list[RenderValidationError]] = None
