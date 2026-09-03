package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArdExploreResponse response body for the ARD POST /explore endpoint.
type ArdExploreResponse struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Facets keyed by the requested facet field name.
	facets ArdFacetsable
	// The resultType property
	resultType *string
}

// NewArdExploreResponse instantiates a new ArdExploreResponse and sets the default values.
func NewArdExploreResponse() *ArdExploreResponse {
	m := &ArdExploreResponse{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArdExploreResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArdExploreResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArdExploreResponse(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArdExploreResponse) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFacets gets the facets property value. Facets keyed by the requested facet field name.
// returns a ArdFacetsable when successful
func (m *ArdExploreResponse) GetFacets() ArdFacetsable {
	return m.facets
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArdExploreResponse) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["facets"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateArdFacetsFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetFacets(val.(ArdFacetsable))
		}
		return nil
	}
	res["resultType"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetResultType(val)
		}
		return nil
	}
	return res
}

// GetResultType gets the resultType property value. The resultType property
// returns a *string when successful
func (m *ArdExploreResponse) GetResultType() *string {
	return m.resultType
}

// Serialize serializes information the current object
func (m *ArdExploreResponse) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteObjectValue("facets", m.GetFacets())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("resultType", m.GetResultType())
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
func (m *ArdExploreResponse) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetFacets sets the facets property value. Facets keyed by the requested facet field name.
func (m *ArdExploreResponse) SetFacets(value ArdFacetsable) {
	m.facets = value
}

// SetResultType sets the resultType property value. The resultType property
func (m *ArdExploreResponse) SetResultType(value *string) {
	m.resultType = value
}

type ArdExploreResponseable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetFacets() ArdFacetsable
	GetResultType() *string
	SetFacets(value ArdFacetsable)
	SetResultType(value *string)
}
