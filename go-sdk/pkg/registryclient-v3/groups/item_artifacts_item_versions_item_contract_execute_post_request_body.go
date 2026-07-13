package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type ItemArtifactsItemVersionsItemContractExecutePostRequestBody struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The record property
    record ItemArtifactsItemVersionsItemContractExecutePostRequestBody_recordable
}
// NewItemArtifactsItemVersionsItemContractExecutePostRequestBody instantiates a new ItemArtifactsItemVersionsItemContractExecutePostRequestBody and sets the default values.
func NewItemArtifactsItemVersionsItemContractExecutePostRequestBody()(*ItemArtifactsItemVersionsItemContractExecutePostRequestBody) {
    m := &ItemArtifactsItemVersionsItemContractExecutePostRequestBody{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateItemArtifactsItemVersionsItemContractExecutePostRequestBodyFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemVersionsItemContractExecutePostRequestBodyFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemVersionsItemContractExecutePostRequestBody(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostRequestBody) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostRequestBody) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["record"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateItemArtifactsItemVersionsItemContractExecutePostRequestBody_recordFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetRecord(val.(ItemArtifactsItemVersionsItemContractExecutePostRequestBody_recordable))
        }
        return nil
    }
    return res
}
// GetRecord gets the record property value. The record property
// returns a ItemArtifactsItemVersionsItemContractExecutePostRequestBody_recordable when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostRequestBody) GetRecord()(ItemArtifactsItemVersionsItemContractExecutePostRequestBody_recordable) {
    return m.record
}
// Serialize serializes information the current object
func (m *ItemArtifactsItemVersionsItemContractExecutePostRequestBody) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteObjectValue("record", m.GetRecord())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteAdditionalData(m.GetAdditionalData())
        if err != nil {
            return err
        }
    }
    return nil
}
// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *ItemArtifactsItemVersionsItemContractExecutePostRequestBody) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetRecord sets the record property value. The record property
func (m *ItemArtifactsItemVersionsItemContractExecutePostRequestBody) SetRecord(value ItemArtifactsItemVersionsItemContractExecutePostRequestBody_recordable)() {
    m.record = value
}
type ItemArtifactsItemVersionsItemContractExecutePostRequestBodyable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetRecord()(ItemArtifactsItemVersionsItemContractExecutePostRequestBody_recordable)
    SetRecord(value ItemArtifactsItemVersionsItemContractExecutePostRequestBody_recordable)()
}
