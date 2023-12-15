package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArtifactOwner describes the ownership of an artifact.
type ArtifactOwner struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The owner property
	owner *string
}

// NewArtifactOwner instantiates a new ArtifactOwner and sets the default values.
func NewArtifactOwner() *ArtifactOwner {
	m := &ArtifactOwner{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArtifactOwnerFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateArtifactOwnerFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArtifactOwner(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *ArtifactOwner) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
func (m *ArtifactOwner) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["owner"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetOwner(val)
		}
		return nil
	}
	return res
}

// GetOwner gets the owner property value. The owner property
func (m *ArtifactOwner) GetOwner() *string {
	return m.owner
}

// Serialize serializes information the current object
func (m *ArtifactOwner) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("owner", m.GetOwner())
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
func (m *ArtifactOwner) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetOwner sets the owner property value. The owner property
func (m *ArtifactOwner) SetOwner(value *string) {
	m.owner = value
}

// ArtifactOwnerable
type ArtifactOwnerable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetOwner() *string
	SetOwner(value *string)
}
