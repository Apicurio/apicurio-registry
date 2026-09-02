package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AiCatalogEntry an entry in the AI Catalog, representing an A2A agent card or an MCP server card.
type AiCatalogEntry struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The capabilities property
	capabilities []string
	// The description property
	description *string
	// The displayName property
	displayName *string
	// The identifier property
	identifier *string
	// The tags property
	tags []string
	// The type property
	typeEscaped *string
	// The updatedAt property
	updatedAt *string
	// The url property
	url *string
	// The version property
	version *string
}

// NewAiCatalogEntry instantiates a new AiCatalogEntry and sets the default values.
func NewAiCatalogEntry() *AiCatalogEntry {
	m := &AiCatalogEntry{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAiCatalogEntryFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAiCatalogEntryFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAiCatalogEntry(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AiCatalogEntry) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetCapabilities gets the capabilities property value. The capabilities property
// returns a []string when successful
func (m *AiCatalogEntry) GetCapabilities() []string {
	return m.capabilities
}

// GetDescription gets the description property value. The description property
// returns a *string when successful
func (m *AiCatalogEntry) GetDescription() *string {
	return m.description
}

// GetDisplayName gets the displayName property value. The displayName property
// returns a *string when successful
func (m *AiCatalogEntry) GetDisplayName() *string {
	return m.displayName
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AiCatalogEntry) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["capabilities"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
			m.SetCapabilities(res)
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
	res["tags"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
			m.SetTags(res)
		}
		return nil
	}
	res["type"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTypeEscaped(val)
		}
		return nil
	}
	res["updatedAt"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetUpdatedAt(val)
		}
		return nil
	}
	res["url"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetUrl(val)
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

// GetIdentifier gets the identifier property value. The identifier property
// returns a *string when successful
func (m *AiCatalogEntry) GetIdentifier() *string {
	return m.identifier
}

// GetTags gets the tags property value. The tags property
// returns a []string when successful
func (m *AiCatalogEntry) GetTags() []string {
	return m.tags
}

// GetTypeEscaped gets the type property value. The type property
// returns a *string when successful
func (m *AiCatalogEntry) GetTypeEscaped() *string {
	return m.typeEscaped
}

// GetUpdatedAt gets the updatedAt property value. The updatedAt property
// returns a *string when successful
func (m *AiCatalogEntry) GetUpdatedAt() *string {
	return m.updatedAt
}

// GetUrl gets the url property value. The url property
// returns a *string when successful
func (m *AiCatalogEntry) GetUrl() *string {
	return m.url
}

// GetVersion gets the version property value. The version property
// returns a *string when successful
func (m *AiCatalogEntry) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *AiCatalogEntry) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetCapabilities() != nil {
		err := writer.WriteCollectionOfStringValues("capabilities", m.GetCapabilities())
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
		err := writer.WriteStringValue("displayName", m.GetDisplayName())
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
	if m.GetTags() != nil {
		err := writer.WriteCollectionOfStringValues("tags", m.GetTags())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("type", m.GetTypeEscaped())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("updatedAt", m.GetUpdatedAt())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("url", m.GetUrl())
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
func (m *AiCatalogEntry) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetCapabilities sets the capabilities property value. The capabilities property
func (m *AiCatalogEntry) SetCapabilities(value []string) {
	m.capabilities = value
}

// SetDescription sets the description property value. The description property
func (m *AiCatalogEntry) SetDescription(value *string) {
	m.description = value
}

// SetDisplayName sets the displayName property value. The displayName property
func (m *AiCatalogEntry) SetDisplayName(value *string) {
	m.displayName = value
}

// SetIdentifier sets the identifier property value. The identifier property
func (m *AiCatalogEntry) SetIdentifier(value *string) {
	m.identifier = value
}

// SetTags sets the tags property value. The tags property
func (m *AiCatalogEntry) SetTags(value []string) {
	m.tags = value
}

// SetTypeEscaped sets the type property value. The type property
func (m *AiCatalogEntry) SetTypeEscaped(value *string) {
	m.typeEscaped = value
}

// SetUpdatedAt sets the updatedAt property value. The updatedAt property
func (m *AiCatalogEntry) SetUpdatedAt(value *string) {
	m.updatedAt = value
}

// SetUrl sets the url property value. The url property
func (m *AiCatalogEntry) SetUrl(value *string) {
	m.url = value
}

// SetVersion sets the version property value. The version property
func (m *AiCatalogEntry) SetVersion(value *string) {
	m.version = value
}

type AiCatalogEntryable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetCapabilities() []string
	GetDescription() *string
	GetDisplayName() *string
	GetIdentifier() *string
	GetTags() []string
	GetTypeEscaped() *string
	GetUpdatedAt() *string
	GetUrl() *string
	GetVersion() *string
	SetCapabilities(value []string)
	SetDescription(value *string)
	SetDisplayName(value *string)
	SetIdentifier(value *string)
	SetTags(value []string)
	SetTypeEscaped(value *string)
	SetUpdatedAt(value *string)
	SetUrl(value *string)
	SetVersion(value *string)
}
