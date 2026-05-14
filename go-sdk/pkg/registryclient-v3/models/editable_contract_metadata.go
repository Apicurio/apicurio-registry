package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// EditableContractMetadata editable contract metadata fields.
type EditableContractMetadata struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Data classification level.
	classification *EditableContractMetadata_classification
	// The domain the contract belongs to.
	ownerDomain *string
	// The team that owns the contract.
	ownerTeam *string
	// Promotion stage.
	stage *EditableContractMetadata_stage
	// The contract lifecycle status.
	status *EditableContractMetadata_status
	// Support contact email.
	supportContact *string
}

// NewEditableContractMetadata instantiates a new EditableContractMetadata and sets the default values.
func NewEditableContractMetadata() *EditableContractMetadata {
	m := &EditableContractMetadata{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateEditableContractMetadataFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateEditableContractMetadataFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewEditableContractMetadata(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *EditableContractMetadata) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetClassification gets the classification property value. Data classification level.
// returns a *EditableContractMetadata_classification when successful
func (m *EditableContractMetadata) GetClassification() *EditableContractMetadata_classification {
	return m.classification
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *EditableContractMetadata) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["classification"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseEditableContractMetadata_classification)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetClassification(val.(*EditableContractMetadata_classification))
		}
		return nil
	}
	res["ownerDomain"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetOwnerDomain(val)
		}
		return nil
	}
	res["ownerTeam"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetOwnerTeam(val)
		}
		return nil
	}
	res["stage"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseEditableContractMetadata_stage)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetStage(val.(*EditableContractMetadata_stage))
		}
		return nil
	}
	res["status"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseEditableContractMetadata_status)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetStatus(val.(*EditableContractMetadata_status))
		}
		return nil
	}
	res["supportContact"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSupportContact(val)
		}
		return nil
	}
	return res
}

// GetOwnerDomain gets the ownerDomain property value. The domain the contract belongs to.
// returns a *string when successful
func (m *EditableContractMetadata) GetOwnerDomain() *string {
	return m.ownerDomain
}

// GetOwnerTeam gets the ownerTeam property value. The team that owns the contract.
// returns a *string when successful
func (m *EditableContractMetadata) GetOwnerTeam() *string {
	return m.ownerTeam
}

// GetStage gets the stage property value. Promotion stage.
// returns a *EditableContractMetadata_stage when successful
func (m *EditableContractMetadata) GetStage() *EditableContractMetadata_stage {
	return m.stage
}

// GetStatus gets the status property value. The contract lifecycle status.
// returns a *EditableContractMetadata_status when successful
func (m *EditableContractMetadata) GetStatus() *EditableContractMetadata_status {
	return m.status
}

// GetSupportContact gets the supportContact property value. Support contact email.
// returns a *string when successful
func (m *EditableContractMetadata) GetSupportContact() *string {
	return m.supportContact
}

// Serialize serializes information the current object
func (m *EditableContractMetadata) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetClassification() != nil {
		cast := (*m.GetClassification()).String()
		err := writer.WriteStringValue("classification", &cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("ownerDomain", m.GetOwnerDomain())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("ownerTeam", m.GetOwnerTeam())
		if err != nil {
			return err
		}
	}
	if m.GetStage() != nil {
		cast := (*m.GetStage()).String()
		err := writer.WriteStringValue("stage", &cast)
		if err != nil {
			return err
		}
	}
	if m.GetStatus() != nil {
		cast := (*m.GetStatus()).String()
		err := writer.WriteStringValue("status", &cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("supportContact", m.GetSupportContact())
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
func (m *EditableContractMetadata) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetClassification sets the classification property value. Data classification level.
func (m *EditableContractMetadata) SetClassification(value *EditableContractMetadata_classification) {
	m.classification = value
}

// SetOwnerDomain sets the ownerDomain property value. The domain the contract belongs to.
func (m *EditableContractMetadata) SetOwnerDomain(value *string) {
	m.ownerDomain = value
}

// SetOwnerTeam sets the ownerTeam property value. The team that owns the contract.
func (m *EditableContractMetadata) SetOwnerTeam(value *string) {
	m.ownerTeam = value
}

// SetStage sets the stage property value. Promotion stage.
func (m *EditableContractMetadata) SetStage(value *EditableContractMetadata_stage) {
	m.stage = value
}

// SetStatus sets the status property value. The contract lifecycle status.
func (m *EditableContractMetadata) SetStatus(value *EditableContractMetadata_status) {
	m.status = value
}

// SetSupportContact sets the supportContact property value. Support contact email.
func (m *EditableContractMetadata) SetSupportContact(value *string) {
	m.supportContact = value
}

type EditableContractMetadataable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetClassification() *EditableContractMetadata_classification
	GetOwnerDomain() *string
	GetOwnerTeam() *string
	GetStage() *EditableContractMetadata_stage
	GetStatus() *EditableContractMetadata_status
	GetSupportContact() *string
	SetClassification(value *EditableContractMetadata_classification)
	SetOwnerDomain(value *string)
	SetOwnerTeam(value *string)
	SetStage(value *EditableContractMetadata_stage)
	SetStatus(value *EditableContractMetadata_status)
	SetSupportContact(value *string)
}
