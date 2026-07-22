package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentCardSignature represents a cryptographic signature on an A2A agent card.
type AgentCardSignature struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // Unprotected header parameters.
    header AgentCardSignature_headerable
    // Base64url-encoded protected header.
    protected *string
    // The signature value.
    signature *string
}
// NewAgentCardSignature instantiates a new AgentCardSignature and sets the default values.
func NewAgentCardSignature()(*AgentCardSignature) {
    m := &AgentCardSignature{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateAgentCardSignatureFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentCardSignatureFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewAgentCardSignature(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentCardSignature) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentCardSignature) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["header"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateAgentCardSignature_headerFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetHeader(val.(AgentCardSignature_headerable))
        }
        return nil
    }
    res["protected"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetProtected(val)
        }
        return nil
    }
    res["signature"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetSignature(val)
        }
        return nil
    }
    return res
}
// GetHeader gets the header property value. Unprotected header parameters.
// returns a AgentCardSignature_headerable when successful
func (m *AgentCardSignature) GetHeader()(AgentCardSignature_headerable) {
    return m.header
}
// GetProtected gets the protected property value. Base64url-encoded protected header.
// returns a *string when successful
func (m *AgentCardSignature) GetProtected()(*string) {
    return m.protected
}
// GetSignature gets the signature property value. The signature value.
// returns a *string when successful
func (m *AgentCardSignature) GetSignature()(*string) {
    return m.signature
}
// Serialize serializes information the current object
func (m *AgentCardSignature) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteObjectValue("header", m.GetHeader())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("protected", m.GetProtected())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("signature", m.GetSignature())
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
func (m *AgentCardSignature) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetHeader sets the header property value. Unprotected header parameters.
func (m *AgentCardSignature) SetHeader(value AgentCardSignature_headerable)() {
    m.header = value
}
// SetProtected sets the protected property value. Base64url-encoded protected header.
func (m *AgentCardSignature) SetProtected(value *string)() {
    m.protected = value
}
// SetSignature sets the signature property value. The signature value.
func (m *AgentCardSignature) SetSignature(value *string)() {
    m.signature = value
}
type AgentCardSignatureable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetHeader()(AgentCardSignature_headerable)
    GetProtected()(*string)
    GetSignature()(*string)
    SetHeader(value AgentCardSignature_headerable)()
    SetProtected(value *string)()
    SetSignature(value *string)()
}
