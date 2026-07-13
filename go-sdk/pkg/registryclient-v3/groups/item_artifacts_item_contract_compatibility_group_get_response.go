package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type ItemArtifactsItemContractCompatibilityGroupGetResponse struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The compatibilityGroup property
    compatibilityGroup *string
}
// NewItemArtifactsItemContractCompatibilityGroupGetResponse instantiates a new ItemArtifactsItemContractCompatibilityGroupGetResponse and sets the default values.
func NewItemArtifactsItemContractCompatibilityGroupGetResponse()(*ItemArtifactsItemContractCompatibilityGroupGetResponse) {
    m := &ItemArtifactsItemContractCompatibilityGroupGetResponse{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateItemArtifactsItemContractCompatibilityGroupGetResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemContractCompatibilityGroupGetResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemContractCompatibilityGroupGetResponse(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ItemArtifactsItemContractCompatibilityGroupGetResponse) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetCompatibilityGroup gets the compatibilityGroup property value. The compatibilityGroup property
// returns a *string when successful
func (m *ItemArtifactsItemContractCompatibilityGroupGetResponse) GetCompatibilityGroup()(*string) {
    return m.compatibilityGroup
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ItemArtifactsItemContractCompatibilityGroupGetResponse) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["compatibilityGroup"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetCompatibilityGroup(val)
        }
        return nil
    }
    return res
}
// Serialize serializes information the current object
func (m *ItemArtifactsItemContractCompatibilityGroupGetResponse) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteStringValue("compatibilityGroup", m.GetCompatibilityGroup())
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
func (m *ItemArtifactsItemContractCompatibilityGroupGetResponse) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetCompatibilityGroup sets the compatibilityGroup property value. The compatibilityGroup property
func (m *ItemArtifactsItemContractCompatibilityGroupGetResponse) SetCompatibilityGroup(value *string)() {
    m.compatibilityGroup = value
}
type ItemArtifactsItemContractCompatibilityGroupGetResponseable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetCompatibilityGroup()(*string)
    SetCompatibilityGroup(value *string)()
}
