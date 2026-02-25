package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ContentCreateResponse response from uploading content.  Contains the unique content identifier.
type ContentCreateResponse struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The unique identifier of the content.
	contentId *int64
}

// NewContentCreateResponse instantiates a new ContentCreateResponse and sets the default values.
func NewContentCreateResponse() *ContentCreateResponse {
	m := &ContentCreateResponse{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateContentCreateResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateContentCreateResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewContentCreateResponse(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ContentCreateResponse) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetContentId gets the contentId property value. The unique identifier of the content.
// returns a *int64 when successful
func (m *ContentCreateResponse) GetContentId() *int64 {
	return m.contentId
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ContentCreateResponse) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["contentId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetContentId(val)
		}
		return nil
	}
	return res
}

// Serialize serializes information the current object
func (m *ContentCreateResponse) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteInt64Value("contentId", m.GetContentId())
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
func (m *ContentCreateResponse) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetContentId sets the contentId property value. The unique identifier of the content.
func (m *ContentCreateResponse) SetContentId(value *int64) {
	m.contentId = value
}

type ContentCreateResponseable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetContentId() *int64
	SetContentId(value *int64)
}
