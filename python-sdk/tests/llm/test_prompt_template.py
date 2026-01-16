"""
Unit tests for PromptTemplate class.

These tests verify the PromptTemplate class functionality without requiring
a running registry server.
"""

import pytest
from apicurioregistrysdk.llm.prompt_registry import PromptTemplate, ValidationError


class TestPromptTemplate:
    """Tests for the PromptTemplate dataclass."""

    def test_basic_render(self):
        """Test basic variable substitution."""
        template = PromptTemplate(
            template_id="test-1",
            name="Test Template",
            version="1.0",
            template="Hello {{name}}! Welcome to {{place}}.",
            variables={
                "name": {"type": "string"},
                "place": {"type": "string"},
            }
        )

        result = template.render(name="World", place="Registry")
        assert result == "Hello World! Welcome to Registry."

    def test_render_with_numbers(self):
        """Test rendering with numeric values."""
        template = PromptTemplate(
            template_id="test-2",
            name="Test Template",
            version="1.0",
            template="Count to {{count}} with step {{step}}.",
            variables={
                "count": {"type": "integer"},
                "step": {"type": "number"},
            }
        )

        result = template.render(count=10, step=2.5)
        assert result == "Count to 10 with step 2.5."

    def test_render_with_list(self):
        """Test rendering with list values."""
        template = PromptTemplate(
            template_id="test-3",
            name="Test Template",
            version="1.0",
            template="Items: {{items}}",
            variables={
                "items": {"type": "array"},
            }
        )

        result = template.render(items=["a", "b", "c"])
        assert result == 'Items: ["a", "b", "c"]'

    def test_render_with_dict(self):
        """Test rendering with dictionary values."""
        template = PromptTemplate(
            template_id="test-4",
            name="Test Template",
            version="1.0",
            template="Config: {{config}}",
            variables={
                "config": {"type": "object"},
            }
        )

        result = template.render(config={"key": "value"})
        assert result == 'Config: {"key": "value"}'

    def test_validate_required_variable_missing(self):
        """Test validation fails for missing required variable."""
        template = PromptTemplate(
            template_id="test-5",
            name="Test Template",
            version="1.0",
            template="Hello {{name}}!",
            variables={
                "name": {"type": "string", "required": True},
            }
        )

        errors = template.validate_variables({})
        assert len(errors) == 1
        assert errors[0].variable_name == "name"
        assert "missing" in errors[0].message.lower()

    def test_validate_type_mismatch(self):
        """Test validation fails for type mismatch."""
        template = PromptTemplate(
            template_id="test-6",
            name="Test Template",
            version="1.0",
            template="Count: {{count}}",
            variables={
                "count": {"type": "integer"},
            }
        )

        errors = template.validate_variables({"count": "not-a-number"})
        assert len(errors) == 1
        assert errors[0].variable_name == "count"
        assert "type mismatch" in errors[0].message.lower()
        assert errors[0].expected_type == "integer"
        assert errors[0].actual_type == "str"

    def test_validate_enum_invalid(self):
        """Test validation fails for invalid enum value."""
        template = PromptTemplate(
            template_id="test-7",
            name="Test Template",
            version="1.0",
            template="Style: {{style}}",
            variables={
                "style": {"type": "string", "enum": ["concise", "detailed"]},
            }
        )

        errors = template.validate_variables({"style": "verbose"})
        assert len(errors) == 1
        assert errors[0].variable_name == "style"
        assert "verbose" in errors[0].message
        assert "allowed values" in errors[0].message.lower()

    def test_validate_minimum(self):
        """Test validation fails when value is below minimum."""
        template = PromptTemplate(
            template_id="test-8",
            name="Test Template",
            version="1.0",
            template="Words: {{max_words}}",
            variables={
                "max_words": {"type": "integer", "minimum": 50},
            }
        )

        errors = template.validate_variables({"max_words": 25})
        assert len(errors) == 1
        assert errors[0].variable_name == "max_words"
        assert "less than minimum" in errors[0].message.lower()

    def test_validate_maximum(self):
        """Test validation fails when value is above maximum."""
        template = PromptTemplate(
            template_id="test-9",
            name="Test Template",
            version="1.0",
            template="Words: {{max_words}}",
            variables={
                "max_words": {"type": "integer", "maximum": 1000},
            }
        )

        errors = template.validate_variables({"max_words": 1500})
        assert len(errors) == 1
        assert errors[0].variable_name == "max_words"
        assert "greater than maximum" in errors[0].message.lower()

    def test_validate_passes(self):
        """Test validation passes for valid input."""
        template = PromptTemplate(
            template_id="test-10",
            name="Test Template",
            version="1.0",
            template="Summarize in {{style}} style, max {{max_words}} words.",
            variables={
                "style": {"type": "string", "enum": ["concise", "detailed"]},
                "max_words": {"type": "integer", "minimum": 50, "maximum": 1000},
            }
        )

        errors = template.validate_variables({"style": "concise", "max_words": 200})
        assert len(errors) == 0

    def test_render_raises_on_validation_error(self):
        """Test render raises ValueError for validation errors."""
        template = PromptTemplate(
            template_id="test-11",
            name="Test Template",
            version="1.0",
            template="Hello {{name}}!",
            variables={
                "name": {"type": "string", "required": True},
            }
        )

        with pytest.raises(ValueError) as exc_info:
            template.render()

        assert "validation failed" in str(exc_info.value).lower()

    def test_get_variable_names(self):
        """Test extraction of variable names from template."""
        template = PromptTemplate(
            template_id="test-12",
            name="Test Template",
            version="1.0",
            template="Hello {{name}}! Count to {{count}} with {{step}}.",
            variables={}
        )

        names = template.get_variable_names()
        assert set(names) == {"name", "count", "step"}

    def test_get_defaults(self):
        """Test extraction of default values."""
        template = PromptTemplate(
            template_id="test-13",
            name="Test Template",
            version="1.0",
            template="Style: {{style}}, Words: {{max_words}}",
            variables={
                "style": {"type": "string", "default": "concise"},
                "max_words": {"type": "integer", "default": 200},
                "optional": {"type": "string"},  # No default
            }
        )

        defaults = template.get_defaults()
        assert defaults == {"style": "concise", "max_words": 200}

    def test_render_keeps_unset_placeholders(self):
        """Test that unset placeholders are kept in output."""
        template = PromptTemplate(
            template_id="test-14",
            name="Test Template",
            version="1.0",
            template="Hello {{name}}! See {{other}}.",
            variables={
                "name": {"type": "string"},
                "other": {"type": "string"},
            }
        )

        result = template.render(name="World")
        assert result == "Hello World! See {{other}}."


