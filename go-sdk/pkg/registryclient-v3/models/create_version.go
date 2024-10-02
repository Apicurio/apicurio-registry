package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type CreateVersion struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The branches property
	branches []string
	// The content property
	content VersionContentable
	// The description property
	description *string
	// User-defined name-value pairs. Name and value must be strings.
	labels Labelsable
	// The name property
	name *string
	// A single version of an artifact.  Can be provided by the client when creating a new version,or it can be server-generated.  The value can be any string unique to the artifact, but it isrecommended to use a simple integer or a semver value.
	version *string
}

// NewCreateVersion instantiates a new CreateVersion and sets the default values.
func NewCreateVersion() *CreateVersion {
	m := &CreateVersion{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateCreateVersionFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateCreateVersionFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewCreateVersion(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *CreateVersion) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetBranches gets the branches property value. The branches property
// returns a []string when successful
func (m *CreateVersion) GetBranches() []string {
	return m.branches
}

// GetContent gets the content property value. The content property
// returns a VersionContentable when successful
func (m *CreateVersion) GetContent() VersionContentable {
	return m.content
}

// GetDescription gets the description property value. The description property
// returns a *string when successful
func (m *CreateVersion) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *CreateVersion) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["branches"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfPrimitiveValues("string")
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]string, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = *(v.(*string))
				}
			}
			m.SetBranches(res)
		}
		return nil
	}
	res["content"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateVersionContentFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetContent(val.(VersionContentable))
		}
		return nil
	}
	res["description"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDescription(val)
		}
		return nil
	}
	res["labels"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateLabelsFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetLabels(val.(Labelsable))
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

// GetLabels gets the labels property value. User-defined name-value pairs. Name and value must be strings.
// returns a Labelsable when successful
func (m *CreateVersion) GetLabels() Labelsable {
	return m.labels
}

// GetName gets the name property value. The name property
// returns a *string when successful
func (m *CreateVersion) GetName() *string {
	return m.name
}

// GetVersion gets the version property value. A single version of an artifact.  Can be provided by the client when creating a new version,or it can be server-generated.  The value can be any string unique to the artifact, but it isrecommended to use a simple integer or a semver value.
// returns a *string when successful
func (m *CreateVersion) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *CreateVersion) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetBranches() != nil {
		err := writer.WriteCollectionOfStringValues("branches", m.GetBranches())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("content", m.GetContent())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("description", m.GetDescription())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("labels", m.GetLabels())
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
func (m *CreateVersion) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetBranches sets the branches property value. The branches property
func (m *CreateVersion) SetBranches(value []string) {
	m.branches = value
}

// SetContent sets the content property value. The content property
func (m *CreateVersion) SetContent(value VersionContentable) {
	m.content = value
}

// SetDescription sets the description property value. The description property
func (m *CreateVersion) SetDescription(value *string) {
	m.description = value
}

// SetLabels sets the labels property value. User-defined name-value pairs. Name and value must be strings.
func (m *CreateVersion) SetLabels(value Labelsable) {
	m.labels = value
}

// SetName sets the name property value. The name property
func (m *CreateVersion) SetName(value *string) {
	m.name = value
}

// SetVersion sets the version property value. A single version of an artifact.  Can be provided by the client when creating a new version,or it can be server-generated.  The value can be any string unique to the artifact, but it isrecommended to use a simple integer or a semver value.
func (m *CreateVersion) SetVersion(value *string) {
	m.version = value
}

type CreateVersionable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetBranches() []string
	GetContent() VersionContentable
	GetDescription() *string
	GetLabels() Labelsable
	GetName() *string
	GetVersion() *string
	SetBranches(value []string)
	SetContent(value VersionContentable)
	SetDescription(value *string)
	SetLabels(value Labelsable)
	SetName(value *string)
	SetVersion(value *string)
}
