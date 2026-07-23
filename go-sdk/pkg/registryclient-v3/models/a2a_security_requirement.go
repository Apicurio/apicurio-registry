package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// A2aSecurityRequirement represents a security requirement for an A2A agent.
type A2aSecurityRequirement struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Map of security scheme names to their required scopes.
	schemes A2aSecurityRequirement_schemesable
}

// NewA2aSecurityRequirement instantiates a new A2aSecurityRequirement and sets the default values.
func NewA2aSecurityRequirement() *A2aSecurityRequirement {
	m := &A2aSecurityRequirement{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateA2aSecurityRequirementFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateA2aSecurityRequirementFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewA2aSecurityRequirement(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *A2aSecurityRequirement) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *A2aSecurityRequirement) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["schemes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateA2aSecurityRequirement_schemesFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSchemes(val.(A2aSecurityRequirement_schemesable))
		}
		return nil
	}
	return res
}

// GetSchemes gets the schemes property value. Map of security scheme names to their required scopes.
// returns a A2aSecurityRequirement_schemesable when successful
func (m *A2aSecurityRequirement) GetSchemes() A2aSecurityRequirement_schemesable {
	return m.schemes
}

// Serialize serializes information the current object
func (m *A2aSecurityRequirement) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteObjectValue("schemes", m.GetSchemes())
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
func (m *A2aSecurityRequirement) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetSchemes sets the schemes property value. Map of security scheme names to their required scopes.
func (m *A2aSecurityRequirement) SetSchemes(value A2aSecurityRequirement_schemesable) {
	m.schemes = value
}

type A2aSecurityRequirementable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetSchemes() A2aSecurityRequirement_schemesable
	SetSchemes(value A2aSecurityRequirement_schemesable)
}
