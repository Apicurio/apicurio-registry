package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// SchemaUsageEventList a batch of schema usage events reported by a SerDes client.
type SchemaUsageEventList struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The list of schema usage events.
	events []SchemaUsageEventable
}

// NewSchemaUsageEventList instantiates a new SchemaUsageEventList and sets the default values.
func NewSchemaUsageEventList() *SchemaUsageEventList {
	m := &SchemaUsageEventList{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateSchemaUsageEventListFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateSchemaUsageEventListFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewSchemaUsageEventList(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *SchemaUsageEventList) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetEvents gets the events property value. The list of schema usage events.
// returns a []SchemaUsageEventable when successful
func (m *SchemaUsageEventList) GetEvents() []SchemaUsageEventable {
	return m.events
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *SchemaUsageEventList) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["events"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateSchemaUsageEventFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]SchemaUsageEventable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(SchemaUsageEventable)
				}
			}
			m.SetEvents(res)
		}
		return nil
	}
	return res
}

// Serialize serializes information the current object
func (m *SchemaUsageEventList) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetEvents() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetEvents()))
		for i, v := range m.GetEvents() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("events", cast)
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
func (m *SchemaUsageEventList) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetEvents sets the events property value. The list of schema usage events.
func (m *SchemaUsageEventList) SetEvents(value []SchemaUsageEventable) {
	m.events = value
}

type SchemaUsageEventListable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetEvents() []SchemaUsageEventable
	SetEvents(value []SchemaUsageEventable)
}
