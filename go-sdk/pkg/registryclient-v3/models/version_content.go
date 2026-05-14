package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type VersionContent struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Raw content of the artifact version or a valid (and accessible) URL where the content can be found.
	content *string
	// The content-type, such as `application/json` or `text/xml`.
	contentType *string
	// Optional encoding for the content property. When set to 'base64', the content value will be base64-decoded by the server before processing.
	encoding *VersionContent_encoding
	// Collection of references to other artifacts.
	references []ArtifactReferenceable
}

// NewVersionContent instantiates a new VersionContent and sets the default values.
func NewVersionContent() *VersionContent {
	m := &VersionContent{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateVersionContentFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateVersionContentFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewVersionContent(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *VersionContent) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetContent gets the content property value. Raw content of the artifact version or a valid (and accessible) URL where the content can be found.
// returns a *string when successful
func (m *VersionContent) GetContent() *string {
	return m.content
}

// GetContentType gets the contentType property value. The content-type, such as `application/json` or `text/xml`.
// returns a *string when successful
func (m *VersionContent) GetContentType() *string {
	return m.contentType
}

// GetEncoding gets the encoding property value. Optional encoding for the content property. When set to 'base64', the content value will be base64-decoded by the server before processing.
// returns a *VersionContent_encoding when successful
func (m *VersionContent) GetEncoding() *VersionContent_encoding {
	return m.encoding
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *VersionContent) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["contentType"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetContentType(val)
		}
		return nil
	}
	res["encoding"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseVersionContent_encoding)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetEncoding(val.(*VersionContent_encoding))
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
func (m *VersionContent) GetReferences() []ArtifactReferenceable {
	return m.references
}

// Serialize serializes information the current object
func (m *VersionContent) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("content", m.GetContent())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("contentType", m.GetContentType())
		if err != nil {
			return err
		}
	}
	if m.GetEncoding() != nil {
		cast := (*m.GetEncoding()).String()
		err := writer.WriteStringValue("encoding", &cast)
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
func (m *VersionContent) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetContent sets the content property value. Raw content of the artifact version or a valid (and accessible) URL where the content can be found.
func (m *VersionContent) SetContent(value *string) {
	m.content = value
}

// SetContentType sets the contentType property value. The content-type, such as `application/json` or `text/xml`.
func (m *VersionContent) SetContentType(value *string) {
	m.contentType = value
}

// SetEncoding sets the encoding property value. Optional encoding for the content property. When set to 'base64', the content value will be base64-decoded by the server before processing.
func (m *VersionContent) SetEncoding(value *VersionContent_encoding) {
	m.encoding = value
}

// SetReferences sets the references property value. Collection of references to other artifacts.
func (m *VersionContent) SetReferences(value []ArtifactReferenceable) {
	m.references = value
}

type VersionContentable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetContent() *string
	GetContentType() *string
	GetEncoding() *VersionContent_encoding
	GetReferences() []ArtifactReferenceable
	SetContent(value *string)
	SetContentType(value *string)
	SetEncoding(value *VersionContent_encoding)
	SetReferences(value []ArtifactReferenceable)
}
