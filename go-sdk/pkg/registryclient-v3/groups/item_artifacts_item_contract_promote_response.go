package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// Deprecated: This class is obsolete. Use ItemArtifactsItemContractPromotePostResponseable instead.
type ItemArtifactsItemContractPromoteResponse struct {
    ItemArtifactsItemContractPromotePostResponse
}
// NewItemArtifactsItemContractPromoteResponse instantiates a new ItemArtifactsItemContractPromoteResponse and sets the default values.
func NewItemArtifactsItemContractPromoteResponse()(*ItemArtifactsItemContractPromoteResponse) {
    m := &ItemArtifactsItemContractPromoteResponse{
        ItemArtifactsItemContractPromotePostResponse: *NewItemArtifactsItemContractPromotePostResponse(),
    }
    return m
}
// CreateItemArtifactsItemContractPromoteResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemContractPromoteResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemContractPromoteResponse(), nil
}
// Deprecated: This class is obsolete. Use ItemArtifactsItemContractPromotePostResponseable instead.
type ItemArtifactsItemContractPromoteResponseable interface {
    ItemArtifactsItemContractPromotePostResponseable
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
}
