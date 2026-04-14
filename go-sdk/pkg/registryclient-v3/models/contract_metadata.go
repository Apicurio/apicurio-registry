package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ContractMetadata contract metadata for an artifact.
type ContractMetadata struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Data classification level.
	classification *ContractMetadata_classification
	// ISO-8601 date when contract was deprecated.
	deprecatedDate *string
	// Reason for deprecation.
	deprecationReason *string
	// The domain the contract belongs to.
	ownerDomain *string
	// The team that owns the contract.
	ownerTeam *string
	// ISO-8601 date when contract became stable.
	stableDate *string
	// Promotion stage.
	stage *ContractMetadata_stage
	// The contract lifecycle status.
	status *ContractMetadata_status
	// Support contact email.
	supportContact *string
}

// NewContractMetadata instantiates a new ContractMetadata and sets the default values.
func NewContractMetadata() *ContractMetadata {
	m := &ContractMetadata{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateContractMetadataFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateContractMetadataFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewContractMetadata(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ContractMetadata) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetClassification gets the classification property value. Data classification level.
// returns a *ContractMetadata_classification when successful
func (m *ContractMetadata) GetClassification() *ContractMetadata_classification {
	return m.classification
}

// GetDeprecatedDate gets the deprecatedDate property value. ISO-8601 date when contract was deprecated.
// returns a *string when successful
func (m *ContractMetadata) GetDeprecatedDate() *string {
	return m.deprecatedDate
}

// GetDeprecationReason gets the deprecationReason property value. Reason for deprecation.
// returns a *string when successful
func (m *ContractMetadata) GetDeprecationReason() *string {
	return m.deprecationReason
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ContractMetadata) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["classification"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseContractMetadata_classification)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetClassification(val.(*ContractMetadata_classification))
		}
		return nil
	}
	res["deprecatedDate"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDeprecatedDate(val)
		}
		return nil
	}
	res["deprecationReason"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDeprecationReason(val)
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
	res["stableDate"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetStableDate(val)
		}
		return nil
	}
	res["stage"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseContractMetadata_stage)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetStage(val.(*ContractMetadata_stage))
		}
		return nil
	}
	res["status"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseContractMetadata_status)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetStatus(val.(*ContractMetadata_status))
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
func (m *ContractMetadata) GetOwnerDomain() *string {
	return m.ownerDomain
}

// GetOwnerTeam gets the ownerTeam property value. The team that owns the contract.
// returns a *string when successful
func (m *ContractMetadata) GetOwnerTeam() *string {
	return m.ownerTeam
}

// GetStableDate gets the stableDate property value. ISO-8601 date when contract became stable.
// returns a *string when successful
func (m *ContractMetadata) GetStableDate() *string {
	return m.stableDate
}

// GetStage gets the stage property value. Promotion stage.
// returns a *ContractMetadata_stage when successful
func (m *ContractMetadata) GetStage() *ContractMetadata_stage {
	return m.stage
}

// GetStatus gets the status property value. The contract lifecycle status.
// returns a *ContractMetadata_status when successful
func (m *ContractMetadata) GetStatus() *ContractMetadata_status {
	return m.status
}

// GetSupportContact gets the supportContact property value. Support contact email.
// returns a *string when successful
func (m *ContractMetadata) GetSupportContact() *string {
	return m.supportContact
}

// Serialize serializes information the current object
func (m *ContractMetadata) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetClassification() != nil {
		cast := (*m.GetClassification()).String()
		err := writer.WriteStringValue("classification", &cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("deprecatedDate", m.GetDeprecatedDate())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("deprecationReason", m.GetDeprecationReason())
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
	{
		err := writer.WriteStringValue("stableDate", m.GetStableDate())
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
func (m *ContractMetadata) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetClassification sets the classification property value. Data classification level.
func (m *ContractMetadata) SetClassification(value *ContractMetadata_classification) {
	m.classification = value
}

// SetDeprecatedDate sets the deprecatedDate property value. ISO-8601 date when contract was deprecated.
func (m *ContractMetadata) SetDeprecatedDate(value *string) {
	m.deprecatedDate = value
}

// SetDeprecationReason sets the deprecationReason property value. Reason for deprecation.
func (m *ContractMetadata) SetDeprecationReason(value *string) {
	m.deprecationReason = value
}

// SetOwnerDomain sets the ownerDomain property value. The domain the contract belongs to.
func (m *ContractMetadata) SetOwnerDomain(value *string) {
	m.ownerDomain = value
}

// SetOwnerTeam sets the ownerTeam property value. The team that owns the contract.
func (m *ContractMetadata) SetOwnerTeam(value *string) {
	m.ownerTeam = value
}

// SetStableDate sets the stableDate property value. ISO-8601 date when contract became stable.
func (m *ContractMetadata) SetStableDate(value *string) {
	m.stableDate = value
}

// SetStage sets the stage property value. Promotion stage.
func (m *ContractMetadata) SetStage(value *ContractMetadata_stage) {
	m.stage = value
}

// SetStatus sets the status property value. The contract lifecycle status.
func (m *ContractMetadata) SetStatus(value *ContractMetadata_status) {
	m.status = value
}

// SetSupportContact sets the supportContact property value. Support contact email.
func (m *ContractMetadata) SetSupportContact(value *string) {
	m.supportContact = value
}

type ContractMetadataable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetClassification() *ContractMetadata_classification
	GetDeprecatedDate() *string
	GetDeprecationReason() *string
	GetOwnerDomain() *string
	GetOwnerTeam() *string
	GetStableDate() *string
	GetStage() *ContractMetadata_stage
	GetStatus() *ContractMetadata_status
	GetSupportContact() *string
	SetClassification(value *ContractMetadata_classification)
	SetDeprecatedDate(value *string)
	SetDeprecationReason(value *string)
	SetOwnerDomain(value *string)
	SetOwnerTeam(value *string)
	SetStableDate(value *string)
	SetStage(value *ContractMetadata_stage)
	SetStatus(value *ContractMetadata_status)
	SetSupportContact(value *string)
}
