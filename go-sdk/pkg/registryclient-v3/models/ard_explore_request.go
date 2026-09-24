package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArdExploreRequest request body for the ARD POST /explore endpoint.
type ArdExploreRequest struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// ARD search query.
	query ArdSearchQueryable
	// Requested result type for the ARD POST /explore endpoint.
	resultType ArdResultTypeable
}

// NewArdExploreRequest instantiates a new ArdExploreRequest and sets the default values.
func NewArdExploreRequest() *ArdExploreRequest {
	m := &ArdExploreRequest{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArdExploreRequestFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArdExploreRequestFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArdExploreRequest(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArdExploreRequest) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArdExploreRequest) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["query"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateArdSearchQueryFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetQuery(val.(ArdSearchQueryable))
		}
		return nil
	}
	res["resultType"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateArdResultTypeFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetResultType(val.(ArdResultTypeable))
		}
		return nil
	}
	return res
}

// GetQuery gets the query property value. ARD search query.
// returns a ArdSearchQueryable when successful
func (m *ArdExploreRequest) GetQuery() ArdSearchQueryable {
	return m.query
}

// GetResultType gets the resultType property value. Requested result type for the ARD POST /explore endpoint.
// returns a ArdResultTypeable when successful
func (m *ArdExploreRequest) GetResultType() ArdResultTypeable {
	return m.resultType
}

// Serialize serializes information the current object
func (m *ArdExploreRequest) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteObjectValue("query", m.GetQuery())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("resultType", m.GetResultType())
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
func (m *ArdExploreRequest) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetQuery sets the query property value. ARD search query.
func (m *ArdExploreRequest) SetQuery(value ArdSearchQueryable) {
	m.query = value
}

// SetResultType sets the resultType property value. Requested result type for the ARD POST /explore endpoint.
func (m *ArdExploreRequest) SetResultType(value ArdResultTypeable) {
	m.resultType = value
}

type ArdExploreRequestable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetQuery() ArdSearchQueryable
	GetResultType() ArdResultTypeable
	SetQuery(value ArdSearchQueryable)
	SetResultType(value ArdResultTypeable)
}
