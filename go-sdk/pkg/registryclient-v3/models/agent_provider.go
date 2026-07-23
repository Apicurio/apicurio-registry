package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentProvider represents the provider/organization that created an A2A agent.
type AgentProvider struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The name of the organization.
	organization *string
	// URL of the organization.
	url *string
}

// NewAgentProvider instantiates a new AgentProvider and sets the default values.
func NewAgentProvider() *AgentProvider {
	m := &AgentProvider{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAgentProviderFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentProviderFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAgentProvider(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentProvider) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentProvider) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["organization"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetOrganization(val)
		}
		return nil
	}
	res["url"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetUrl(val)
		}
		return nil
	}
	return res
}

// GetOrganization gets the organization property value. The name of the organization.
// returns a *string when successful
func (m *AgentProvider) GetOrganization() *string {
	return m.organization
}

// GetUrl gets the url property value. URL of the organization.
// returns a *string when successful
func (m *AgentProvider) GetUrl() *string {
	return m.url
}

// Serialize serializes information the current object
func (m *AgentProvider) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("organization", m.GetOrganization())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("url", m.GetUrl())
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
func (m *AgentProvider) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetOrganization sets the organization property value. The name of the organization.
func (m *AgentProvider) SetOrganization(value *string) {
	m.organization = value
}

// SetUrl sets the url property value. URL of the organization.
func (m *AgentProvider) SetUrl(value *string) {
	m.url = value
}

type AgentProviderable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetOrganization() *string
	GetUrl() *string
	SetOrganization(value *string)
	SetUrl(value *string)
}
