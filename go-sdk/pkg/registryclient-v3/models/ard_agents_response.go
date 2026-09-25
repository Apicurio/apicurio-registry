package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArdAgentsResponse response body for the ARD GET /agents endpoint.
type ArdAgentsResponse struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The items property
	items []AiCatalogEntryable
	// The pageToken property
	pageToken *string
	// The total property
	total *int32
}

// NewArdAgentsResponse instantiates a new ArdAgentsResponse and sets the default values.
func NewArdAgentsResponse() *ArdAgentsResponse {
	m := &ArdAgentsResponse{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArdAgentsResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArdAgentsResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArdAgentsResponse(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArdAgentsResponse) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArdAgentsResponse) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["items"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateAiCatalogEntryFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]AiCatalogEntryable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(AiCatalogEntryable)
				}
			}
			m.SetItems(res)
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
	res["total"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTotal(val)
		}
		return nil
	}
	return res
}

// GetItems gets the items property value. The items property
// returns a []AiCatalogEntryable when successful
func (m *ArdAgentsResponse) GetItems() []AiCatalogEntryable {
	return m.items
}

// GetPageToken gets the pageToken property value. The pageToken property
// returns a *string when successful
func (m *ArdAgentsResponse) GetPageToken() *string {
	return m.pageToken
}

// GetTotal gets the total property value. The total property
// returns a *int32 when successful
func (m *ArdAgentsResponse) GetTotal() *int32 {
	return m.total
}

// Serialize serializes information the current object
func (m *ArdAgentsResponse) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetItems() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetItems()))
		for i, v := range m.GetItems() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("items", cast)
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
		err := writer.WriteInt32Value("total", m.GetTotal())
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
func (m *ArdAgentsResponse) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetItems sets the items property value. The items property
func (m *ArdAgentsResponse) SetItems(value []AiCatalogEntryable) {
	m.items = value
}

// SetPageToken sets the pageToken property value. The pageToken property
func (m *ArdAgentsResponse) SetPageToken(value *string) {
	m.pageToken = value
}

// SetTotal sets the total property value. The total property
func (m *ArdAgentsResponse) SetTotal(value *int32) {
	m.total = value
}

type ArdAgentsResponseable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetItems() []AiCatalogEntryable
	GetPageToken() *string
	GetTotal() *int32
	SetItems(value []AiCatalogEntryable)
	SetPageToken(value *string)
	SetTotal(value *int32)
}
