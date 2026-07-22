package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// McpToolSearchResults search results for MCP tool definitions.
type McpToolSearchResults struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The total number of MCP tools matching the query.
	count *int32
	// The MCP tool search results.
	tools []McpToolSearchResultable
}

// NewMcpToolSearchResults instantiates a new McpToolSearchResults and sets the default values.
func NewMcpToolSearchResults() *McpToolSearchResults {
	m := &McpToolSearchResults{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateMcpToolSearchResultsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateMcpToolSearchResultsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewMcpToolSearchResults(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *McpToolSearchResults) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetCount gets the count property value. The total number of MCP tools matching the query.
// returns a *int32 when successful
func (m *McpToolSearchResults) GetCount() *int32 {
	return m.count
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *McpToolSearchResults) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["count"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetCount(val)
		}
		return nil
	}
	res["tools"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateMcpToolSearchResultFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]McpToolSearchResultable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(McpToolSearchResultable)
				}
			}
			m.SetTools(res)
		}
		return nil
	}
	return res
}

// GetTools gets the tools property value. The MCP tool search results.
// returns a []McpToolSearchResultable when successful
func (m *McpToolSearchResults) GetTools() []McpToolSearchResultable {
	return m.tools
}

// Serialize serializes information the current object
func (m *McpToolSearchResults) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteInt32Value("count", m.GetCount())
		if err != nil {
			return err
		}
	}
	if m.GetTools() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetTools()))
		for i, v := range m.GetTools() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("tools", cast)
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
func (m *McpToolSearchResults) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetCount sets the count property value. The total number of MCP tools matching the query.
func (m *McpToolSearchResults) SetCount(value *int32) {
	m.count = value
}

// SetTools sets the tools property value. The MCP tool search results.
func (m *McpToolSearchResults) SetTools(value []McpToolSearchResultable) {
	m.tools = value
}

type McpToolSearchResultsable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetCount() *int32
	GetTools() []McpToolSearchResultable
	SetCount(value *int32)
	SetTools(value []McpToolSearchResultable)
}
