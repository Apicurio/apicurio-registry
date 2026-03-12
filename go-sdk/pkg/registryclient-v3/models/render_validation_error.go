package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// RenderValidationError a validation error encountered when validating template variables.
type RenderValidationError struct {
	// The actual type that was provided.
	actualType *string
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The expected type of the variable.
	expectedType *string
	// A description of the validation error.
	message *string
	// The name of the variable that failed validation.
	variableName *string
}

// NewRenderValidationError instantiates a new RenderValidationError and sets the default values.
func NewRenderValidationError() *RenderValidationError {
	m := &RenderValidationError{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateRenderValidationErrorFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateRenderValidationErrorFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewRenderValidationError(), nil
}

// GetActualType gets the actualType property value. The actual type that was provided.
// returns a *string when successful
func (m *RenderValidationError) GetActualType() *string {
	return m.actualType
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *RenderValidationError) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetExpectedType gets the expectedType property value. The expected type of the variable.
// returns a *string when successful
func (m *RenderValidationError) GetExpectedType() *string {
	return m.expectedType
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *RenderValidationError) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["actualType"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetActualType(val)
		}
		return nil
	}
	res["expectedType"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetExpectedType(val)
		}
		return nil
	}
	res["message"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMessage(val)
		}
		return nil
	}
	res["variableName"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetVariableName(val)
		}
		return nil
	}
	return res
}

// GetMessage gets the message property value. A description of the validation error.
// returns a *string when successful
func (m *RenderValidationError) GetMessage() *string {
	return m.message
}

// GetVariableName gets the variableName property value. The name of the variable that failed validation.
// returns a *string when successful
func (m *RenderValidationError) GetVariableName() *string {
	return m.variableName
}

// Serialize serializes information the current object
func (m *RenderValidationError) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("actualType", m.GetActualType())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("expectedType", m.GetExpectedType())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("message", m.GetMessage())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("variableName", m.GetVariableName())
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

// SetActualType sets the actualType property value. The actual type that was provided.
func (m *RenderValidationError) SetActualType(value *string) {
	m.actualType = value
}

// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *RenderValidationError) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetExpectedType sets the expectedType property value. The expected type of the variable.
func (m *RenderValidationError) SetExpectedType(value *string) {
	m.expectedType = value
}

// SetMessage sets the message property value. A description of the validation error.
func (m *RenderValidationError) SetMessage(value *string) {
	m.message = value
}

// SetVariableName sets the variableName property value. The name of the variable that failed validation.
func (m *RenderValidationError) SetVariableName(value *string) {
	m.variableName = value
}

type RenderValidationErrorable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetActualType() *string
	GetExpectedType() *string
	GetMessage() *string
	GetVariableName() *string
	SetActualType(value *string)
	SetExpectedType(value *string)
	SetMessage(value *string)
	SetVariableName(value *string)
}
