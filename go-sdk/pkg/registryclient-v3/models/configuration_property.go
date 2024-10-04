package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ConfigurationProperty
type ConfigurationProperty struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The description property
	description *string
	// The label property
	label *string
	// The name property
	name *string
	// The type property
	typeEscaped *string
	// The value property
	value *string
}

// NewConfigurationProperty instantiates a new ConfigurationProperty and sets the default values.
func NewConfigurationProperty() *ConfigurationProperty {
	m := &ConfigurationProperty{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateConfigurationPropertyFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateConfigurationPropertyFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewConfigurationProperty(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *ConfigurationProperty) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetDescription gets the description property value. The description property
func (m *ConfigurationProperty) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
func (m *ConfigurationProperty) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["label"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetLabel(val)
		}
		return nil
	}
	res["name"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetName(val)
		}
		return nil
	}
	res["type"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTypeEscaped(val)
		}
		return nil
	}
	res["value"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetValue(val)
		}
		return nil
	}
	return res
}

// GetLabel gets the label property value. The label property
func (m *ConfigurationProperty) GetLabel() *string {
	return m.label
}

// GetName gets the name property value. The name property
func (m *ConfigurationProperty) GetName() *string {
	return m.name
}

// GetTypeEscaped gets the type property value. The type property
func (m *ConfigurationProperty) GetTypeEscaped() *string {
	return m.typeEscaped
}

// GetValue gets the value property value. The value property
func (m *ConfigurationProperty) GetValue() *string {
	return m.value
}

// Serialize serializes information the current object
func (m *ConfigurationProperty) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("description", m.GetDescription())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("label", m.GetLabel())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("name", m.GetName())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("type", m.GetTypeEscaped())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("value", m.GetValue())
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
func (m *ConfigurationProperty) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetDescription sets the description property value. The description property
func (m *ConfigurationProperty) SetDescription(value *string) {
	m.description = value
}

// SetLabel sets the label property value. The label property
func (m *ConfigurationProperty) SetLabel(value *string) {
	m.label = value
}

// SetName sets the name property value. The name property
func (m *ConfigurationProperty) SetName(value *string) {
	m.name = value
}

// SetTypeEscaped sets the type property value. The type property
func (m *ConfigurationProperty) SetTypeEscaped(value *string) {
	m.typeEscaped = value
}

// SetValue sets the value property value. The value property
func (m *ConfigurationProperty) SetValue(value *string) {
	m.value = value
}

// ConfigurationPropertyable
type ConfigurationPropertyable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetDescription() *string
	GetLabel() *string
	GetName() *string
	GetTypeEscaped() *string
	GetValue() *string
	SetDescription(value *string)
	SetLabel(value *string)
	SetName(value *string)
	SetTypeEscaped(value *string)
	SetValue(value *string)
}
