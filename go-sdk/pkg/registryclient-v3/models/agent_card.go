package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentCard represents an A2A Agent Card - a JSON metadata document describing an AI agent following the A2A (Agent2Agent) protocol specification.
type AgentCard struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Represents the capabilities of an A2A agent.
	capabilities AgentCapabilitiesable
	// The default input modes supported by this agent.
	defaultInputModes []string
	// The default output modes supported by this agent.
	defaultOutputModes []string
	// A human-readable description of the agent.
	description *string
	// URL to the agent's documentation.
	documentationUrl *string
	// URL to the agent's icon.
	iconUrl *string
	// The name of the agent.
	name *string
	// The version of the A2A protocol supported.
	protocolVersion *string
	// Represents the provider/organization that created an A2A agent.
	provider AgentProviderable
	// Security requirements for accessing this agent.
	securityRequirements []A2aSecurityRequirementable
	// Named security schemes available for this agent.
	securitySchemes AgentCard_securitySchemesable
	// Cryptographic signatures on the agent card.
	signatures []AgentCardSignatureable
	// The list of skills this agent can perform.
	skills []AgentSkillable
	// The protocol interfaces this agent supports.
	supportedInterfaces []AgentInterfaceable
	// The version of the agent.
	version *string
}

// NewAgentCard instantiates a new AgentCard and sets the default values.
func NewAgentCard() *AgentCard {
	m := &AgentCard{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAgentCardFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentCardFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAgentCard(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentCard) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetCapabilities gets the capabilities property value. Represents the capabilities of an A2A agent.
// returns a AgentCapabilitiesable when successful
func (m *AgentCard) GetCapabilities() AgentCapabilitiesable {
	return m.capabilities
}

// GetDefaultInputModes gets the defaultInputModes property value. The default input modes supported by this agent.
// returns a []string when successful
func (m *AgentCard) GetDefaultInputModes() []string {
	return m.defaultInputModes
}

// GetDefaultOutputModes gets the defaultOutputModes property value. The default output modes supported by this agent.
// returns a []string when successful
func (m *AgentCard) GetDefaultOutputModes() []string {
	return m.defaultOutputModes
}

// GetDescription gets the description property value. A human-readable description of the agent.
// returns a *string when successful
func (m *AgentCard) GetDescription() *string {
	return m.description
}

// GetDocumentationUrl gets the documentationUrl property value. URL to the agent's documentation.
// returns a *string when successful
func (m *AgentCard) GetDocumentationUrl() *string {
	return m.documentationUrl
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentCard) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
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
	res["defaultInputModes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
			m.SetDefaultInputModes(res)
		}
		return nil
	}
	res["defaultOutputModes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
			m.SetDefaultOutputModes(res)
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
	res["iconUrl"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetIconUrl(val)
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
	res["protocolVersion"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetProtocolVersion(val)
		}
		return nil
	}
	res["provider"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateAgentProviderFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetProvider(val.(AgentProviderable))
		}
		return nil
	}
	res["securityRequirements"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateA2aSecurityRequirementFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]A2aSecurityRequirementable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(A2aSecurityRequirementable)
				}
			}
			m.SetSecurityRequirements(res)
		}
		return nil
	}
	res["securitySchemes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateAgentCard_securitySchemesFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSecuritySchemes(val.(AgentCard_securitySchemesable))
		}
		return nil
	}
	res["signatures"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateAgentCardSignatureFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]AgentCardSignatureable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(AgentCardSignatureable)
				}
			}
			m.SetSignatures(res)
		}
		return nil
	}
	res["skills"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateAgentSkillFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]AgentSkillable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(AgentSkillable)
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

// GetIconUrl gets the iconUrl property value. URL to the agent's icon.
// returns a *string when successful
func (m *AgentCard) GetIconUrl() *string {
	return m.iconUrl
}

// GetName gets the name property value. The name of the agent.
// returns a *string when successful
func (m *AgentCard) GetName() *string {
	return m.name
}

// GetProtocolVersion gets the protocolVersion property value. The version of the A2A protocol supported.
// returns a *string when successful
func (m *AgentCard) GetProtocolVersion() *string {
	return m.protocolVersion
}

// GetProvider gets the provider property value. Represents the provider/organization that created an A2A agent.
// returns a AgentProviderable when successful
func (m *AgentCard) GetProvider() AgentProviderable {
	return m.provider
}

// GetSecurityRequirements gets the securityRequirements property value. Security requirements for accessing this agent.
// returns a []A2aSecurityRequirementable when successful
func (m *AgentCard) GetSecurityRequirements() []A2aSecurityRequirementable {
	return m.securityRequirements
}

// GetSecuritySchemes gets the securitySchemes property value. Named security schemes available for this agent.
// returns a AgentCard_securitySchemesable when successful
func (m *AgentCard) GetSecuritySchemes() AgentCard_securitySchemesable {
	return m.securitySchemes
}

// GetSignatures gets the signatures property value. Cryptographic signatures on the agent card.
// returns a []AgentCardSignatureable when successful
func (m *AgentCard) GetSignatures() []AgentCardSignatureable {
	return m.signatures
}

// GetSkills gets the skills property value. The list of skills this agent can perform.
// returns a []AgentSkillable when successful
func (m *AgentCard) GetSkills() []AgentSkillable {
	return m.skills
}

// GetSupportedInterfaces gets the supportedInterfaces property value. The protocol interfaces this agent supports.
// returns a []AgentInterfaceable when successful
func (m *AgentCard) GetSupportedInterfaces() []AgentInterfaceable {
	return m.supportedInterfaces
}

