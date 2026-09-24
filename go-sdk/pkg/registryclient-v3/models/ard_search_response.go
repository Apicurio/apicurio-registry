package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArdSearchResponse response body for the ARD POST /search endpoint.
type ArdSearchResponse struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The pageToken property
	pageToken *string
	// The results property
	results []ArdSearchResultEntryable
}

// NewArdSearchResponse instantiates a new ArdSearchResponse and sets the default values.
func NewArdSearchResponse() *ArdSearchResponse {
	m := &ArdSearchResponse{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArdSearchResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArdSearchResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArdSearchResponse(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArdSearchResponse) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArdSearchResponse) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
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
	res["results"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateArdSearchResultEntryFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]ArdSearchResultEntryable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(ArdSearchResultEntryable)
				}
			}
			m.SetResults(res)
		}
		return nil
	}
	return res
}

// GetPageToken gets the pageToken property value. The pageToken property
// returns a *string when successful
func (m *ArdSearchResponse) GetPageToken() *string {
	return m.pageToken
}

// GetResults gets the results property value. The results property
// returns a []ArdSearchResultEntryable when successful
func (m *ArdSearchResponse) GetResults() []ArdSearchResultEntryable {
	return m.results
}

// Serialize serializes information the current object
func (m *ArdSearchResponse) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("pageToken", m.GetPageToken())
		if err != nil {
			return err
		}
	}
	if m.GetResults() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetResults()))
		for i, v := range m.GetResults() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("results", cast)
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
func (m *ArdSearchResponse) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetPageToken sets the pageToken property value. The pageToken property
func (m *ArdSearchResponse) SetPageToken(value *string) {
	m.pageToken = value
}

// SetResults sets the results property value. The results property
func (m *ArdSearchResponse) SetResults(value []ArdSearchResultEntryable) {
	m.results = value
}

type ArdSearchResponseable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetPageToken() *string
	GetResults() []ArdSearchResultEntryable
	SetPageToken(value *string)
	SetResults(value []ArdSearchResultEntryable)
}
