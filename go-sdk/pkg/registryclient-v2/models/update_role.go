package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type UpdateRole struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The role property
	role *RoleType
}

// NewUpdateRole instantiates a new UpdateRole and sets the default values.
func NewUpdateRole() *UpdateRole {
	m := &UpdateRole{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateUpdateRoleFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateUpdateRoleFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewUpdateRole(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *UpdateRole) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *UpdateRole) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["role"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseRoleType)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetRole(val.(*RoleType))
		}
		return nil
	}
	return res
}

// GetRole gets the role property value. The role property
// returns a *RoleType when successful
func (m *UpdateRole) GetRole() *RoleType {
	return m.role
}

// Serialize serializes information the current object
func (m *UpdateRole) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetRole() != nil {
		cast := (*m.GetRole()).String()
		err := writer.WriteStringValue("role", &cast)
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
func (m *UpdateRole) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetRole sets the role property value. The role property
func (m *UpdateRole) SetRole(value *RoleType) {
	m.role = value
}

type UpdateRoleable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetRole() *RoleType
	SetRole(value *RoleType)
}
