package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentSkill a skill an A2A agent can perform.
type AgentSkill struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The description property
	description *string
	// The examples property
	examples []string
	// The id property
	id *string
	// The inputModes property
	inputModes []string
	// The name property
	name *string
	// The outputModes property
	outputModes []string
	// The securityRequirements property
	securityRequirements []AgentSecurityRequirementable
	// The tags property
	tags []string
}

// NewAgentSkill instantiates a new AgentSkill and sets the default values.
func NewAgentSkill() *AgentSkill {
	m := &AgentSkill{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAgentSkillFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentSkillFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAgentSkill(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentSkill) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetDescription gets the description property value. The description property
// returns a *string when successful
func (m *AgentSkill) GetDescription() *string {
	return m.description
}

// GetExamples gets the examples property value. The examples property
// returns a []string when successful
func (m *AgentSkill) GetExamples() []string {
	return m.examples
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentSkill) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
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
	res["examples"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
			m.SetExamples(res)
		}
		return nil
	}
	res["id"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetId(val)
		}
		return nil
	}
	res["inputModes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
			m.SetInputModes(res)
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
	res["outputModes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
			m.SetOutputModes(res)
		}
		return nil
	}
	res["securityRequirements"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateAgentSecurityRequirementFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]AgentSecurityRequirementable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(AgentSecurityRequirementable)
				}
			}
			m.SetSecurityRequirements(res)
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
	return res
}

// GetId gets the id property value. The id property
// returns a *string when successful
func (m *AgentSkill) GetId() *string {
	return m.id
}

// GetInputModes gets the inputModes property value. The inputModes property
// returns a []string when successful
func (m *AgentSkill) GetInputModes() []string {
	return m.inputModes
}

// GetName gets the name property value. The name property
// returns a *string when successful
func (m *AgentSkill) GetName() *string {
	return m.name
}

// GetOutputModes gets the outputModes property value. The outputModes property
// returns a []string when successful
func (m *AgentSkill) GetOutputModes() []string {
	return m.outputModes
}

// GetSecurityRequirements gets the securityRequirements property value. The securityRequirements property
// returns a []AgentSecurityRequirementable when successful
func (m *AgentSkill) GetSecurityRequirements() []AgentSecurityRequirementable {
	return m.securityRequirements
}

// GetTags gets the tags property value. The tags property
// returns a []string when successful
func (m *AgentSkill) GetTags() []string {
	return m.tags
}

// Serialize serializes information the current object
func (m *AgentSkill) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("description", m.GetDescription())
		if err != nil {
			return err
		}
	}
	if m.GetExamples() != nil {
		err := writer.WriteCollectionOfStringValues("examples", m.GetExamples())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("id", m.GetId())
		if err != nil {
			return err
		}
	}
	if m.GetInputModes() != nil {
		err := writer.WriteCollectionOfStringValues("inputModes", m.GetInputModes())
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
	if m.GetOutputModes() != nil {
		err := writer.WriteCollectionOfStringValues("outputModes", m.GetOutputModes())
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
	if m.GetTags() != nil {
		err := writer.WriteCollectionOfStringValues("tags", m.GetTags())
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
func (m *AgentSkill) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetDescription sets the description property value. The description property
func (m *AgentSkill) SetDescription(value *string) {
	m.description = value
}

// SetExamples sets the examples property value. The examples property
func (m *AgentSkill) SetExamples(value []string) {
	m.examples = value
}

// SetId sets the id property value. The id property
func (m *AgentSkill) SetId(value *string) {
	m.id = value
}

// SetInputModes sets the inputModes property value. The inputModes property
func (m *AgentSkill) SetInputModes(value []string) {
	m.inputModes = value
}

// SetName sets the name property value. The name property
func (m *AgentSkill) SetName(value *string) {
	m.name = value
}

// SetOutputModes sets the outputModes property value. The outputModes property
func (m *AgentSkill) SetOutputModes(value []string) {
	m.outputModes = value
}

// SetSecurityRequirements sets the securityRequirements property value. The securityRequirements property
func (m *AgentSkill) SetSecurityRequirements(value []AgentSecurityRequirementable) {
	m.securityRequirements = value
}

// SetTags sets the tags property value. The tags property
func (m *AgentSkill) SetTags(value []string) {
	m.tags = value
}

type AgentSkillable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetDescription() *string
	GetExamples() []string
	GetId() *string
	GetInputModes() []string
	GetName() *string
	GetOutputModes() []string
	GetSecurityRequirements() []AgentSecurityRequirementable
	GetTags() []string
	SetDescription(value *string)
	SetExamples(value []string)
	SetId(value *string)
	SetInputModes(value []string)
	SetName(value *string)
	SetOutputModes(value []string)
	SetSecurityRequirements(value []AgentSecurityRequirementable)
	SetTags(value []string)
}
