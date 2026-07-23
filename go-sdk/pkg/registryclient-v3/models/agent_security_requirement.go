package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentSecurityRequirement represents a security requirement for an A2A agent.
type AgentSecurityRequirement struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Map of security scheme names to their required scopes.
	schemes AgentSecurityRequirement_schemesable
}

// NewAgentSecurityRequirement instantiates a new AgentSecurityRequirement and sets the default values.
func NewAgentSecurityRequirement() *AgentSecurityRequirement {
	m := &AgentSecurityRequirement{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAgentSecurityRequirementFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentSecurityRequirementFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAgentSecurityRequirement(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentSecurityRequirement) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentSecurityRequirement) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["schemes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateAgentSecurityRequirement_schemesFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSchemes(val.(AgentSecurityRequirement_schemesable))
		}
		return nil
	}
	return res
}

// GetSchemes gets the schemes property value. Map of security scheme names to their required scopes.
// returns a AgentSecurityRequirement_schemesable when successful
func (m *AgentSecurityRequirement) GetSchemes() AgentSecurityRequirement_schemesable {
	return m.schemes
}

// Serialize serializes information the current object
func (m *AgentSecurityRequirement) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
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
func (m *AgentSecurityRequirement) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetSchemes sets the schemes property value. Map of security scheme names to their required scopes.
func (m *AgentSecurityRequirement) SetSchemes(value AgentSecurityRequirement_schemesable) {
	m.schemes = value
}

type AgentSecurityRequirementable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetSchemes() AgentSecurityRequirement_schemesable
	SetSchemes(value AgentSecurityRequirement_schemesable)
}
