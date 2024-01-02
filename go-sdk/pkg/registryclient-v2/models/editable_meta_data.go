package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// EditableMetaData
type EditableMetaData struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The description property
	description *string
	// The labels property
	labels []string
	// The name property
	name *string
	// User-defined name-value pairs. Name and value must be strings.
	properties Propertiesable
}

// NewEditableMetaData instantiates a new EditableMetaData and sets the default values.
func NewEditableMetaData() *EditableMetaData {
	m := &EditableMetaData{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateEditableMetaDataFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateEditableMetaDataFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewEditableMetaData(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *EditableMetaData) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetDescription gets the description property value. The description property
func (m *EditableMetaData) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
func (m *EditableMetaData) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["labels"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfPrimitiveValues("string")
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]string, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = *(v.(*string))
				}
			}
			m.SetLabels(res)
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
	res["properties"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreatePropertiesFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetProperties(val.(Propertiesable))
		}
		return nil
	}
	return res
}

// GetLabels gets the labels property value. The labels property
func (m *EditableMetaData) GetLabels() []string {
	return m.labels
}

// GetName gets the name property value. The name property
func (m *EditableMetaData) GetName() *string {
	return m.name
}

// GetProperties gets the properties property value. User-defined name-value pairs. Name and value must be strings.
func (m *EditableMetaData) GetProperties() Propertiesable {
	return m.properties
}

// Serialize serializes information the current object
func (m *EditableMetaData) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("description", m.GetDescription())
		if err != nil {
			return err
		}
	}
	if m.GetLabels() != nil {
		err := writer.WriteCollectionOfStringValues("labels", m.GetLabels())
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
		err := writer.WriteObjectValue("properties", m.GetProperties())
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
func (m *EditableMetaData) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetDescription sets the description property value. The description property
func (m *EditableMetaData) SetDescription(value *string) {
	m.description = value
}

// SetLabels sets the labels property value. The labels property
func (m *EditableMetaData) SetLabels(value []string) {
	m.labels = value
}

// SetName sets the name property value. The name property
func (m *EditableMetaData) SetName(value *string) {
	m.name = value
}

// SetProperties sets the properties property value. User-defined name-value pairs. Name and value must be strings.
func (m *EditableMetaData) SetProperties(value Propertiesable) {
	m.properties = value
}

// EditableMetaDataable
type EditableMetaDataable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetDescription() *string
	GetLabels() []string
	GetName() *string
	GetProperties() Propertiesable
	SetDescription(value *string)
	SetLabels(value []string)
	SetName(value *string)
	SetProperties(value Propertiesable)
}
