package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArtifactReference a reference to a different artifact. Typically used with artifact types that can have dependencies like Protobuf.
type ArtifactReference struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifactId property
	artifactId *string
	// The groupId property
	groupId *string
	// The name property
	name *string
	// The version property
	version *string
}

// NewArtifactReference instantiates a new ArtifactReference and sets the default values.
func NewArtifactReference() *ArtifactReference {
	m := &ArtifactReference{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArtifactReferenceFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArtifactReferenceFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArtifactReference(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArtifactReference) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The artifactId property
// returns a *string when successful
func (m *ArtifactReference) GetArtifactId() *string {
	return m.artifactId
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArtifactReference) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["artifactId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetArtifactId(val)
		}
		return nil
	}
	res["groupId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetGroupId(val)
		}
		return nil
	}
	res["name"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetName(val)
		}
		return nil
	}
	res["version"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetVersion(val)
		}
		return nil
	}
	return res
}

// GetGroupId gets the groupId property value. The groupId property
// returns a *string when successful
func (m *ArtifactReference) GetGroupId() *string {
	return m.groupId
}

// GetName gets the name property value. The name property
// returns a *string when successful
func (m *ArtifactReference) GetName() *string {
	return m.name
}

// GetVersion gets the version property value. The version property
// returns a *string when successful
func (m *ArtifactReference) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *ArtifactReference) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("artifactId", m.GetArtifactId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("groupId", m.GetGroupId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("name", m.GetName())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("version", m.GetVersion())
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
func (m *ArtifactReference) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The artifactId property
func (m *ArtifactReference) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetGroupId sets the groupId property value. The groupId property
func (m *ArtifactReference) SetGroupId(value *string) {
	m.groupId = value
}

// SetName sets the name property value. The name property
func (m *ArtifactReference) SetName(value *string) {
	m.name = value
}

// SetVersion sets the version property value. The version property
func (m *ArtifactReference) SetVersion(value *string) {
	m.version = value
}

type ArtifactReferenceable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetGroupId() *string
	GetName() *string
	GetVersion() *string
	SetArtifactId(value *string)
	SetGroupId(value *string)
	SetName(value *string)
	SetVersion(value *string)
}
