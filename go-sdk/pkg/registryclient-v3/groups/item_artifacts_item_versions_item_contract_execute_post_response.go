package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type ItemArtifactsItemVersionsItemContractExecutePostResponse struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The executedRules property
    executedRules *int32
    // The failedRules property
    failedRules *int32
    // The passed property
    passed *bool
    // The transformedRecord property
    transformedRecord ItemArtifactsItemVersionsItemContractExecutePostResponse_transformedRecordable
    // The violations property
    violations []ItemArtifactsItemVersionsItemContractExecutePostResponse_violationsable
}
// NewItemArtifactsItemVersionsItemContractExecutePostResponse instantiates a new ItemArtifactsItemVersionsItemContractExecutePostResponse and sets the default values.
func NewItemArtifactsItemVersionsItemContractExecutePostResponse()(*ItemArtifactsItemVersionsItemContractExecutePostResponse) {
    m := &ItemArtifactsItemVersionsItemContractExecutePostResponse{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateItemArtifactsItemVersionsItemContractExecutePostResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemVersionsItemContractExecutePostResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemVersionsItemContractExecutePostResponse(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetExecutedRules gets the executedRules property value. The executedRules property
// returns a *int32 when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) GetExecutedRules()(*int32) {
    return m.executedRules
}
// GetFailedRules gets the failedRules property value. The failedRules property
// returns a *int32 when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) GetFailedRules()(*int32) {
    return m.failedRules
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["executedRules"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetExecutedRules(val)
        }
        return nil
    }
    res["failedRules"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetFailedRules(val)
        }
        return nil
    }
    res["passed"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetBoolValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetPassed(val)
        }
        return nil
    }
    res["transformedRecord"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateItemArtifactsItemVersionsItemContractExecutePostResponse_transformedRecordFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetTransformedRecord(val.(ItemArtifactsItemVersionsItemContractExecutePostResponse_transformedRecordable))
        }
        return nil
    }
    res["violations"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetCollectionOfObjectValues(CreateItemArtifactsItemVersionsItemContractExecutePostResponse_violationsFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            res := make([]ItemArtifactsItemVersionsItemContractExecutePostResponse_violationsable, len(val))
            for i, v := range val {
                if v != nil {
                    res[i] = v.(ItemArtifactsItemVersionsItemContractExecutePostResponse_violationsable)
                }
            }
            m.SetViolations(res)
        }
        return nil
    }
    return res
}
// GetPassed gets the passed property value. The passed property
// returns a *bool when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) GetPassed()(*bool) {
    return m.passed
}
// GetTransformedRecord gets the transformedRecord property value. The transformedRecord property
// returns a ItemArtifactsItemVersionsItemContractExecutePostResponse_transformedRecordable when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) GetTransformedRecord()(ItemArtifactsItemVersionsItemContractExecutePostResponse_transformedRecordable) {
    return m.transformedRecord
}
// GetViolations gets the violations property value. The violations property
// returns a []ItemArtifactsItemVersionsItemContractExecutePostResponse_violationsable when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) GetViolations()([]ItemArtifactsItemVersionsItemContractExecutePostResponse_violationsable) {
    return m.violations
}
// Serialize serializes information the current object
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteInt32Value("executedRules", m.GetExecutedRules())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteInt32Value("failedRules", m.GetFailedRules())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteBoolValue("passed", m.GetPassed())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteObjectValue("transformedRecord", m.GetTransformedRecord())
        if err != nil {
            return err
        }
    }
    if m.GetViolations() != nil {
        cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetViolations()))
        for i, v := range m.GetViolations() {
            if v != nil {
                cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
            }
        }
        err := writer.WriteCollectionOfObjectValues("violations", cast)
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
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetExecutedRules sets the executedRules property value. The executedRules property
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) SetExecutedRules(value *int32)() {
    m.executedRules = value
}
// SetFailedRules sets the failedRules property value. The failedRules property
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) SetFailedRules(value *int32)() {
    m.failedRules = value
}
// SetPassed sets the passed property value. The passed property
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) SetPassed(value *bool)() {
    m.passed = value
}
// SetTransformedRecord sets the transformedRecord property value. The transformedRecord property
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) SetTransformedRecord(value ItemArtifactsItemVersionsItemContractExecutePostResponse_transformedRecordable)() {
    m.transformedRecord = value
}
// SetViolations sets the violations property value. The violations property
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse) SetViolations(value []ItemArtifactsItemVersionsItemContractExecutePostResponse_violationsable)() {
    m.violations = value
}
type ItemArtifactsItemVersionsItemContractExecutePostResponseable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetExecutedRules()(*int32)
    GetFailedRules()(*int32)
    GetPassed()(*bool)
    GetTransformedRecord()(ItemArtifactsItemVersionsItemContractExecutePostResponse_transformedRecordable)
    GetViolations()([]ItemArtifactsItemVersionsItemContractExecutePostResponse_violationsable)
    SetExecutedRules(value *int32)()
    SetFailedRules(value *int32)()
    SetPassed(value *bool)()
    SetTransformedRecord(value ItemArtifactsItemVersionsItemContractExecutePostResponse_transformedRecordable)()
    SetViolations(value []ItemArtifactsItemVersionsItemContractExecutePostResponse_violationsable)()
}
