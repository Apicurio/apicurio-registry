package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// Deprecated: This class is obsolete. Use ItemArtifactsItemContractMigratePostResponseable instead.
type ItemArtifactsItemContractMigrateResponse struct {
    ItemArtifactsItemContractMigratePostResponse
}
// NewItemArtifactsItemContractMigrateResponse instantiates a new ItemArtifactsItemContractMigrateResponse and sets the default values.
func NewItemArtifactsItemContractMigrateResponse()(*ItemArtifactsItemContractMigrateResponse) {
    m := &ItemArtifactsItemContractMigrateResponse{
        ItemArtifactsItemContractMigratePostResponse: *NewItemArtifactsItemContractMigratePostResponse(),
    }
    return m
}
// CreateItemArtifactsItemContractMigrateResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemContractMigrateResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemContractMigrateResponse(), nil
}
// Deprecated: This class is obsolete. Use ItemArtifactsItemContractMigratePostResponseable instead.
type ItemArtifactsItemContractMigrateResponseable interface {
    ItemArtifactsItemContractMigratePostResponseable
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
}
