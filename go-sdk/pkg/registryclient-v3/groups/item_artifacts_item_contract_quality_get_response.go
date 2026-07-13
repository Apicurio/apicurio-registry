package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type ItemArtifactsItemContractQualityGetResponse struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The completeness property
    completeness *float32
    // The compliance property
    compliance *float32
    // The overall property
    overall *float32
    // The stability property
    stability *float32
}
// NewItemArtifactsItemContractQualityGetResponse instantiates a new ItemArtifactsItemContractQualityGetResponse and sets the default values.
func NewItemArtifactsItemContractQualityGetResponse()(*ItemArtifactsItemContractQualityGetResponse) {
    m := &ItemArtifactsItemContractQualityGetResponse{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateItemArtifactsItemContractQualityGetResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemContractQualityGetResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemContractQualityGetResponse(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ItemArtifactsItemContractQualityGetResponse) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetCompleteness gets the completeness property value. The completeness property
// returns a *float32 when successful
func (m *ItemArtifactsItemContractQualityGetResponse) GetCompleteness()(*float32) {
    return m.completeness
}
// GetCompliance gets the compliance property value. The compliance property
// returns a *float32 when successful
func (m *ItemArtifactsItemContractQualityGetResponse) GetCompliance()(*float32) {
    return m.compliance
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ItemArtifactsItemContractQualityGetResponse) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["completeness"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetFloat32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetCompleteness(val)
        }
        return nil
    }
    res["compliance"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetFloat32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetCompliance(val)
        }
        return nil
    }
    res["overall"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetFloat32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetOverall(val)
        }
        return nil
    }
    res["stability"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetFloat32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetStability(val)
        }
        return nil
    }
    return res
}
// GetOverall gets the overall property value. The overall property
// returns a *float32 when successful
func (m *ItemArtifactsItemContractQualityGetResponse) GetOverall()(*float32) {
    return m.overall
}
// GetStability gets the stability property value. The stability property
// returns a *float32 when successful
func (m *ItemArtifactsItemContractQualityGetResponse) GetStability()(*float32) {
    return m.stability
}
// Serialize serializes information the current object
func (m *ItemArtifactsItemContractQualityGetResponse) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteFloat32Value("completeness", m.GetCompleteness())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteFloat32Value("compliance", m.GetCompliance())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteFloat32Value("overall", m.GetOverall())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteFloat32Value("stability", m.GetStability())
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
func (m *ItemArtifactsItemContractQualityGetResponse) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetCompleteness sets the completeness property value. The completeness property
func (m *ItemArtifactsItemContractQualityGetResponse) SetCompleteness(value *float32)() {
    m.completeness = value
}
// SetCompliance sets the compliance property value. The compliance property
func (m *ItemArtifactsItemContractQualityGetResponse) SetCompliance(value *float32)() {
    m.compliance = value
}
// SetOverall sets the overall property value. The overall property
func (m *ItemArtifactsItemContractQualityGetResponse) SetOverall(value *float32)() {
    m.overall = value
}
// SetStability sets the stability property value. The stability property
func (m *ItemArtifactsItemContractQualityGetResponse) SetStability(value *float32)() {
    m.stability = value
}
type ItemArtifactsItemContractQualityGetResponseable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetCompleteness()(*float32)
    GetCompliance()(*float32)
    GetOverall()(*float32)
    GetStability()(*float32)
    SetCompleteness(value *float32)()
    SetCompliance(value *float32)()
    SetOverall(value *float32)()
    SetStability(value *float32)()
}
