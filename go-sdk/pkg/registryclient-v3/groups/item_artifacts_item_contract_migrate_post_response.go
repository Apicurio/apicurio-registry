package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type ItemArtifactsItemContractMigratePostResponse struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The executedRules property
    executedRules *int32
    // The failedRules property
    failedRules *int32
    // The passed property
    passed *bool
    // The transformedRecord property
    transformedRecord ItemArtifactsItemContractMigratePostResponse_transformedRecordable
    // The violations property
    violations []ItemArtifactsItemContractMigratePostResponse_violationsable
}
// NewItemArtifactsItemContractMigratePostResponse instantiates a new ItemArtifactsItemContractMigratePostResponse and sets the default values.
func NewItemArtifactsItemContractMigratePostResponse()(*ItemArtifactsItemContractMigratePostResponse) {
    m := &ItemArtifactsItemContractMigratePostResponse{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateItemArtifactsItemContractMigratePostResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemContractMigratePostResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemContractMigratePostResponse(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ItemArtifactsItemContractMigratePostResponse) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetExecutedRules gets the executedRules property value. The executedRules property
// returns a *int32 when successful
func (m *ItemArtifactsItemContractMigratePostResponse) GetExecutedRules()(*int32) {
    return m.executedRules
}
// GetFailedRules gets the failedRules property value. The failedRules property
// returns a *int32 when successful
func (m *ItemArtifactsItemContractMigratePostResponse) GetFailedRules()(*int32) {
    return m.failedRules
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ItemArtifactsItemContractMigratePostResponse) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
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
        val, err := n.GetObjectValue(CreateItemArtifactsItemContractMigratePostResponse_transformedRecordFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetTransformedRecord(val.(ItemArtifactsItemContractMigratePostResponse_transformedRecordable))
        }
        return nil
    }
    res["violations"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetCollectionOfObjectValues(CreateItemArtifactsItemContractMigratePostResponse_violationsFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            res := make([]ItemArtifactsItemContractMigratePostResponse_violationsable, len(val))
            for i, v := range val {
                if v != nil {
                    res[i] = v.(ItemArtifactsItemContractMigratePostResponse_violationsable)
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
func (m *ItemArtifactsItemContractMigratePostResponse) GetPassed()(*bool) {
    return m.passed
}
// GetTransformedRecord gets the transformedRecord property value. The transformedRecord property
// returns a ItemArtifactsItemContractMigratePostResponse_transformedRecordable when successful
func (m *ItemArtifactsItemContractMigratePostResponse) GetTransformedRecord()(ItemArtifactsItemContractMigratePostResponse_transformedRecordable) {
    return m.transformedRecord
}
// GetViolations gets the violations property value. The violations property
// returns a []ItemArtifactsItemContractMigratePostResponse_violationsable when successful
func (m *ItemArtifactsItemContractMigratePostResponse) GetViolations()([]ItemArtifactsItemContractMigratePostResponse_violationsable) {
    return m.violations
}
// Serialize serializes information the current object
func (m *ItemArtifactsItemContractMigratePostResponse) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
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
func (m *ItemArtifactsItemContractMigratePostResponse) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetExecutedRules sets the executedRules property value. The executedRules property
func (m *ItemArtifactsItemContractMigratePostResponse) SetExecutedRules(value *int32)() {
    m.executedRules = value
}
// SetFailedRules sets the failedRules property value. The failedRules property
func (m *ItemArtifactsItemContractMigratePostResponse) SetFailedRules(value *int32)() {
    m.failedRules = value
}
// SetPassed sets the passed property value. The passed property
func (m *ItemArtifactsItemContractMigratePostResponse) SetPassed(value *bool)() {
    m.passed = value
}
// SetTransformedRecord sets the transformedRecord property value. The transformedRecord property
func (m *ItemArtifactsItemContractMigratePostResponse) SetTransformedRecord(value ItemArtifactsItemContractMigratePostResponse_transformedRecordable)() {
    m.transformedRecord = value
}
// SetViolations sets the violations property value. The violations property
func (m *ItemArtifactsItemContractMigratePostResponse) SetViolations(value []ItemArtifactsItemContractMigratePostResponse_violationsable)() {
    m.violations = value
}
type ItemArtifactsItemContractMigratePostResponseable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetExecutedRules()(*int32)
    GetFailedRules()(*int32)
    GetPassed()(*bool)
    GetTransformedRecord()(ItemArtifactsItemContractMigratePostResponse_transformedRecordable)
    GetViolations()([]ItemArtifactsItemContractMigratePostResponse_violationsable)
    SetExecutedRules(value *int32)()
    SetFailedRules(value *int32)()
    SetPassed(value *bool)()
    SetTransformedRecord(value ItemArtifactsItemContractMigratePostResponse_transformedRecordable)()
    SetViolations(value []ItemArtifactsItemContractMigratePostResponse_violationsable)()
}
