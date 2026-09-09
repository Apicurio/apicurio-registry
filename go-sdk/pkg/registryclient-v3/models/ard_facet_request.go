package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArdFacetRequest a single facet request for the ARD POST /explore endpoint.
type ArdFacetRequest struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The field property
	field *string
	// The limit property
	limit *int32
	// The minCount property
	minCount *int32
}

// NewArdFacetRequest instantiates a new ArdFacetRequest and sets the default values.
func NewArdFacetRequest() *ArdFacetRequest {
	m := &ArdFacetRequest{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArdFacetRequestFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArdFacetRequestFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArdFacetRequest(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArdFacetRequest) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetField gets the field property value. The field property
// returns a *string when successful
func (m *ArdFacetRequest) GetField() *string {
	return m.field
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArdFacetRequest) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["field"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetField(val)
		}
		return nil
	}
	res["limit"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetLimit(val)
		}
		return nil
	}
	res["minCount"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMinCount(val)
		}
		return nil
	}
	return res
}

// GetLimit gets the limit property value. The limit property
// returns a *int32 when successful
func (m *ArdFacetRequest) GetLimit() *int32 {
	return m.limit
}

// GetMinCount gets the minCount property value. The minCount property
// returns a *int32 when successful
func (m *ArdFacetRequest) GetMinCount() *int32 {
	return m.minCount
}

// Serialize serializes information the current object
func (m *ArdFacetRequest) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("field", m.GetField())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("limit", m.GetLimit())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("minCount", m.GetMinCount())
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
func (m *ArdFacetRequest) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetField sets the field property value. The field property
func (m *ArdFacetRequest) SetField(value *string) {
	m.field = value
}

// SetLimit sets the limit property value. The limit property
func (m *ArdFacetRequest) SetLimit(value *int32) {
	m.limit = value
}

// SetMinCount sets the minCount property value. The minCount property
func (m *ArdFacetRequest) SetMinCount(value *int32) {
	m.minCount = value
}

type ArdFacetRequestable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetField() *string
	GetLimit() *int32
	GetMinCount() *int32
	SetField(value *string)
	SetLimit(value *int32)
	SetMinCount(value *int32)
}
