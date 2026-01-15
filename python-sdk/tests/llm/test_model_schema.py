"""
Unit tests for ModelSchema class.

These tests verify the ModelSchema class functionality without requiring
a running registry server.
"""

import pytest
from apicurioregistrysdk.llm.model_registry import ModelSchema, ModelPricing


class TestModelSchema:
    """Tests for the ModelSchema dataclass."""

    def test_has_capability(self):
        """Test capability checking."""
        model = ModelSchema(
            model_id="test-model",
            provider="test-provider",
            version="1.0",
            capabilities=["chat", "vision", "tool_use"]
        )

        assert model.has_capability("chat") is True
        assert model.has_capability("CHAT") is True  # Case insensitive
        assert model.has_capability("vision") is True
        assert model.has_capability("audio") is False

    def test_has_all_capabilities(self):
        """Test checking for all capabilities."""
        model = ModelSchema(
            model_id="test-model",
            provider="test-provider",
            version="1.0",
            capabilities=["chat", "vision", "tool_use"]
        )

        assert model.has_all_capabilities(["chat", "vision"]) is True
        assert model.has_all_capabilities(["chat", "audio"]) is False
        assert model.has_all_capabilities([]) is True

    def test_has_any_capability(self):
        """Test checking for any capability."""
        model = ModelSchema(
            model_id="test-model",
            provider="test-provider",
            version="1.0",
            capabilities=["chat", "vision"]
        )

        assert model.has_any_capability(["chat", "audio"]) is True
        assert model.has_any_capability(["audio", "video"]) is False
        assert model.has_any_capability([]) is False

    def test_supports_context_size(self):
        """Test context window checking."""
        model = ModelSchema(
            model_id="test-model",
            provider="test-provider",
            version="1.0",
            context_window=128000
        )

        assert model.supports_context_size(100000) is True
        assert model.supports_context_size(128000) is True
        assert model.supports_context_size(200000) is False

    def test_supports_context_size_none(self):
        """Test context window checking when not set."""
        model = ModelSchema(
            model_id="test-model",
            provider="test-provider",
            version="1.0",
            context_window=None
        )

        assert model.supports_context_size(100000) is False

    def test_estimate_cost_with_pricing(self):
        """Test cost estimation with pricing info."""
        model = ModelSchema(
            model_id="test-model",
            provider="test-provider",
            version="1.0",
            pricing=ModelPricing(
                input_cost=0.015,
                output_cost=0.075,
                currency="USD",
                unit="1K tokens"
            )
        )

        # 1000 input tokens + 500 output tokens
        cost = model.estimate_cost(1000, 500)
        expected = (1000 / 1000) * 0.015 + (500 / 1000) * 0.075
        assert cost == pytest.approx(expected)

    def test_estimate_cost_without_pricing(self):
        """Test cost estimation returns None without pricing."""
        model = ModelSchema(
            model_id="test-model",
            provider="test-provider",
            version="1.0",
            pricing=None
        )

        assert model.estimate_cost(1000, 500) is None

    def test_to_dict(self):
        """Test conversion to dictionary."""
        model = ModelSchema(
            model_id="claude-3-opus",
            provider="anthropic",
            version="2024-02",
            context_window=200000,
            capabilities=["chat", "vision"],
            input_schema={"type": "object"},
            output_schema={"type": "object"},
            metadata={"trainingDataCutoff": "2024-02"},
            group_id="ai-models",
            artifact_id="claude-3-opus",
            pricing=ModelPricing(
                input_cost=0.015,
                output_cost=0.075
            )
        )

        result = model.to_dict()

        assert result["modelId"] == "claude-3-opus"
        assert result["provider"] == "anthropic"
        assert result["version"] == "2024-02"
        assert result["contextWindow"] == 200000
        assert result["capabilities"] == ["chat", "vision"]
        assert result["groupId"] == "ai-models"
        assert result["artifactId"] == "claude-3-opus"
        assert result["pricing"]["input"] == 0.015
        assert result["pricing"]["output"] == 0.075

    def test_to_dict_without_pricing(self):
        """Test conversion to dictionary without pricing."""
        model = ModelSchema(
            model_id="test-model",
            provider="test-provider",
            version="1.0"
        )

        result = model.to_dict()
        assert result["pricing"] is None


class TestModelPricing:
    """Tests for the ModelPricing dataclass."""

    def test_defaults(self):
        """Test default values."""
        pricing = ModelPricing(input_cost=0.01, output_cost=0.02)

        assert pricing.currency == "USD"
        assert pricing.unit == "1K tokens"

    def test_custom_values(self):
        """Test custom values."""
        pricing = ModelPricing(
            input_cost=0.001,
            output_cost=0.002,
            currency="EUR",
            unit="1M tokens"
        )

        assert pricing.input_cost == 0.001
        assert pricing.output_cost == 0.002
        assert pricing.currency == "EUR"
        assert pricing.unit == "1M tokens"
