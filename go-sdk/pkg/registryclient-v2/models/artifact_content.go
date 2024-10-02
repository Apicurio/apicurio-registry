package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type ArtifactContent struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Raw content of the artifact or a valid (and accessible) URL where the content can be found.
	content *string
	// Collection of references to other artifacts.
	references []ArtifactReferenceable
}

// NewArtifactContent instantiates a new ArtifactContent and sets the default values.
func NewArtifactContent() *ArtifactContent {
	m := &ArtifactContent{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArtifactContentFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArtifactContentFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArtifactContent(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArtifactContent) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetContent gets the content property value. Raw content of the artifact or a valid (and accessible) URL where the content can be found.
// returns a *string when successful
func (m *ArtifactContent) GetContent() *string {
	return m.content
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArtifactContent) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["content"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetContent(val)
		}
		return nil
	}
	res["references"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateArtifactReferenceFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]ArtifactReferenceable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(ArtifactReferenceable)
				}
			}
			m.SetReferences(res)
		}
		return nil
	}
	return res
}

// GetReferences gets the references property value. Collection of references to other artifacts.
// returns a []ArtifactReferenceable when successful
func (m *ArtifactContent) GetReferences() []ArtifactReferenceable {
	return m.references
}

// Serialize serializes information the current object
func (m *ArtifactContent) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("content", m.GetContent())
		if err != nil {
			return err
		}
	}
	if m.GetReferences() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetReferences()))
		for i, v := range m.GetReferences() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("references", cast)
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
func (m *ArtifactContent) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetContent sets the content property value. Raw content of the artifact or a valid (and accessible) URL where the content can be found.
func (m *ArtifactContent) SetContent(value *string) {
	m.content = value
}

// SetReferences sets the references property value. Collection of references to other artifacts.
func (m *ArtifactContent) SetReferences(value []ArtifactReferenceable) {
	m.references = value
}

type ArtifactContentable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetContent() *string
	GetReferences() []ArtifactReferenceable
	SetContent(value *string)
	SetReferences(value []ArtifactReferenceable)
}
