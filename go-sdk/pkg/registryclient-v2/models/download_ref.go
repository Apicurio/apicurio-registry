package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// DownloadRef models a download "link".  Useful for browser use-cases.
type DownloadRef struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The downloadId property
	downloadId *string
	// The href property
	href *string
}

// NewDownloadRef instantiates a new DownloadRef and sets the default values.
func NewDownloadRef() *DownloadRef {
	m := &DownloadRef{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateDownloadRefFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateDownloadRefFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewDownloadRef(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *DownloadRef) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetDownloadId gets the downloadId property value. The downloadId property
func (m *DownloadRef) GetDownloadId() *string {
	return m.downloadId
}

// GetFieldDeserializers the deserialization information for the current model
func (m *DownloadRef) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["downloadId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDownloadId(val)
		}
		return nil
	}
	res["href"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetHref(val)
		}
		return nil
	}
	return res
}

// GetHref gets the href property value. The href property
func (m *DownloadRef) GetHref() *string {
	return m.href
}

// Serialize serializes information the current object
func (m *DownloadRef) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("downloadId", m.GetDownloadId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("href", m.GetHref())
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
func (m *DownloadRef) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetDownloadId sets the downloadId property value. The downloadId property
func (m *DownloadRef) SetDownloadId(value *string) {
	m.downloadId = value
}

// SetHref sets the href property value. The href property
func (m *DownloadRef) SetHref(value *string) {
	m.href = value
}

// DownloadRefable
type DownloadRefable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetDownloadId() *string
	GetHref() *string
	SetDownloadId(value *string)
	SetHref(value *string)
}
