package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentSearchFilters structured filters for advanced agent search.
type AgentSearchFilters struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Filter by capabilities (key = capability name, value = required state).
	capabilities AgentSearchFilters_capabilitiesable
	// Filter by input modes.
	inputModes []string
	// Filter by labels.
	labels AgentSearchFilters_labelsable
	// Filter by output modes.
	outputModes []string
	// Filter by protocol bindings.
	protocolBindings []string
	// Filter by skill IDs.
	skills []string
}

// NewAgentSearchFilters instantiates a new AgentSearchFilters and sets the default values.
func NewAgentSearchFilters() *AgentSearchFilters {
	m := &AgentSearchFilters{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAgentSearchFiltersFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentSearchFiltersFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAgentSearchFilters(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentSearchFilters) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetCapabilities gets the capabilities property value. Filter by capabilities (key = capability name, value = required state).
// returns a AgentSearchFilters_capabilitiesable when successful
func (m *AgentSearchFilters) GetCapabilities() AgentSearchFilters_capabilitiesable {
	return m.capabilities
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentSearchFilters) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["capabilities"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateAgentSearchFilters_capabilitiesFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetCapabilities(val.(AgentSearchFilters_capabilitiesable))
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
	res["labels"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateAgentSearchFilters_labelsFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetLabels(val.(AgentSearchFilters_labelsable))
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
	res["protocolBindings"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
			m.SetProtocolBindings(res)
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
	return res
}

// GetInputModes gets the inputModes property value. Filter by input modes.
// returns a []string when successful
func (m *AgentSearchFilters) GetInputModes() []string {
	return m.inputModes
}

// GetLabels gets the labels property value. Filter by labels.
// returns a AgentSearchFilters_labelsable when successful
func (m *AgentSearchFilters) GetLabels() AgentSearchFilters_labelsable {
	return m.labels
}

// GetOutputModes gets the outputModes property value. Filter by output modes.
// returns a []string when successful
func (m *AgentSearchFilters) GetOutputModes() []string {
	return m.outputModes
}

// GetProtocolBindings gets the protocolBindings property value. Filter by protocol bindings.
// returns a []string when successful
func (m *AgentSearchFilters) GetProtocolBindings() []string {
	return m.protocolBindings
}

// GetSkills gets the skills property value. Filter by skill IDs.
// returns a []string when successful
func (m *AgentSearchFilters) GetSkills() []string {
	return m.skills
}

// Serialize serializes information the current object
func (m *AgentSearchFilters) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteObjectValue("capabilities", m.GetCapabilities())
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
		err := writer.WriteObjectValue("labels", m.GetLabels())
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
	if m.GetProtocolBindings() != nil {
		err := writer.WriteCollectionOfStringValues("protocolBindings", m.GetProtocolBindings())
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
	{
		err := writer.WriteAdditionalData(m.GetAdditionalData())
		if err != nil {
			return err
		}
	}
	return nil
}

// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *AgentSearchFilters) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetCapabilities sets the capabilities property value. Filter by capabilities (key = capability name, value = required state).
func (m *AgentSearchFilters) SetCapabilities(value AgentSearchFilters_capabilitiesable) {
	m.capabilities = value
}

// SetInputModes sets the inputModes property value. Filter by input modes.
func (m *AgentSearchFilters) SetInputModes(value []string) {
	m.inputModes = value
}

// SetLabels sets the labels property value. Filter by labels.
func (m *AgentSearchFilters) SetLabels(value AgentSearchFilters_labelsable) {
	m.labels = value
}

// SetOutputModes sets the outputModes property value. Filter by output modes.
func (m *AgentSearchFilters) SetOutputModes(value []string) {
	m.outputModes = value
}

// SetProtocolBindings sets the protocolBindings property value. Filter by protocol bindings.
func (m *AgentSearchFilters) SetProtocolBindings(value []string) {
	m.protocolBindings = value
}

// SetSkills sets the skills property value. Filter by skill IDs.
func (m *AgentSearchFilters) SetSkills(value []string) {
	m.skills = value
}

type AgentSearchFiltersable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetCapabilities() AgentSearchFilters_capabilitiesable
	GetInputModes() []string
	GetLabels() AgentSearchFilters_labelsable
	GetOutputModes() []string
	GetProtocolBindings() []string
	GetSkills() []string
	SetCapabilities(value AgentSearchFilters_capabilitiesable)
	SetInputModes(value []string)
	SetLabels(value AgentSearchFilters_labelsable)
	SetOutputModes(value []string)
	SetProtocolBindings(value []string)
	SetSkills(value []string)
}
