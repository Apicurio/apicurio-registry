package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArdSearchQuery aRD search query.
type ArdSearchQuery struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// ARD search filter map; keys are filter names (type, tags, capabilities, publisher) and values are the list of accepted values for that filter (OR semantics within a key, AND semantics across keys).
	filter ArdFilterable
	// The text property
	text *string
}

// NewArdSearchQuery instantiates a new ArdSearchQuery and sets the default values.
func NewArdSearchQuery() *ArdSearchQuery {
	m := &ArdSearchQuery{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArdSearchQueryFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArdSearchQueryFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArdSearchQuery(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArdSearchQuery) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArdSearchQuery) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["filter"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateArdFilterFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetFilter(val.(ArdFilterable))
		}
		return nil
	}
	res["text"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetText(val)
		}
		return nil
	}
	return res
}

// GetFilter gets the filter property value. ARD search filter map; keys are filter names (type, tags, capabilities, publisher) and values are the list of accepted values for that filter (OR semantics within a key, AND semantics across keys).
// returns a ArdFilterable when successful
func (m *ArdSearchQuery) GetFilter() ArdFilterable {
	return m.filter
}

// GetText gets the text property value. The text property
// returns a *string when successful
func (m *ArdSearchQuery) GetText() *string {
	return m.text
}

// Serialize serializes information the current object
func (m *ArdSearchQuery) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteObjectValue("filter", m.GetFilter())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("text", m.GetText())
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
func (m *ArdSearchQuery) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetFilter sets the filter property value. ARD search filter map; keys are filter names (type, tags, capabilities, publisher) and values are the list of accepted values for that filter (OR semantics within a key, AND semantics across keys).
func (m *ArdSearchQuery) SetFilter(value ArdFilterable) {
	m.filter = value
}

// SetText sets the text property value. The text property
func (m *ArdSearchQuery) SetText(value *string) {
	m.text = value
}

type ArdSearchQueryable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetFilter() ArdFilterable
	GetText() *string
	SetFilter(value ArdFilterable)
	SetText(value *string)
}
