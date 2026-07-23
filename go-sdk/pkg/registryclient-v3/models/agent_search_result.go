package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentSearchResult a single agent card search result with metadata.
type AgentSearchResult struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifact ID of the agent card.
	artifactId *string
	// Represents the capabilities of an A2A agent.
	capabilities AgentCapabilitiesable
	// Timestamp when the agent card was created.
	createdOn *int64
	// A description of the agent.
	description *string
	// The group ID of the agent card artifact.
	groupId *string
	// The name of the agent.
	name *string
	// The owner of the agent card artifact.
	owner *string
	// Skill IDs associated with this agent.
	skills []string
	// Protocol interfaces supported by this agent.
	supportedInterfaces []AgentInterfaceable
	// The version of the agent card.
	version *string
}

// NewAgentSearchResult instantiates a new AgentSearchResult and sets the default values.
func NewAgentSearchResult() *AgentSearchResult {
	m := &AgentSearchResult{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAgentSearchResultFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentSearchResultFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAgentSearchResult(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentSearchResult) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The artifact ID of the agent card.
// returns a *string when successful
func (m *AgentSearchResult) GetArtifactId() *string {
	return m.artifactId
}

// GetCapabilities gets the capabilities property value. Represents the capabilities of an A2A agent.
// returns a AgentCapabilitiesable when successful
func (m *AgentSearchResult) GetCapabilities() AgentCapabilitiesable {
	return m.capabilities
}

// GetCreatedOn gets the createdOn property value. Timestamp when the agent card was created.
// returns a *int64 when successful
func (m *AgentSearchResult) GetCreatedOn() *int64 {
	return m.createdOn
}

// GetDescription gets the description property value. A description of the agent.
// returns a *string when successful
func (m *AgentSearchResult) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentSearchResult) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["capabilities"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateAgentCapabilitiesFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetCapabilities(val.(AgentCapabilitiesable))
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
	res["skills"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
			m.SetSkills(res)
		}
		return nil
	}
	res["supportedInterfaces"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateAgentInterfaceFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]AgentInterfaceable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(AgentInterfaceable)
				}
			}
			m.SetSupportedInterfaces(res)
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

// GetGroupId gets the groupId property value. The group ID of the agent card artifact.
// returns a *string when successful
func (m *AgentSearchResult) GetGroupId() *string {
	return m.groupId
}

// GetName gets the name property value. The name of the agent.
// returns a *string when successful
func (m *AgentSearchResult) GetName() *string {
	return m.name
}

// GetOwner gets the owner property value. The owner of the agent card artifact.
// returns a *string when successful
func (m *AgentSearchResult) GetOwner() *string {
	return m.owner
}

// GetSkills gets the skills property value. Skill IDs associated with this agent.
// returns a []string when successful
func (m *AgentSearchResult) GetSkills() []string {
	return m.skills
}

// GetSupportedInterfaces gets the supportedInterfaces property value. Protocol interfaces supported by this agent.
// returns a []AgentInterfaceable when successful
func (m *AgentSearchResult) GetSupportedInterfaces() []AgentInterfaceable {
	return m.supportedInterfaces
}

// GetVersion gets the version property value. The version of the agent card.
// returns a *string when successful
func (m *AgentSearchResult) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *AgentSearchResult) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("artifactId", m.GetArtifactId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("capabilities", m.GetCapabilities())
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
	if m.GetSkills() != nil {
		err := writer.WriteCollectionOfStringValues("skills", m.GetSkills())
		if err != nil {
			return err
		}
	}
	if m.GetSupportedInterfaces() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetSupportedInterfaces()))
		for i, v := range m.GetSupportedInterfaces() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("supportedInterfaces", cast)
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
func (m *AgentSearchResult) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The artifact ID of the agent card.
func (m *AgentSearchResult) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetCapabilities sets the capabilities property value. Represents the capabilities of an A2A agent.
func (m *AgentSearchResult) SetCapabilities(value AgentCapabilitiesable) {
	m.capabilities = value
}

// SetCreatedOn sets the createdOn property value. Timestamp when the agent card was created.
func (m *AgentSearchResult) SetCreatedOn(value *int64) {
	m.createdOn = value
}

// SetDescription sets the description property value. A description of the agent.
func (m *AgentSearchResult) SetDescription(value *string) {
	m.description = value
}

// SetGroupId sets the groupId property value. The group ID of the agent card artifact.
func (m *AgentSearchResult) SetGroupId(value *string) {
	m.groupId = value
}

// SetName sets the name property value. The name of the agent.
func (m *AgentSearchResult) SetName(value *string) {
	m.name = value
}

// SetOwner sets the owner property value. The owner of the agent card artifact.
func (m *AgentSearchResult) SetOwner(value *string) {
	m.owner = value
}

// SetSkills sets the skills property value. Skill IDs associated with this agent.
func (m *AgentSearchResult) SetSkills(value []string) {
	m.skills = value
}

// SetSupportedInterfaces sets the supportedInterfaces property value. Protocol interfaces supported by this agent.
func (m *AgentSearchResult) SetSupportedInterfaces(value []AgentInterfaceable) {
	m.supportedInterfaces = value
}

// SetVersion sets the version property value. The version of the agent card.
func (m *AgentSearchResult) SetVersion(value *string) {
	m.version = value
}

type AgentSearchResultable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetCapabilities() AgentCapabilitiesable
	GetCreatedOn() *int64
	GetDescription() *string
	GetGroupId() *string
	GetName() *string
	GetOwner() *string
	GetSkills() []string
	GetSupportedInterfaces() []AgentInterfaceable
	GetVersion() *string
	SetArtifactId(value *string)
	SetCapabilities(value AgentCapabilitiesable)
	SetCreatedOn(value *int64)
	SetDescription(value *string)
	SetGroupId(value *string)
	SetName(value *string)
	SetOwner(value *string)
	SetSkills(value []string)
	SetSupportedInterfaces(value []AgentInterfaceable)
	SetVersion(value *string)
}
