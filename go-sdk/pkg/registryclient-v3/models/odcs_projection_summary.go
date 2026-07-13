package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// OdcsProjectionSummary summary of the projection performed when an ODCS contract is applied.
type OdcsProjectionSummary struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // Number of contract.* labels set on the schema artifact.
    labelsApplied *int32
    // Number of CEL quality rules projected onto the schema artifact.
    rulesApplied *int32
    // Number of field-tag.* labels set on the schema artifact version.
    tagsApplied *int32
    // Any warnings encountered during projection.
    warnings []string
}
// NewOdcsProjectionSummary instantiates a new OdcsProjectionSummary and sets the default values.
func NewOdcsProjectionSummary()(*OdcsProjectionSummary) {
    m := &OdcsProjectionSummary{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateOdcsProjectionSummaryFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateOdcsProjectionSummaryFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewOdcsProjectionSummary(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *OdcsProjectionSummary) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *OdcsProjectionSummary) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["labelsApplied"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetLabelsApplied(val)
        }
        return nil
    }
    res["rulesApplied"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetRulesApplied(val)
        }
        return nil
    }
    res["tagsApplied"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetTagsApplied(val)
        }
        return nil
    }
    res["warnings"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetCollectionOfPrimitiveValues("string")
        if err != nil {
            return err
        }
        if val != nil {
            res := make([]string, len(val))
            for i, v := range val {
                if v != nil {
                    res[i] = *(v.(*string))
                }
            }
            m.SetWarnings(res)
        }
        return nil
    }
    return res
}
// GetLabelsApplied gets the labelsApplied property value. Number of contract.* labels set on the schema artifact.
// returns a *int32 when successful
func (m *OdcsProjectionSummary) GetLabelsApplied()(*int32) {
    return m.labelsApplied
}
// GetRulesApplied gets the rulesApplied property value. Number of CEL quality rules projected onto the schema artifact.
// returns a *int32 when successful
func (m *OdcsProjectionSummary) GetRulesApplied()(*int32) {
    return m.rulesApplied
}
// GetTagsApplied gets the tagsApplied property value. Number of field-tag.* labels set on the schema artifact version.
// returns a *int32 when successful
func (m *OdcsProjectionSummary) GetTagsApplied()(*int32) {
    return m.tagsApplied
}
// GetWarnings gets the warnings property value. Any warnings encountered during projection.
// returns a []string when successful
func (m *OdcsProjectionSummary) GetWarnings()([]string) {
    return m.warnings
}
// Serialize serializes information the current object
func (m *OdcsProjectionSummary) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteInt32Value("labelsApplied", m.GetLabelsApplied())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteInt32Value("rulesApplied", m.GetRulesApplied())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteInt32Value("tagsApplied", m.GetTagsApplied())
        if err != nil {
            return err
        }
    }
    if m.GetWarnings() != nil {
        err := writer.WriteCollectionOfStringValues("warnings", m.GetWarnings())
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
func (m *OdcsProjectionSummary) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetLabelsApplied sets the labelsApplied property value. Number of contract.* labels set on the schema artifact.
func (m *OdcsProjectionSummary) SetLabelsApplied(value *int32)() {
    m.labelsApplied = value
}
// SetRulesApplied sets the rulesApplied property value. Number of CEL quality rules projected onto the schema artifact.
func (m *OdcsProjectionSummary) SetRulesApplied(value *int32)() {
    m.rulesApplied = value
}
// SetTagsApplied sets the tagsApplied property value. Number of field-tag.* labels set on the schema artifact version.
func (m *OdcsProjectionSummary) SetTagsApplied(value *int32)() {
    m.tagsApplied = value
}
// SetWarnings sets the warnings property value. Any warnings encountered during projection.
func (m *OdcsProjectionSummary) SetWarnings(value []string)() {
    m.warnings = value
}
type OdcsProjectionSummaryable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetLabelsApplied()(*int32)
    GetRulesApplied()(*int32)
    GetTagsApplied()(*int32)
    GetWarnings()([]string)
    SetLabelsApplied(value *int32)()
    SetRulesApplied(value *int32)()
    SetTagsApplied(value *int32)()
    SetWarnings(value []string)()
}
