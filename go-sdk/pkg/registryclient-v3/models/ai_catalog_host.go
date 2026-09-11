package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AiCatalogHost information about the AI Catalog host (this registry instance).
type AiCatalogHost struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The displayName property
	displayName *string
	// The documentationUrl property
	documentationUrl *string
	// The identifier property
	identifier *string
	// The logoUrl property
	logoUrl *string
}

// NewAiCatalogHost instantiates a new AiCatalogHost and sets the default values.
func NewAiCatalogHost() *AiCatalogHost {
	m := &AiCatalogHost{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAiCatalogHostFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAiCatalogHostFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAiCatalogHost(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AiCatalogHost) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetDisplayName gets the displayName property value. The displayName property
// returns a *string when successful
func (m *AiCatalogHost) GetDisplayName() *string {
	return m.displayName
}

// GetDocumentationUrl gets the documentationUrl property value. The documentationUrl property
// returns a *string when successful
func (m *AiCatalogHost) GetDocumentationUrl() *string {
	return m.documentationUrl
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AiCatalogHost) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["displayName"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDisplayName(val)
		}
		return nil
	}
	res["documentationUrl"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDocumentationUrl(val)
		}
		return nil
	}
	res["identifier"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetIdentifier(val)
		}
		return nil
	}
	res["logoUrl"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetLogoUrl(val)
		}
		return nil
	}
	return res
}

// GetIdentifier gets the identifier property value. The identifier property
// returns a *string when successful
func (m *AiCatalogHost) GetIdentifier() *string {
	return m.identifier
}

// GetLogoUrl gets the logoUrl property value. The logoUrl property
// returns a *string when successful
func (m *AiCatalogHost) GetLogoUrl() *string {
	return m.logoUrl
}

// Serialize serializes information the current object
func (m *AiCatalogHost) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("displayName", m.GetDisplayName())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("documentationUrl", m.GetDocumentationUrl())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("identifier", m.GetIdentifier())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("logoUrl", m.GetLogoUrl())
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
func (m *AiCatalogHost) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetDisplayName sets the displayName property value. The displayName property
func (m *AiCatalogHost) SetDisplayName(value *string) {
	m.displayName = value
}

// SetDocumentationUrl sets the documentationUrl property value. The documentationUrl property
func (m *AiCatalogHost) SetDocumentationUrl(value *string) {
	m.documentationUrl = value
}

// SetIdentifier sets the identifier property value. The identifier property
func (m *AiCatalogHost) SetIdentifier(value *string) {
	m.identifier = value
}

// SetLogoUrl sets the logoUrl property value. The logoUrl property
func (m *AiCatalogHost) SetLogoUrl(value *string) {
	m.logoUrl = value
}

type AiCatalogHostable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetDisplayName() *string
	GetDocumentationUrl() *string
	GetIdentifier() *string
	GetLogoUrl() *string
	SetDisplayName(value *string)
	SetDocumentationUrl(value *string)
	SetIdentifier(value *string)
	SetLogoUrl(value *string)
}
