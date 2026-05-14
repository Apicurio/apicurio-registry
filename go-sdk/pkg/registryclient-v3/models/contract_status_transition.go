package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ContractStatusTransition request body for transitioning the contract lifecycle status.
type ContractStatusTransition struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The target lifecycle status. Valid transitions: DRAFT to STABLE, DRAFT to DEPRECATED, STABLE to DEPRECATED.
	status *ContractStatusTransition_status
}

// NewContractStatusTransition instantiates a new ContractStatusTransition and sets the default values.
func NewContractStatusTransition() *ContractStatusTransition {
	m := &ContractStatusTransition{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateContractStatusTransitionFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateContractStatusTransitionFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewContractStatusTransition(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ContractStatusTransition) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ContractStatusTransition) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["status"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseContractStatusTransition_status)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetStatus(val.(*ContractStatusTransition_status))
		}
		return nil
	}
	return res
}

// GetStatus gets the status property value. The target lifecycle status. Valid transitions: DRAFT to STABLE, DRAFT to DEPRECATED, STABLE to DEPRECATED.
// returns a *ContractStatusTransition_status when successful
func (m *ContractStatusTransition) GetStatus() *ContractStatusTransition_status {
	return m.status
}

// Serialize serializes information the current object
func (m *ContractStatusTransition) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetStatus() != nil {
		cast := (*m.GetStatus()).String()
		err := writer.WriteStringValue("status", &cast)
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
func (m *ContractStatusTransition) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetStatus sets the status property value. The target lifecycle status. Valid transitions: DRAFT to STABLE, DRAFT to DEPRECATED, STABLE to DEPRECATED.
func (m *ContractStatusTransition) SetStatus(value *ContractStatusTransition_status) {
	m.status = value
}

type ContractStatusTransitionable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetStatus() *ContractStatusTransition_status
	SetStatus(value *ContractStatusTransition_status)
}
