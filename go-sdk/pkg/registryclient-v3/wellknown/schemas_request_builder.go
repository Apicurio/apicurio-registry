package wellknown

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// SchemasRequestBuilder builds and executes requests for operations under \well-known\schemas
type SchemasRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// BySchemaType gets an item from the github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3.wellKnown.schemas.item collection
// returns a *SchemasWithSchemaTypeItemRequestBuilder when successful
func (m *SchemasRequestBuilder) BySchemaType(schemaType string) *SchemasWithSchemaTypeItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if schemaType != "" {
		urlTplParams["schemaType"] = schemaType
	}
	return NewSchemasWithSchemaTypeItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewSchemasRequestBuilderInternal instantiates a new SchemasRequestBuilder and sets the default values.
func NewSchemasRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *SchemasRequestBuilder {
	m := &SchemasRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/schemas", pathParameters),
	}
	return m
}

// NewSchemasRequestBuilder instantiates a new SchemasRequestBuilder and sets the default values.
func NewSchemasRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *SchemasRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewSchemasRequestBuilderInternal(urlParams, requestAdapter)
}
