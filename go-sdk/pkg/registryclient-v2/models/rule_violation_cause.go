package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// RuleViolationCause
type RuleViolationCause struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The context property
	context *string
	// The description property
	description *string
}

// NewRuleViolationCause instantiates a new RuleViolationCause and sets the default values.
func NewRuleViolationCause() *RuleViolationCause {
	m := &RuleViolationCause{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateRuleViolationCauseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateRuleViolationCauseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewRuleViolationCause(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *RuleViolationCause) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetContext gets the context property value. The context property
func (m *RuleViolationCause) GetContext() *string {
	return m.context
}

// GetDescription gets the description property value. The description property
func (m *RuleViolationCause) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
func (m *RuleViolationCause) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["context"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetContext(val)
		}
		return nil
	}
	res["description"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDescription(val)
		}
		return nil
	}
	return res
}

// Serialize serializes information the current object
func (m *RuleViolationCause) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("context", m.GetContext())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("description", m.GetDescription())
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
func (m *RuleViolationCause) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetContext sets the context property value. The context property
func (m *RuleViolationCause) SetContext(value *string) {
	m.context = value
}

// SetDescription sets the description property value. The description property
func (m *RuleViolationCause) SetDescription(value *string) {
	m.description = value
}

// RuleViolationCauseable
type RuleViolationCauseable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetContext() *string
	GetDescription() *string
	SetContext(value *string)
	SetDescription(value *string)
}
