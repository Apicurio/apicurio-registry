package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// UserInterfaceConfigFeatures
type UserInterfaceConfigFeatures struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The breadcrumbs property
	breadcrumbs *bool
	// The deleteArtifact property
	deleteArtifact *bool
	// The deleteGroup property
	deleteGroup *bool
	// The deleteVersion property
	deleteVersion *bool
	// The readOnly property
	readOnly *bool
	// The roleManagement property
	roleManagement *bool
	// The settings property
	settings *bool
}

// NewUserInterfaceConfigFeatures instantiates a new UserInterfaceConfigFeatures and sets the default values.
func NewUserInterfaceConfigFeatures() *UserInterfaceConfigFeatures {
	m := &UserInterfaceConfigFeatures{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateUserInterfaceConfigFeaturesFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateUserInterfaceConfigFeaturesFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewUserInterfaceConfigFeatures(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *UserInterfaceConfigFeatures) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetBreadcrumbs gets the breadcrumbs property value. The breadcrumbs property
func (m *UserInterfaceConfigFeatures) GetBreadcrumbs() *bool {
	return m.breadcrumbs
}

// GetDeleteArtifact gets the deleteArtifact property value. The deleteArtifact property
func (m *UserInterfaceConfigFeatures) GetDeleteArtifact() *bool {
	return m.deleteArtifact
}

// GetDeleteGroup gets the deleteGroup property value. The deleteGroup property
func (m *UserInterfaceConfigFeatures) GetDeleteGroup() *bool {
	return m.deleteGroup
}

// GetDeleteVersion gets the deleteVersion property value. The deleteVersion property
func (m *UserInterfaceConfigFeatures) GetDeleteVersion() *bool {
	return m.deleteVersion
}

// GetFieldDeserializers the deserialization information for the current model
func (m *UserInterfaceConfigFeatures) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["breadcrumbs"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetBreadcrumbs(val)
		}
		return nil
	}
	res["deleteArtifact"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDeleteArtifact(val)
		}
		return nil
	}
	res["deleteGroup"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDeleteGroup(val)
		}
		return nil
	}
	res["deleteVersion"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDeleteVersion(val)
		}
		return nil
	}
	res["readOnly"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetReadOnly(val)
		}
		return nil
	}
	res["roleManagement"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetRoleManagement(val)
		}
		return nil
	}
	res["settings"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSettings(val)
		}
		return nil
	}
	return res
}

// GetReadOnly gets the readOnly property value. The readOnly property
func (m *UserInterfaceConfigFeatures) GetReadOnly() *bool {
	return m.readOnly
}

// GetRoleManagement gets the roleManagement property value. The roleManagement property
func (m *UserInterfaceConfigFeatures) GetRoleManagement() *bool {
	return m.roleManagement
}

// GetSettings gets the settings property value. The settings property
func (m *UserInterfaceConfigFeatures) GetSettings() *bool {
	return m.settings
}

// Serialize serializes information the current object
func (m *UserInterfaceConfigFeatures) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteBoolValue("breadcrumbs", m.GetBreadcrumbs())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteBoolValue("deleteArtifact", m.GetDeleteArtifact())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteBoolValue("deleteGroup", m.GetDeleteGroup())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteBoolValue("deleteVersion", m.GetDeleteVersion())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteBoolValue("readOnly", m.GetReadOnly())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteBoolValue("roleManagement", m.GetRoleManagement())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteBoolValue("settings", m.GetSettings())
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
func (m *UserInterfaceConfigFeatures) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetBreadcrumbs sets the breadcrumbs property value. The breadcrumbs property
func (m *UserInterfaceConfigFeatures) SetBreadcrumbs(value *bool) {
	m.breadcrumbs = value
}

// SetDeleteArtifact sets the deleteArtifact property value. The deleteArtifact property
func (m *UserInterfaceConfigFeatures) SetDeleteArtifact(value *bool) {
	m.deleteArtifact = value
}

// SetDeleteGroup sets the deleteGroup property value. The deleteGroup property
func (m *UserInterfaceConfigFeatures) SetDeleteGroup(value *bool) {
	m.deleteGroup = value
}

// SetDeleteVersion sets the deleteVersion property value. The deleteVersion property
func (m *UserInterfaceConfigFeatures) SetDeleteVersion(value *bool) {
	m.deleteVersion = value
}

// SetReadOnly sets the readOnly property value. The readOnly property
func (m *UserInterfaceConfigFeatures) SetReadOnly(value *bool) {
	m.readOnly = value
}

// SetRoleManagement sets the roleManagement property value. The roleManagement property
func (m *UserInterfaceConfigFeatures) SetRoleManagement(value *bool) {
	m.roleManagement = value
}

// SetSettings sets the settings property value. The settings property
func (m *UserInterfaceConfigFeatures) SetSettings(value *bool) {
	m.settings = value
}

// UserInterfaceConfigFeaturesable
type UserInterfaceConfigFeaturesable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetBreadcrumbs() *bool
	GetDeleteArtifact() *bool
	GetDeleteGroup() *bool
	GetDeleteVersion() *bool
	GetReadOnly() *bool
	GetRoleManagement() *bool
	GetSettings() *bool
	SetBreadcrumbs(value *bool)
	SetDeleteArtifact(value *bool)
	SetDeleteGroup(value *bool)
	SetDeleteVersion(value *bool)
	SetReadOnly(value *bool)
	SetRoleManagement(value *bool)
	SetSettings(value *bool)
}
