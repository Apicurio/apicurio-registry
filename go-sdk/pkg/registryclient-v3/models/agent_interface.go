package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentInterface represents an interface endpoint for an A2A agent.
type AgentInterface struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The protocol binding (e.g., http+json).
	protocolBinding *string
	// The protocol version.
	protocolVersion *string
	// Optional tenant identifier.
	tenant *string
	// The URL of the interface endpoint.
	url *string
}

// NewAgentInterface instantiates a new AgentInterface and sets the default values.
func NewAgentInterface() *AgentInterface {
	m := &AgentInterface{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAgentInterfaceFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentInterfaceFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAgentInterface(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentInterface) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentInterface) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["protocolBinding"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetProtocolBinding(val)
		}
		return nil
	}
	res["protocolVersion"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetProtocolVersion(val)
		}
		return nil
	}
	res["tenant"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTenant(val)
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

// GetProtocolBinding gets the protocolBinding property value. The protocol binding (e.g., http+json).
// returns a *string when successful
func (m *AgentInterface) GetProtocolBinding() *string {
	return m.protocolBinding
}

// GetProtocolVersion gets the protocolVersion property value. The protocol version.
// returns a *string when successful
func (m *AgentInterface) GetProtocolVersion() *string {
	return m.protocolVersion
}

// GetTenant gets the tenant property value. Optional tenant identifier.
// returns a *string when successful
func (m *AgentInterface) GetTenant() *string {
	return m.tenant
}

// GetUrl gets the url property value. The URL of the interface endpoint.
// returns a *string when successful
func (m *AgentInterface) GetUrl() *string {
	return m.url
}

// Serialize serializes information the current object
func (m *AgentInterface) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("protocolBinding", m.GetProtocolBinding())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("protocolVersion", m.GetProtocolVersion())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("tenant", m.GetTenant())
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
func (m *AgentInterface) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetProtocolBinding sets the protocolBinding property value. The protocol binding (e.g., http+json).
func (m *AgentInterface) SetProtocolBinding(value *string) {
	m.protocolBinding = value
}

// SetProtocolVersion sets the protocolVersion property value. The protocol version.
func (m *AgentInterface) SetProtocolVersion(value *string) {
	m.protocolVersion = value
}

// SetTenant sets the tenant property value. Optional tenant identifier.
func (m *AgentInterface) SetTenant(value *string) {
	m.tenant = value
}

// SetUrl sets the url property value. The URL of the interface endpoint.
func (m *AgentInterface) SetUrl(value *string) {
	m.url = value
}

type AgentInterfaceable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetProtocolBinding() *string
	GetProtocolVersion() *string
	GetTenant() *string
	GetUrl() *string
	SetProtocolBinding(value *string)
	SetProtocolVersion(value *string)
	SetTenant(value *string)
	SetUrl(value *string)
}
