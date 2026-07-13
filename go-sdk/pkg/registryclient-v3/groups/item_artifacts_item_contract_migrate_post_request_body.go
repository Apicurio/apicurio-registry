package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type ItemArtifactsItemContractMigratePostRequestBody struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The fromVersion property
    fromVersion *string
    // The record property
    record ItemArtifactsItemContractMigratePostRequestBody_recordable
    // The toVersion property
    toVersion *string
}
// NewItemArtifactsItemContractMigratePostRequestBody instantiates a new ItemArtifactsItemContractMigratePostRequestBody and sets the default values.
func NewItemArtifactsItemContractMigratePostRequestBody()(*ItemArtifactsItemContractMigratePostRequestBody) {
    m := &ItemArtifactsItemContractMigratePostRequestBody{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateItemArtifactsItemContractMigratePostRequestBodyFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemContractMigratePostRequestBodyFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemContractMigratePostRequestBody(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ItemArtifactsItemContractMigratePostRequestBody) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ItemArtifactsItemContractMigratePostRequestBody) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["fromVersion"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetFromVersion(val)
        }
        return nil
    }
    res["record"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateItemArtifactsItemContractMigratePostRequestBody_recordFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetRecord(val.(ItemArtifactsItemContractMigratePostRequestBody_recordable))
        }
        return nil
    }
    res["toVersion"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetToVersion(val)
        }
        return nil
    }
    return res
}
// GetFromVersion gets the fromVersion property value. The fromVersion property
// returns a *string when successful
func (m *ItemArtifactsItemContractMigratePostRequestBody) GetFromVersion()(*string) {
    return m.fromVersion
}
// GetRecord gets the record property value. The record property
// returns a ItemArtifactsItemContractMigratePostRequestBody_recordable when successful
func (m *ItemArtifactsItemContractMigratePostRequestBody) GetRecord()(ItemArtifactsItemContractMigratePostRequestBody_recordable) {
    return m.record
}
// GetToVersion gets the toVersion property value. The toVersion property
// returns a *string when successful
func (m *ItemArtifactsItemContractMigratePostRequestBody) GetToVersion()(*string) {
    return m.toVersion
}
// Serialize serializes information the current object
func (m *ItemArtifactsItemContractMigratePostRequestBody) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteStringValue("fromVersion", m.GetFromVersion())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteObjectValue("record", m.GetRecord())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("toVersion", m.GetToVersion())
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
func (m *ItemArtifactsItemContractMigratePostRequestBody) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetFromVersion sets the fromVersion property value. The fromVersion property
func (m *ItemArtifactsItemContractMigratePostRequestBody) SetFromVersion(value *string)() {
    m.fromVersion = value
}
// SetRecord sets the record property value. The record property
func (m *ItemArtifactsItemContractMigratePostRequestBody) SetRecord(value ItemArtifactsItemContractMigratePostRequestBody_recordable)() {
    m.record = value
}
// SetToVersion sets the toVersion property value. The toVersion property
func (m *ItemArtifactsItemContractMigratePostRequestBody) SetToVersion(value *string)() {
    m.toVersion = value
}
type ItemArtifactsItemContractMigratePostRequestBodyable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetFromVersion()(*string)
    GetRecord()(ItemArtifactsItemContractMigratePostRequestBody_recordable)
    GetToVersion()(*string)
    SetFromVersion(value *string)()
    SetRecord(value ItemArtifactsItemContractMigratePostRequestBody_recordable)()
    SetToVersion(value *string)()
}
