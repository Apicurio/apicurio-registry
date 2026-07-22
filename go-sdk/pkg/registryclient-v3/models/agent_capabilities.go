package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentCapabilities represents the capabilities of an A2A agent.
type AgentCapabilities struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // Whether the agent supports extended agent card.
    extendedAgentCard *bool
    // Additional extensions supported by this agent.
    extensions []AgentExtensionable
    // Whether the agent supports push notifications.
    pushNotifications *bool
    // Whether the agent supports streaming responses.
    streaming *bool
}
// NewAgentCapabilities instantiates a new AgentCapabilities and sets the default values.
func NewAgentCapabilities()(*AgentCapabilities) {
    m := &AgentCapabilities{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateAgentCapabilitiesFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentCapabilitiesFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewAgentCapabilities(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentCapabilities) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetExtendedAgentCard gets the extendedAgentCard property value. Whether the agent supports extended agent card.
// returns a *bool when successful
func (m *AgentCapabilities) GetExtendedAgentCard()(*bool) {
    return m.extendedAgentCard
}
// GetExtensions gets the extensions property value. Additional extensions supported by this agent.
// returns a []AgentExtensionable when successful
func (m *AgentCapabilities) GetExtensions()([]AgentExtensionable) {
    return m.extensions
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentCapabilities) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["extendedAgentCard"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetBoolValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetExtendedAgentCard(val)
        }
        return nil
    }
    res["extensions"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetCollectionOfObjectValues(CreateAgentExtensionFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            res := make([]AgentExtensionable, len(val))
            for i, v := range val {
                if v != nil {
                    res[i] = v.(AgentExtensionable)
                }
            }
            m.SetExtensions(res)
        }
        return nil
    }
    res["pushNotifications"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetBoolValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetPushNotifications(val)
        }
        return nil
    }
    res["streaming"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetBoolValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetStreaming(val)
        }
        return nil
    }
    return res
}
// GetPushNotifications gets the pushNotifications property value. Whether the agent supports push notifications.
// returns a *bool when successful
func (m *AgentCapabilities) GetPushNotifications()(*bool) {
    return m.pushNotifications
}
// GetStreaming gets the streaming property value. Whether the agent supports streaming responses.
// returns a *bool when successful
func (m *AgentCapabilities) GetStreaming()(*bool) {
    return m.streaming
}
// Serialize serializes information the current object
func (m *AgentCapabilities) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteBoolValue("extendedAgentCard", m.GetExtendedAgentCard())
        if err != nil {
            return err
        }
    }
    if m.GetExtensions() != nil {
        cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetExtensions()))
        for i, v := range m.GetExtensions() {
            if v != nil {
                cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
            }
        }
        err := writer.WriteCollectionOfObjectValues("extensions", cast)
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteBoolValue("pushNotifications", m.GetPushNotifications())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteBoolValue("streaming", m.GetStreaming())
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
func (m *AgentCapabilities) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetExtendedAgentCard sets the extendedAgentCard property value. Whether the agent supports extended agent card.
func (m *AgentCapabilities) SetExtendedAgentCard(value *bool)() {
    m.extendedAgentCard = value
}
// SetExtensions sets the extensions property value. Additional extensions supported by this agent.
func (m *AgentCapabilities) SetExtensions(value []AgentExtensionable)() {
    m.extensions = value
}
// SetPushNotifications sets the pushNotifications property value. Whether the agent supports push notifications.
func (m *AgentCapabilities) SetPushNotifications(value *bool)() {
    m.pushNotifications = value
}
// SetStreaming sets the streaming property value. Whether the agent supports streaming responses.
func (m *AgentCapabilities) SetStreaming(value *bool)() {
    m.streaming = value
}
type AgentCapabilitiesable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetExtendedAgentCard()(*bool)
    GetExtensions()([]AgentExtensionable)
    GetPushNotifications()(*bool)
    GetStreaming()(*bool)
    SetExtendedAgentCard(value *bool)()
    SetExtensions(value []AgentExtensionable)()
    SetPushNotifications(value *bool)()
    SetStreaming(value *bool)()
}
