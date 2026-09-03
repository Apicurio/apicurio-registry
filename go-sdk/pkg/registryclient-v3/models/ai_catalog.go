package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AiCatalog aI Catalog document per the ai-catalog.io specification, listing agent and tool entries hosted by this registry.
type AiCatalog struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The entries property
	entries []AiCatalogEntryable
	// Information about the AI Catalog host (this registry instance).
	host AiCatalogHostable
	// Opaque pagination token for retrieving the next page of ARD list results. Only populated by the ARD `GET /agents` endpoint; absent from the static `/.well-known/ai-catalog.json` projection.
	nextPageToken *string
	// The specVersion property
	specVersion *string
}

// NewAiCatalog instantiates a new AiCatalog and sets the default values.
func NewAiCatalog() *AiCatalog {
	m := &AiCatalog{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAiCatalogFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAiCatalogFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAiCatalog(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AiCatalog) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetEntries gets the entries property value. The entries property
// returns a []AiCatalogEntryable when successful
func (m *AiCatalog) GetEntries() []AiCatalogEntryable {
	return m.entries
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AiCatalog) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["entries"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateAiCatalogEntryFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]AiCatalogEntryable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(AiCatalogEntryable)
				}
			}
			m.SetEntries(res)
		}
		return nil
	}
	res["host"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateAiCatalogHostFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetHost(val.(AiCatalogHostable))
		}
		return nil
	}
	res["nextPageToken"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetNextPageToken(val)
		}
		return nil
	}
	res["specVersion"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSpecVersion(val)
		}
		return nil
	}
	return res
}

// GetHost gets the host property value. Information about the AI Catalog host (this registry instance).
// returns a AiCatalogHostable when successful
func (m *AiCatalog) GetHost() AiCatalogHostable {
	return m.host
}

// GetNextPageToken gets the nextPageToken property value. Opaque pagination token for retrieving the next page of ARD list results. Only populated by the ARD `GET /agents` endpoint; absent from the static `/.well-known/ai-catalog.json` projection.
// returns a *string when successful
func (m *AiCatalog) GetNextPageToken() *string {
	return m.nextPageToken
}

// GetSpecVersion gets the specVersion property value. The specVersion property
// returns a *string when successful
func (m *AiCatalog) GetSpecVersion() *string {
	return m.specVersion
}

// Serialize serializes information the current object
func (m *AiCatalog) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetEntries() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetEntries()))
		for i, v := range m.GetEntries() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("entries", cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("host", m.GetHost())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("nextPageToken", m.GetNextPageToken())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("specVersion", m.GetSpecVersion())
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
func (m *AiCatalog) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetEntries sets the entries property value. The entries property
func (m *AiCatalog) SetEntries(value []AiCatalogEntryable) {
	m.entries = value
}

// SetHost sets the host property value. Information about the AI Catalog host (this registry instance).
func (m *AiCatalog) SetHost(value AiCatalogHostable) {
	m.host = value
}

// SetNextPageToken sets the nextPageToken property value. Opaque pagination token for retrieving the next page of ARD list results. Only populated by the ARD `GET /agents` endpoint; absent from the static `/.well-known/ai-catalog.json` projection.
func (m *AiCatalog) SetNextPageToken(value *string) {
	m.nextPageToken = value
}

// SetSpecVersion sets the specVersion property value. The specVersion property
func (m *AiCatalog) SetSpecVersion(value *string) {
	m.specVersion = value
}

type AiCatalogable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetEntries() []AiCatalogEntryable
	GetHost() AiCatalogHostable
	GetNextPageToken() *string
	GetSpecVersion() *string
	SetEntries(value []AiCatalogEntryable)
	SetHost(value AiCatalogHostable)
	SetNextPageToken(value *string)
	SetSpecVersion(value *string)
}