// GetVersion gets the version property value. The version of the agent.
// returns a *string when successful
func (m *AgentCard) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *AgentCard) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteObjectValue("capabilities", m.GetCapabilities())
		if err != nil {
			return err
		}
	}
	if m.GetDefaultInputModes() != nil {
		err := writer.WriteCollectionOfStringValues("defaultInputModes", m.GetDefaultInputModes())
		if err != nil {
			return err
		}
	}
	if m.GetDefaultOutputModes() != nil {
		err := writer.WriteCollectionOfStringValues("defaultOutputModes", m.GetDefaultOutputModes())
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
		err := writer.WriteStringValue("documentationUrl", m.GetDocumentationUrl())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("iconUrl", m.GetIconUrl())
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
		err := writer.WriteStringValue("protocolVersion", m.GetProtocolVersion())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("provider", m.GetProvider())
		if err != nil {
			return err
		}
	}
	if m.GetSecurityRequirements() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetSecurityRequirements()))
		for i, v := range m.GetSecurityRequirements() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("securityRequirements", cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("securitySchemes", m.GetSecuritySchemes())
		if err != nil {
			return err
		}
	}
	if m.GetSignatures() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetSignatures()))
		for i, v := range m.GetSignatures() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("signatures", cast)
		if err != nil {
			return err
		}
	}
	if m.GetSkills() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetSkills()))
		for i, v := range m.GetSkills() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("skills", cast)
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
func (m *AgentCard) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetCapabilities sets the capabilities property value. Represents the capabilities of an A2A agent.
func (m *AgentCard) SetCapabilities(value AgentCapabilitiesable) {
	m.capabilities = value
}

// SetDefaultInputModes sets the defaultInputModes property value. The default input modes supported by this agent.
func (m *AgentCard) SetDefaultInputModes(value []string) {
	m.defaultInputModes = value
}

// SetDefaultOutputModes sets the defaultOutputModes property value. The default output modes supported by this agent.
func (m *AgentCard) SetDefaultOutputModes(value []string) {
	m.defaultOutputModes = value
}

// SetDescription sets the description property value. A human-readable description of the agent.
func (m *AgentCard) SetDescription(value *string) {
	m.description = value
}

// SetDocumentationUrl sets the documentationUrl property value. URL to the agent's documentation.
func (m *AgentCard) SetDocumentationUrl(value *string) {
	m.documentationUrl = value
}

// SetIconUrl sets the iconUrl property value. URL to the agent's icon.
func (m *AgentCard) SetIconUrl(value *string) {
	m.iconUrl = value
}

// SetName sets the name property value. The name of the agent.
func (m *AgentCard) SetName(value *string) {
	m.name = value
}

// SetProtocolVersion sets the protocolVersion property value. The version of the A2A protocol supported.
func (m *AgentCard) SetProtocolVersion(value *string) {
	m.protocolVersion = value
}

// SetProvider sets the provider property value. Represents the provider/organization that created an A2A agent.
func (m *AgentCard) SetProvider(value AgentProviderable) {
	m.provider = value
}

// SetSecurityRequirements sets the securityRequirements property value. Security requirements for accessing this agent.
func (m *AgentCard) SetSecurityRequirements(value []A2aSecurityRequirementable) {
	m.securityRequirements = value
}

// SetSecuritySchemes sets the securitySchemes property value. Named security schemes available for this agent.
func (m *AgentCard) SetSecuritySchemes(value AgentCard_securitySchemesable) {
	m.securitySchemes = value
}

// SetSignatures sets the signatures property value. Cryptographic signatures on the agent card.
func (m *AgentCard) SetSignatures(value []AgentCardSignatureable) {
	m.signatures = value
}

// SetSkills sets the skills property value. The list of skills this agent can perform.
func (m *AgentCard) SetSkills(value []AgentSkillable) {
	m.skills = value
}

// SetSupportedInterfaces sets the supportedInterfaces property value. The protocol interfaces this agent supports.
func (m *AgentCard) SetSupportedInterfaces(value []AgentInterfaceable) {
	m.supportedInterfaces = value
}

// SetVersion sets the version property value. The version of the agent.
func (m *AgentCard) SetVersion(value *string) {
	m.version = value
}

type AgentCardable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetCapabilities() AgentCapabilitiesable
	GetDefaultInputModes() []string
	GetDefaultOutputModes() []string
	GetDescription() *string
	GetDocumentationUrl() *string
	GetIconUrl() *string
	GetName() *string
	GetProtocolVersion() *string
	GetProvider() AgentProviderable
	GetSecurityRequirements() []A2aSecurityRequirementable
	GetSecuritySchemes() AgentCard_securitySchemesable
	GetSignatures() []AgentCardSignatureable
	GetSkills() []AgentSkillable
	GetSupportedInterfaces() []AgentInterfaceable
	GetVersion() *string
	SetCapabilities(value AgentCapabilitiesable)
	SetDefaultInputModes(value []string)
	SetDefaultOutputModes(value []string)
	SetDescription(value *string)
	SetDocumentationUrl(value *string)
	SetIconUrl(value *string)
	SetName(value *string)
	SetProtocolVersion(value *string)
	SetProvider(value AgentProviderable)
	SetSecurityRequirements(value []A2aSecurityRequirementable)
	SetSecuritySchemes(value AgentCard_securitySchemesable)
	SetSignatures(value []AgentCardSignatureable)
	SetSkills(value []AgentSkillable)
	SetSupportedInterfaces(value []AgentInterfaceable)
	SetVersion(value *string)
}
