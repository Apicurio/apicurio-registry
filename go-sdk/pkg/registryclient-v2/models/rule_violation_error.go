package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// RuleViolationError all error responses, whether `4xx` or `5xx` will include one of these as the responsebody.
type RuleViolationError struct {
    Error
    // List of rule violation causes.
    causes []RuleViolationCauseable
}
// NewRuleViolationError instantiates a new RuleViolationError and sets the default values.
func NewRuleViolationError()(*RuleViolationError) {
    m := &RuleViolationError{
        Error: *NewError(),
    }
    return m
}
// CreateRuleViolationErrorFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateRuleViolationErrorFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewRuleViolationError(), nil
}
// GetCauses gets the causes property value. List of rule violation causes.
func (m *RuleViolationError) GetCauses()([]RuleViolationCauseable) {
    return m.causes
}
// GetFieldDeserializers the deserialization information for the current model
func (m *RuleViolationError) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := m.Error.GetFieldDeserializers()
    res["causes"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetCollectionOfObjectValues(CreateRuleViolationCauseFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            res := make([]RuleViolationCauseable, len(val))
            for i, v := range val {
                if v != nil {
                    res[i] = v.(RuleViolationCauseable)
                }
            }
            m.SetCauses(res)
        }
        return nil
    }
    return res
}
// Serialize serializes information the current object
func (m *RuleViolationError) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    err := m.Error.Serialize(writer)
    if err != nil {
        return err
    }
    if m.GetCauses() != nil {
        cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetCauses()))
        for i, v := range m.GetCauses() {
            if v != nil {
                cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
            }
        }
        err = writer.WriteCollectionOfObjectValues("causes", cast)
        if err != nil {
            return err
        }
    }
    return nil
}
// SetCauses sets the causes property value. List of rule violation causes.
func (m *RuleViolationError) SetCauses(value []RuleViolationCauseable)() {
    m.causes = value
}
// RuleViolationErrorable 
type RuleViolationErrorable interface {
    Errorable
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetCauses()([]RuleViolationCauseable)
    SetCauses(value []RuleViolationCauseable)()
}
