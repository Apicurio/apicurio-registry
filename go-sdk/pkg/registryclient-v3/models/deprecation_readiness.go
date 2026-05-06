package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// DeprecationReadiness deprecation readiness report for a specific version.
type DeprecationReadiness struct {
	// List of consumers still actively using this version.
	activeConsumers []DeprecationReadiness_activeConsumersable
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifactId property
	artifactId *string
	// The globalId property
	globalId *int64
	// The groupId property
	groupId *string
	// True if no active consumers are using this version.
	safeToDeprecate *bool
	// The version property
	version *string
}

// NewDeprecationReadiness instantiates a new DeprecationReadiness and sets the default values.
func NewDeprecationReadiness() *DeprecationReadiness {
	m := &DeprecationReadiness{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateDeprecationReadinessFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateDeprecationReadinessFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewDeprecationReadiness(), nil
}

// GetActiveConsumers gets the activeConsumers property value. List of consumers still actively using this version.
// returns a []DeprecationReadiness_activeConsumersable when successful
func (m *DeprecationReadiness) GetActiveConsumers() []DeprecationReadiness_activeConsumersable {
	return m.activeConsumers
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *DeprecationReadiness) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The artifactId property
// returns a *string when successful
func (m *DeprecationReadiness) GetArtifactId() *string {
	return m.artifactId
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *DeprecationReadiness) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["activeConsumers"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateDeprecationReadiness_activeConsumersFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]DeprecationReadiness_activeConsumersable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(DeprecationReadiness_activeConsumersable)
				}
			}
			m.SetActiveConsumers(res)
		}
		return nil
	}
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
	res["globalId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetGlobalId(val)
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
	res["safeToDeprecate"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSafeToDeprecate(val)
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

// GetGlobalId gets the globalId property value. The globalId property
// returns a *int64 when successful
func (m *DeprecationReadiness) GetGlobalId() *int64 {
	return m.globalId
}

// GetGroupId gets the groupId property value. The groupId property
// returns a *string when successful
func (m *DeprecationReadiness) GetGroupId() *string {
	return m.groupId
}

// GetSafeToDeprecate gets the safeToDeprecate property value. True if no active consumers are using this version.
// returns a *bool when successful
func (m *DeprecationReadiness) GetSafeToDeprecate() *bool {
	return m.safeToDeprecate
}

// GetVersion gets the version property value. The version property
// returns a *string when successful
func (m *DeprecationReadiness) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *DeprecationReadiness) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetActiveConsumers() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetActiveConsumers()))
		for i, v := range m.GetActiveConsumers() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("activeConsumers", cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("artifactId", m.GetArtifactId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("globalId", m.GetGlobalId())
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
		err := writer.WriteBoolValue("safeToDeprecate", m.GetSafeToDeprecate())
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

// SetActiveConsumers sets the activeConsumers property value. List of consumers still actively using this version.
func (m *DeprecationReadiness) SetActiveConsumers(value []DeprecationReadiness_activeConsumersable) {
	m.activeConsumers = value
}

// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *DeprecationReadiness) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The artifactId property
func (m *DeprecationReadiness) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetGlobalId sets the globalId property value. The globalId property
func (m *DeprecationReadiness) SetGlobalId(value *int64) {
	m.globalId = value
}

// SetGroupId sets the groupId property value. The groupId property
func (m *DeprecationReadiness) SetGroupId(value *string) {
	m.groupId = value
}

// SetSafeToDeprecate sets the safeToDeprecate property value. True if no active consumers are using this version.
func (m *DeprecationReadiness) SetSafeToDeprecate(value *bool) {
	m.safeToDeprecate = value
}

// SetVersion sets the version property value. The version property
func (m *DeprecationReadiness) SetVersion(value *string) {
	m.version = value
}

type DeprecationReadinessable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetActiveConsumers() []DeprecationReadiness_activeConsumersable
	GetArtifactId() *string
	GetGlobalId() *int64
	GetGroupId() *string
	GetSafeToDeprecate() *bool
	GetVersion() *string
	SetActiveConsumers(value []DeprecationReadiness_activeConsumersable)
	SetArtifactId(value *string)
	SetGlobalId(value *int64)
	SetGroupId(value *string)
	SetSafeToDeprecate(value *bool)
	SetVersion(value *string)
}
