package wellknown

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// SchemasWithSchemaTypeItemRequestBuilder builds and executes requests for operations under \well-known\schemas\{schemaType}
type SchemasWithSchemaTypeItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ByVersion get a JSON Schema for a specific LLM artifact type.
// returns a *SchemasItemWithVersionItemRequestBuilder when successful
func (m *SchemasWithSchemaTypeItemRequestBuilder) ByVersion(version string) *SchemasItemWithVersionItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if version != "" {
		urlTplParams["version"] = version
	}
	return NewSchemasItemWithVersionItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewSchemasWithSchemaTypeItemRequestBuilderInternal instantiates a new SchemasWithSchemaTypeItemRequestBuilder and sets the default values.
func NewSchemasWithSchemaTypeItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *SchemasWithSchemaTypeItemRequestBuilder {
	m := &SchemasWithSchemaTypeItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/schemas/{schemaType}", pathParameters),
	}
	return m
}

// NewSchemasWithSchemaTypeItemRequestBuilder instantiates a new SchemasWithSchemaTypeItemRequestBuilder and sets the default values.
func NewSchemasWithSchemaTypeItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *SchemasWithSchemaTypeItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewSchemasWithSchemaTypeItemRequestBuilderInternal(urlParams, requestAdapter)
}
