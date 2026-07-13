package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// Deprecated: This class is obsolete. Use ItemArtifactsItemVersionsItemContractExecutePostResponseable instead.
type ItemArtifactsItemVersionsItemContractExecuteResponse struct {
    ItemArtifactsItemVersionsItemContractExecutePostResponse
}
// NewItemArtifactsItemVersionsItemContractExecuteResponse instantiates a new ItemArtifactsItemVersionsItemContractExecuteResponse and sets the default values.
func NewItemArtifactsItemVersionsItemContractExecuteResponse()(*ItemArtifactsItemVersionsItemContractExecuteResponse) {
    m := &ItemArtifactsItemVersionsItemContractExecuteResponse{
        ItemArtifactsItemVersionsItemContractExecutePostResponse: *NewItemArtifactsItemVersionsItemContractExecutePostResponse(),
    }
    return m
}
// CreateItemArtifactsItemVersionsItemContractExecuteResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemVersionsItemContractExecuteResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemVersionsItemContractExecuteResponse(), nil
}
// Deprecated: This class is obsolete. Use ItemArtifactsItemVersionsItemContractExecutePostResponseable instead.
type ItemArtifactsItemVersionsItemContractExecuteResponseable interface {
    ItemArtifactsItemVersionsItemContractExecutePostResponseable
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
}
