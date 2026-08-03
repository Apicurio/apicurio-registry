package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentExtension extension for an A2A agent.
type AgentExtension struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The description property
	description *string
	// The params property
	params AgentExtension_paramsable
	// The required property
	required *bool
	// The uri property
	uri *string
}

// NewAgentExtension instantiates a new AgentExtension and sets the default values.
func NewAgentExtension() *AgentExtension {
	m := &AgentExtension{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAgentExtensionFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentExtensionFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAgentExtension(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentExtension) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetDescription gets the description property value. The description property
// returns a *string when successful
func (m *AgentExtension) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentExtension) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
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
	res["params"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateAgentExtension_paramsFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetParams(val.(AgentExtension_paramsable))
		}
		return nil
	}
	res["required"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetRequired(val)
		}
		return nil
	}
	res["uri"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetUri(val)
		}
		return nil
	}
	return res
}

// GetParams gets the params property value. The params property
// returns a AgentExtension_paramsable when successful
func (m *AgentExtension) GetParams() AgentExtension_paramsable {
	return m.params
}

// GetRequired gets the required property value. The required property
// returns a *bool when successful
func (m *AgentExtension) GetRequired() *bool {
	return m.required
}

// GetUri gets the uri property value. The uri property
// returns a *string when successful
func (m *AgentExtension) GetUri() *string {
	return m.uri
}

// Serialize serializes information the current object
func (m *AgentExtension) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("description", m.GetDescription())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("params", m.GetParams())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteBoolValue("required", m.GetRequired())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("uri", m.GetUri())
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
func (m *AgentExtension) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetDescription sets the description property value. The description property
func (m *AgentExtension) SetDescription(value *string) {
	m.description = value
}

// SetParams sets the params property value. The params property
func (m *AgentExtension) SetParams(value AgentExtension_paramsable) {
	m.params = value
}

// SetRequired sets the required property value. The required property
func (m *AgentExtension) SetRequired(value *bool) {
	m.required = value
}

// SetUri sets the uri property value. The uri property
func (m *AgentExtension) SetUri(value *string) {
	m.uri = value
}

type AgentExtensionable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetDescription() *string
	GetParams() AgentExtension_paramsable
	GetRequired() *bool
	GetUri() *string
	SetDescription(value *string)
	SetParams(value AgentExtension_paramsable)
	SetRequired(value *bool)
	SetUri(value *string)
}
