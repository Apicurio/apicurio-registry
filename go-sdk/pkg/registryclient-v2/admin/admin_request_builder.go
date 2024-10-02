package admin

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// AdminRequestBuilder builds and executes requests for operations under \admin
type AdminRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ArtifactTypes the list of artifact types supported by this instance of Registry.
// returns a *ArtifactTypesRequestBuilder when successful
func (m *AdminRequestBuilder) ArtifactTypes() *ArtifactTypesRequestBuilder {
	return NewArtifactTypesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Config the config property
// returns a *ConfigRequestBuilder when successful
func (m *AdminRequestBuilder) Config() *ConfigRequestBuilder {
	return NewConfigRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// NewAdminRequestBuilderInternal instantiates a new AdminRequestBuilder and sets the default values.
func NewAdminRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AdminRequestBuilder {
	m := &AdminRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin", pathParameters),
	}
	return m
}

// NewAdminRequestBuilder instantiates a new AdminRequestBuilder and sets the default values.
func NewAdminRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AdminRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewAdminRequestBuilderInternal(urlParams, requestAdapter)
}

// Export provides a way to export registry data.
// returns a *ExportRequestBuilder when successful
func (m *AdminRequestBuilder) Export() *ExportRequestBuilder {
	return NewExportRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// ImportEscaped provides a way to import data into the registry.
// returns a *ImportRequestBuilder when successful
func (m *AdminRequestBuilder) ImportEscaped() *ImportRequestBuilder {
	return NewImportRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// RoleMappings collection to manage role mappings for authenticated principals
// returns a *RoleMappingsRequestBuilder when successful
func (m *AdminRequestBuilder) RoleMappings() *RoleMappingsRequestBuilder {
	return NewRoleMappingsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Rules manage the global rules that apply to all artifacts if not otherwise configured.
// returns a *RulesRequestBuilder when successful
func (m *AdminRequestBuilder) Rules() *RulesRequestBuilder {
	return NewRulesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
