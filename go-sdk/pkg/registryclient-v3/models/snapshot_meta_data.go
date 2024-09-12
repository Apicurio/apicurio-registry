package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// SnapshotMetaData
type SnapshotMetaData struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The snapshotId property
	snapshotId *string
}

// NewSnapshotMetaData instantiates a new SnapshotMetaData and sets the default values.
func NewSnapshotMetaData() *SnapshotMetaData {
	m := &SnapshotMetaData{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateSnapshotMetaDataFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateSnapshotMetaDataFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewSnapshotMetaData(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *SnapshotMetaData) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
func (m *SnapshotMetaData) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["snapshotId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSnapshotId(val)
		}
		return nil
	}
	return res
}

// GetSnapshotId gets the snapshotId property value. The snapshotId property
func (m *SnapshotMetaData) GetSnapshotId() *string {
	return m.snapshotId
}

// Serialize serializes information the current object
func (m *SnapshotMetaData) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("snapshotId", m.GetSnapshotId())
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
func (m *SnapshotMetaData) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetSnapshotId sets the snapshotId property value. The snapshotId property
func (m *SnapshotMetaData) SetSnapshotId(value *string) {
	m.snapshotId = value
}

// SnapshotMetaDataable
type SnapshotMetaDataable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetSnapshotId() *string
	SetSnapshotId(value *string)
}
