package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// CreateArtifactResponse
type CreateArtifactResponse struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifact property
	artifact ArtifactMetaDataable
	// The version property
	version VersionMetaDataable
}

// NewCreateArtifactResponse instantiates a new CreateArtifactResponse and sets the default values.
func NewCreateArtifactResponse() *CreateArtifactResponse {
	m := &CreateArtifactResponse{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateCreateArtifactResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateCreateArtifactResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewCreateArtifactResponse(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *CreateArtifactResponse) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifact gets the artifact property value. The artifact property
func (m *CreateArtifactResponse) GetArtifact() ArtifactMetaDataable {
	return m.artifact
}

// GetFieldDeserializers the deserialization information for the current model
func (m *CreateArtifactResponse) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["artifact"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateArtifactMetaDataFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetArtifact(val.(ArtifactMetaDataable))
		}
		return nil
	}
	res["version"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateVersionMetaDataFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetVersion(val.(VersionMetaDataable))
		}
		return nil
	}
	return res
}

// GetVersion gets the version property value. The version property
func (m *CreateArtifactResponse) GetVersion() VersionMetaDataable {
	return m.version
}

// Serialize serializes information the current object
func (m *CreateArtifactResponse) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteObjectValue("artifact", m.GetArtifact())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("version", m.GetVersion())
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
func (m *CreateArtifactResponse) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifact sets the artifact property value. The artifact property
func (m *CreateArtifactResponse) SetArtifact(value ArtifactMetaDataable) {
	m.artifact = value
}

// SetVersion sets the version property value. The version property
func (m *CreateArtifactResponse) SetVersion(value VersionMetaDataable) {
	m.version = value
}

// CreateArtifactResponseable
type CreateArtifactResponseable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifact() ArtifactMetaDataable
	GetVersion() VersionMetaDataable
	SetArtifact(value ArtifactMetaDataable)
	SetVersion(value VersionMetaDataable)
}
