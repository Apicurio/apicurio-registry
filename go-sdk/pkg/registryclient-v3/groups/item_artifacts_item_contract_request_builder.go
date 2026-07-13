package groups

import (
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemContractRequestBuilder builds and executes requests for operations under \groups\{groupId}\artifacts\{artifactId}\contract
type ItemArtifactsItemContractRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// Audit get the contract audit log for an artifact.
// returns a *ItemArtifactsItemContractAuditRequestBuilder when successful
func (m *ItemArtifactsItemContractRequestBuilder) Audit()(*ItemArtifactsItemContractAuditRequestBuilder) {
    return NewItemArtifactsItemContractAuditRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// CompatibilityGroup manage the compatibility group for an artifact's contract.
// returns a *ItemArtifactsItemContractCompatibilityGroupRequestBuilder when successful
func (m *ItemArtifactsItemContractRequestBuilder) CompatibilityGroup()(*ItemArtifactsItemContractCompatibilityGroupRequestBuilder) {
    return NewItemArtifactsItemContractCompatibilityGroupRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// NewItemArtifactsItemContractRequestBuilderInternal instantiates a new ItemArtifactsItemContractRequestBuilder and sets the default values.
func NewItemArtifactsItemContractRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractRequestBuilder) {
    m := &ItemArtifactsItemContractRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/contract", pathParameters),
    }
    return m
}
// NewItemArtifactsItemContractRequestBuilder instantiates a new ItemArtifactsItemContractRequestBuilder and sets the default values.
func NewItemArtifactsItemContractRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewItemArtifactsItemContractRequestBuilderInternal(urlParams, requestAdapter)
}
// Export export contract metadata, rules, and field tags as ODCS YAML.
// returns a *ItemArtifactsItemContractExportRequestBuilder when successful
func (m *ItemArtifactsItemContractRequestBuilder) Export()(*ItemArtifactsItemContractExportRequestBuilder) {
    return NewItemArtifactsItemContractExportRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// Metadata manage contract metadata for an artifact.
// returns a *ItemArtifactsItemContractMetadataRequestBuilder when successful
func (m *ItemArtifactsItemContractRequestBuilder) Metadata()(*ItemArtifactsItemContractMetadataRequestBuilder) {
    return NewItemArtifactsItemContractMetadataRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// Migrate execute migration rules to transform a record between versions.
// returns a *ItemArtifactsItemContractMigrateRequestBuilder when successful
func (m *ItemArtifactsItemContractRequestBuilder) Migrate()(*ItemArtifactsItemContractMigrateRequestBuilder) {
    return NewItemArtifactsItemContractMigrateRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// Promote promote a contract to the next deployment stage.
// returns a *ItemArtifactsItemContractPromoteRequestBuilder when successful
func (m *ItemArtifactsItemContractRequestBuilder) Promote()(*ItemArtifactsItemContractPromoteRequestBuilder) {
    return NewItemArtifactsItemContractPromoteRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// Quality get quality score for a contract.
// returns a *ItemArtifactsItemContractQualityRequestBuilder when successful
func (m *ItemArtifactsItemContractRequestBuilder) Quality()(*ItemArtifactsItemContractQualityRequestBuilder) {
    return NewItemArtifactsItemContractQualityRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// Ruleset manage contract ruleset for an artifact (artifact-level).
// returns a *ItemArtifactsItemContractRulesetRequestBuilder when successful
func (m *ItemArtifactsItemContractRequestBuilder) Ruleset()(*ItemArtifactsItemContractRulesetRequestBuilder) {
    return NewItemArtifactsItemContractRulesetRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// Status transition the contract lifecycle status.
// returns a *ItemArtifactsItemContractStatusRequestBuilder when successful
func (m *ItemArtifactsItemContractRequestBuilder) Status()(*ItemArtifactsItemContractStatusRequestBuilder) {
    return NewItemArtifactsItemContractStatusRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
