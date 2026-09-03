package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArdResultType requested result type for the ARD POST /explore endpoint.
type ArdResultType struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The facets property
	facets []ArdFacetRequestable
}

// NewArdResultType instantiates a new ArdResultType and sets the default values.
func NewArdResultType() *ArdResultType {
	m := &ArdResultType{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArdResultTypeFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArdResultTypeFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArdResultType(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArdResultType) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFacets gets the facets property value. The facets property
// returns a []ArdFacetRequestable when successful
func (m *ArdResultType) GetFacets() []ArdFacetRequestable {
	return m.facets
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArdResultType) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["facets"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateArdFacetRequestFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]ArdFacetRequestable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(ArdFacetRequestable)
				}
			}
			m.SetFacets(res)
		}
		return nil
	}
	return res
}

// Serialize serializes information the current object
func (m *ArdResultType) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetFacets() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetFacets()))
		for i, v := range m.GetFacets() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("facets", cast)
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
func (m *ArdResultType) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetFacets sets the facets property value. The facets property
func (m *ArdResultType) SetFacets(value []ArdFacetRequestable) {
	m.facets = value
}

type ArdResultTypeable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetFacets() []ArdFacetRequestable
	SetFacets(value []ArdFacetRequestable)
}
