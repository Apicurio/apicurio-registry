package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// Deprecated: This class is obsolete. Use ItemArtifactsItemContractCompatibilityGroupGetResponseable instead.
type ItemArtifactsItemContractCompatibilityGroupResponse struct {
    ItemArtifactsItemContractCompatibilityGroupGetResponse
}
// NewItemArtifactsItemContractCompatibilityGroupResponse instantiates a new ItemArtifactsItemContractCompatibilityGroupResponse and sets the default values.
func NewItemArtifactsItemContractCompatibilityGroupResponse()(*ItemArtifactsItemContractCompatibilityGroupResponse) {
    m := &ItemArtifactsItemContractCompatibilityGroupResponse{
        ItemArtifactsItemContractCompatibilityGroupGetResponse: *NewItemArtifactsItemContractCompatibilityGroupGetResponse(),
    }
    return m
}
// CreateItemArtifactsItemContractCompatibilityGroupResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemContractCompatibilityGroupResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemContractCompatibilityGroupResponse(), nil
}
// Deprecated: This class is obsolete. Use ItemArtifactsItemContractCompatibilityGroupGetResponseable instead.
type ItemArtifactsItemContractCompatibilityGroupResponseable interface {
    ItemArtifactsItemContractCompatibilityGroupGetResponseable
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
}
