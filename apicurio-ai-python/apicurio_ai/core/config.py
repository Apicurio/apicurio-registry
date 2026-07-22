from dataclasses import dataclass, field
from typing import Optional


@dataclass
class RegistryConfig:
    registry_url: str
    auth_token: Optional[str] = None
    timeout: float = 30.0
    default_group_id: str = "default"

    @property
    def wellknown_base_url(self) -> str:
        return f"{self.registry_url.rstrip('/')}/.well-known"

    @property
    def api_base_url(self) -> str:
        return f"{self.registry_url.rstrip('/')}/apis/registry/v3"

    def auth_headers(self) -> dict[str, str]:
        if self.auth_token:
            return {"Authorization": f"Bearer {self.auth_token}"}
        return {}
