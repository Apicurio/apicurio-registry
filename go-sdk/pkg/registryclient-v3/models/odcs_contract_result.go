package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// OdcsContractResult result of submitting or updating an ODCS contract.
type OdcsContractResult struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The contract artifact ID.
    contractId *string
    // Summary of the projection performed when an ODCS contract is applied.
    projection OdcsProjectionSummaryable
    // The ODCS contract version.
    version *string
}
// NewOdcsContractResult instantiates a new OdcsContractResult and sets the default values.
func NewOdcsContractResult()(*OdcsContractResult) {
    m := &OdcsContractResult{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateOdcsContractResultFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateOdcsContractResultFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewOdcsContractResult(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *OdcsContractResult) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetContractId gets the contractId property value. The contract artifact ID.
// returns a *string when successful
func (m *OdcsContractResult) GetContractId()(*string) {
    return m.contractId
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *OdcsContractResult) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
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
    res["projection"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateOdcsProjectionSummaryFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetProjection(val.(OdcsProjectionSummaryable))
        }
        return nil
    }
    res["version"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetVersion(val)
        }
        return nil
    }
    return res
}
// GetProjection gets the projection property value. Summary of the projection performed when an ODCS contract is applied.
// returns a OdcsProjectionSummaryable when successful
func (m *OdcsContractResult) GetProjection()(OdcsProjectionSummaryable) {
    return m.projection
}
// GetVersion gets the version property value. The ODCS contract version.
// returns a *string when successful
func (m *OdcsContractResult) GetVersion()(*string) {
    return m.version
}
// Serialize serializes information the current object
func (m *OdcsContractResult) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteStringValue("contractId", m.GetContractId())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteObjectValue("projection", m.GetProjection())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("version", m.GetVersion())
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
func (m *OdcsContractResult) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetContractId sets the contractId property value. The contract artifact ID.
func (m *OdcsContractResult) SetContractId(value *string)() {
    m.contractId = value
}
// SetProjection sets the projection property value. Summary of the projection performed when an ODCS contract is applied.
func (m *OdcsContractResult) SetProjection(value OdcsProjectionSummaryable)() {
    m.projection = value
}
// SetVersion sets the version property value. The ODCS contract version.
func (m *OdcsContractResult) SetVersion(value *string)() {
    m.version = value
}
type OdcsContractResultable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetContractId()(*string)
    GetProjection()(OdcsProjectionSummaryable)
    GetVersion()(*string)
    SetContractId(value *string)()
    SetProjection(value OdcsProjectionSummaryable)()
    SetVersion(value *string)()
}
