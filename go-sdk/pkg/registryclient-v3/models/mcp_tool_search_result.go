package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// McpToolSearchResult a single MCP tool search result with metadata.
type McpToolSearchResult struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifact ID of the MCP tool.
	artifactId *string
	// Timestamp when the MCP tool was created.
	createdOn *int64
	// A description of the MCP tool.
	description *string
	// The group ID of the MCP tool artifact.
	groupId *string
	// The name of the MCP tool.
	name *string
	// The owner of the MCP tool artifact.
	owner *string
	// Parameter names accepted by the MCP tool.
	parameters []string
	// The title of the MCP tool.
	title *string
}

// NewMcpToolSearchResult instantiates a new McpToolSearchResult and sets the default values.
func NewMcpToolSearchResult() *McpToolSearchResult {
	m := &McpToolSearchResult{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateMcpToolSearchResultFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateMcpToolSearchResultFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewMcpToolSearchResult(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *McpToolSearchResult) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The artifact ID of the MCP tool.
// returns a *string when successful
func (m *McpToolSearchResult) GetArtifactId() *string {
	return m.artifactId
}

// GetCreatedOn gets the createdOn property value. Timestamp when the MCP tool was created.
// returns a *int64 when successful
func (m *McpToolSearchResult) GetCreatedOn() *int64 {
	return m.createdOn
}

// GetDescription gets the description property value. A description of the MCP tool.
// returns a *string when successful
func (m *McpToolSearchResult) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *McpToolSearchResult) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["createdOn"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetCreatedOn(val)
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
	res["parameters"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
			m.SetParameters(res)
		}
		return nil
	}
	res["title"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTitle(val)
		}
		return nil
	}
	return res
}

// GetGroupId gets the groupId property value. The group ID of the MCP tool artifact.
// returns a *string when successful
func (m *McpToolSearchResult) GetGroupId() *string {
	return m.groupId
}

// GetName gets the name property value. The name of the MCP tool.
// returns a *string when successful
func (m *McpToolSearchResult) GetName() *string {
	return m.name
}

// GetOwner gets the owner property value. The owner of the MCP tool artifact.
// returns a *string when successful
func (m *McpToolSearchResult) GetOwner() *string {
	return m.owner
}

// GetParameters gets the parameters property value. Parameter names accepted by the MCP tool.
// returns a []string when successful
func (m *McpToolSearchResult) GetParameters() []string {
	return m.parameters
}

// GetTitle gets the title property value. The title of the MCP tool.
// returns a *string when successful
func (m *McpToolSearchResult) GetTitle() *string {
	return m.title
}

// Serialize serializes information the current object
func (m *McpToolSearchResult) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("artifactId", m.GetArtifactId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("createdOn", m.GetCreatedOn())
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
		err := writer.WriteStringValue("owner", m.GetOwner())
		if err != nil {
			return err
		}
	}
	if m.GetParameters() != nil {
		err := writer.WriteCollectionOfStringValues("parameters", m.GetParameters())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("title", m.GetTitle())
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
func (m *McpToolSearchResult) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The artifact ID of the MCP tool.
func (m *McpToolSearchResult) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetCreatedOn sets the createdOn property value. Timestamp when the MCP tool was created.
func (m *McpToolSearchResult) SetCreatedOn(value *int64) {
	m.createdOn = value
}

// SetDescription sets the description property value. A description of the MCP tool.
func (m *McpToolSearchResult) SetDescription(value *string) {
	m.description = value
}

// SetGroupId sets the groupId property value. The group ID of the MCP tool artifact.
func (m *McpToolSearchResult) SetGroupId(value *string) {
	m.groupId = value
}

// SetName sets the name property value. The name of the MCP tool.
func (m *McpToolSearchResult) SetName(value *string) {
	m.name = value
}

// SetOwner sets the owner property value. The owner of the MCP tool artifact.
func (m *McpToolSearchResult) SetOwner(value *string) {
	m.owner = value
}

// SetParameters sets the parameters property value. Parameter names accepted by the MCP tool.
func (m *McpToolSearchResult) SetParameters(value []string) {
	m.parameters = value
}

// SetTitle sets the title property value. The title of the MCP tool.
func (m *McpToolSearchResult) SetTitle(value *string) {
	m.title = value
}

type McpToolSearchResultable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetCreatedOn() *int64
	GetDescription() *string
	GetGroupId() *string
	GetName() *string
	GetOwner() *string
	GetParameters() []string
	GetTitle() *string
	SetArtifactId(value *string)
	SetCreatedOn(value *int64)
	SetDescription(value *string)
	SetGroupId(value *string)
	SetName(value *string)
	SetOwner(value *string)
	SetParameters(value []string)
	SetTitle(value *string)
}
