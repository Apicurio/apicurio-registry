package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// Deprecated: This class is obsolete. Use ItemArtifactsItemContractQualityGetResponseable instead.
type ItemArtifactsItemContractQualityResponse struct {
    ItemArtifactsItemContractQualityGetResponse
}
// NewItemArtifactsItemContractQualityResponse instantiates a new ItemArtifactsItemContractQualityResponse and sets the default values.
func NewItemArtifactsItemContractQualityResponse()(*ItemArtifactsItemContractQualityResponse) {
    m := &ItemArtifactsItemContractQualityResponse{
        ItemArtifactsItemContractQualityGetResponse: *NewItemArtifactsItemContractQualityGetResponse(),
    }
    return m
}
// CreateItemArtifactsItemContractQualityResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemContractQualityResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemContractQualityResponse(), nil
}
// Deprecated: This class is obsolete. Use ItemArtifactsItemContractQualityGetResponseable instead.
type ItemArtifactsItemContractQualityResponseable interface {
    ItemArtifactsItemContractQualityGetResponseable
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
}