class TestPromptTemplateLangChainConversion:
    """Tests for LangChain conversion (skip if LangChain not available)."""

    def test_to_langchain_import_error(self):
        """Test that to_langchain raises ImportError when LangChain is not installed."""
        template = PromptTemplate(
            template_id="test-lc-1",
            name="Test Template",
            version="1.0",
            template="Hello {{name}}!",
            variables={"name": {"type": "string"}}
        )

        # This test will pass if LangChain is not installed
        # and fail if it is (which is fine, as the feature should work)
        try:
            result = template.to_langchain()
            # If we get here, LangChain is installed
            assert result is not None
        except ImportError as e:
            assert "langchain" in str(e).lower()

    @pytest.mark.skipif(True, reason="Requires LangChain installation")
    def test_to_langchain_conversion(self):
        """Test conversion to LangChain PromptTemplate."""
        template = PromptTemplate(
            template_id="test-lc-2",
            name="Test Template",
            version="1.0",
            template="Hello {{name}}! Count: {{count}}",
            variables={
                "name": {"type": "string"},
                "count": {"type": "integer"},
            }
        )

        lc_prompt = template.to_langchain()

        # Verify the template was converted
        assert "{name}" in lc_prompt.template
        assert "{count}" in lc_prompt.template
        assert "{{" not in lc_prompt.template  # Converted from {{}} to {}


class TestPromptTemplateLlamaIndexConversion:
    """Tests for LlamaIndex conversion (skip if LlamaIndex not available)."""

    def test_to_llama_index_import_error(self):
        """Test that to_llama_index raises ImportError when LlamaIndex is not installed."""
        template = PromptTemplate(
            template_id="test-li-1",
            name="Test Template",
            version="1.0",
            template="Hello {{name}}!",
            variables={"name": {"type": "string"}}
        )

        try:
            result = template.to_llama_index()
            assert result is not None
        except ImportError as e:
            assert "llama" in str(e).lower()
