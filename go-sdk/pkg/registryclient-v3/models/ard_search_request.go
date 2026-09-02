package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArdSearchRequest request body for the ARD POST /search endpoint.
type ArdSearchRequest struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The federation property
	federation *string
	// The pageSize property
	pageSize *int32
	// The pageToken property
	pageToken *string
	// ARD search query.
	query ArdSearchQueryable
}

// NewArdSearchRequest instantiates a new ArdSearchRequest and sets the default values.
func NewArdSearchRequest() *ArdSearchRequest {
	m := &ArdSearchRequest{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArdSearchRequestFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArdSearchRequestFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArdSearchRequest(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArdSearchRequest) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFederation gets the federation property value. The federation property
// returns a *string when successful
func (m *ArdSearchRequest) GetFederation() *string {
	return m.federation
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArdSearchRequest) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["federation"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetFederation(val)
		}
		return nil
	}
	res["pageSize"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetPageSize(val)
		}
		return nil
	}
	res["pageToken"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetPageToken(val)
		}
		return nil
	}
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
	return res
}

// GetPageSize gets the pageSize property value. The pageSize property
// returns a *int32 when successful
func (m *ArdSearchRequest) GetPageSize() *int32 {
	return m.pageSize
}

// GetPageToken gets the pageToken property value. The pageToken property
// returns a *string when successful
func (m *ArdSearchRequest) GetPageToken() *string {
	return m.pageToken
}

// GetQuery gets the query property value. ARD search query.
// returns a ArdSearchQueryable when successful
func (m *ArdSearchRequest) GetQuery() ArdSearchQueryable {
	return m.query
}

// Serialize serializes information the current object
func (m *ArdSearchRequest) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("federation", m.GetFederation())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("pageSize", m.GetPageSize())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("pageToken", m.GetPageToken())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("query", m.GetQuery())
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
func (m *ArdSearchRequest) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetFederation sets the federation property value. The federation property
func (m *ArdSearchRequest) SetFederation(value *string) {
	m.federation = value
}

// SetPageSize sets the pageSize property value. The pageSize property
func (m *ArdSearchRequest) SetPageSize(value *int32) {
	m.pageSize = value
}

// SetPageToken sets the pageToken property value. The pageToken property
func (m *ArdSearchRequest) SetPageToken(value *string) {
	m.pageToken = value
}

// SetQuery sets the query property value. ARD search query.
func (m *ArdSearchRequest) SetQuery(value ArdSearchQueryable) {
	m.query = value
}

type ArdSearchRequestable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetFederation() *string
	GetPageSize() *int32
	GetPageToken() *string
	GetQuery() ArdSearchQueryable
	SetFederation(value *string)
	SetPageSize(value *int32)
	SetPageToken(value *string)
	SetQuery(value ArdSearchQueryable)
}
