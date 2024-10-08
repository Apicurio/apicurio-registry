package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// RoleMapping the mapping between a user/principal and their role.
type RoleMapping struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The principalId property
	principalId *string
	// A friendly name for the principal.
	principalName *string
	// The role property
	role *RoleType
}

// NewRoleMapping instantiates a new RoleMapping and sets the default values.
func NewRoleMapping() *RoleMapping {
	m := &RoleMapping{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateRoleMappingFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateRoleMappingFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewRoleMapping(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *RoleMapping) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *RoleMapping) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["principalId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetPrincipalId(val)
		}
		return nil
	}
	res["principalName"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetPrincipalName(val)
		}
		return nil
	}
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

// GetPrincipalId gets the principalId property value. The principalId property
// returns a *string when successful
func (m *RoleMapping) GetPrincipalId() *string {
	return m.principalId
}

// GetPrincipalName gets the principalName property value. A friendly name for the principal.
// returns a *string when successful
func (m *RoleMapping) GetPrincipalName() *string {
	return m.principalName
}

// GetRole gets the role property value. The role property
// returns a *RoleType when successful
func (m *RoleMapping) GetRole() *RoleType {
	return m.role
}

// Serialize serializes information the current object
func (m *RoleMapping) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("principalId", m.GetPrincipalId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("principalName", m.GetPrincipalName())
		if err != nil {
			return err
		}
	}
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
func (m *RoleMapping) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetPrincipalId sets the principalId property value. The principalId property
func (m *RoleMapping) SetPrincipalId(value *string) {
	m.principalId = value
}

// SetPrincipalName sets the principalName property value. A friendly name for the principal.
func (m *RoleMapping) SetPrincipalName(value *string) {
	m.principalName = value
}

// SetRole sets the role property value. The role property
func (m *RoleMapping) SetRole(value *RoleType) {
	m.role = value
}

type RoleMappingable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetPrincipalId() *string
	GetPrincipalName() *string
	GetRole() *RoleType
	SetPrincipalId(value *string)
	SetPrincipalName(value *string)
	SetRole(value *RoleType)
}
