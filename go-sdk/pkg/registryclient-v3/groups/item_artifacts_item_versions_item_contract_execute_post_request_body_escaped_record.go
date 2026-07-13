package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type ItemArtifactsItemVersionsItemContractExecutePostRequestBody_record struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
}
// NewItemArtifactsItemVersionsItemContractExecutePostRequestBody_record instantiates a new ItemArtifactsItemVersionsItemContractExecutePostRequestBody_record and sets the default values.
func NewItemArtifactsItemVersionsItemContractExecutePostRequestBody_record()(*ItemArtifactsItemVersionsItemContractExecutePostRequestBody_record) {
    m := &ItemArtifactsItemVersionsItemContractExecutePostRequestBody_record{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateItemArtifactsItemVersionsItemContractExecutePostRequestBody_recordFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemVersionsItemContractExecutePostRequestBody_recordFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemVersionsItemContractExecutePostRequestBody_record(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostRequestBody_record) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostRequestBody_record) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    return res
}
// Serialize serializes information the current object
func (m *ItemArtifactsItemVersionsItemContractExecutePostRequestBody_record) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteAdditionalData(m.GetAdditionalData())
        if err != nil {
            return err
        }
    }
    return nil
}
// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *ItemArtifactsItemVersionsItemContractExecutePostRequestBody_record) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
type ItemArtifactsItemVersionsItemContractExecutePostRequestBody_recordable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
}
