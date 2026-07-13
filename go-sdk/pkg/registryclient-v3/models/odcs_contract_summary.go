package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// OdcsContractSummary summary of an ODCS contract.
type OdcsContractSummary struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The contract artifact ID.
    contractId *string
    // The contract display name.
    name *string
}
// NewOdcsContractSummary instantiates a new OdcsContractSummary and sets the default values.
func NewOdcsContractSummary()(*OdcsContractSummary) {
    m := &OdcsContractSummary{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateOdcsContractSummaryFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateOdcsContractSummaryFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewOdcsContractSummary(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *OdcsContractSummary) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetContractId gets the contractId property value. The contract artifact ID.
// returns a *string when successful
func (m *OdcsContractSummary) GetContractId()(*string) {
    return m.contractId
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *OdcsContractSummary) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["contractId"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetContractId(val)
        }
        return nil
    }
    res["name"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetName(val)
        }
        return nil
    }
    return res
}
// GetName gets the name property value. The contract display name.
// returns a *string when successful
func (m *OdcsContractSummary) GetName()(*string) {
    return m.name
}
// Serialize serializes information the current object
func (m *OdcsContractSummary) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteStringValue("contractId", m.GetContractId())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("name", m.GetName())
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
func (m *OdcsContractSummary) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetContractId sets the contractId property value. The contract artifact ID.
func (m *OdcsContractSummary) SetContractId(value *string)() {
    m.contractId = value
}
// SetName sets the name property value. The contract display name.
func (m *OdcsContractSummary) SetName(value *string)() {
    m.name = value
}
type OdcsContractSummaryable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetContractId()(*string)
    GetName()(*string)
    SetContractId(value *string)()
    SetName(value *string)()
}
