package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ConsumerVersionEntry a consumer's usage of artifact versions.
type ConsumerVersionEntry struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The client application identifier.
	clientId *string
	// True if this consumer exceeds the drift alert threshold.
	driftAlert *bool
	// Map of version string to fetch count.
	versions ConsumerVersionEntry_versionsable
	// How many versions behind the latest this consumer is.
	versionsBehind *int32
}

// NewConsumerVersionEntry instantiates a new ConsumerVersionEntry and sets the default values.
func NewConsumerVersionEntry() *ConsumerVersionEntry {
	m := &ConsumerVersionEntry{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateConsumerVersionEntryFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateConsumerVersionEntryFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewConsumerVersionEntry(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ConsumerVersionEntry) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetClientId gets the clientId property value. The client application identifier.
// returns a *string when successful
func (m *ConsumerVersionEntry) GetClientId() *string {
	return m.clientId
}

// GetDriftAlert gets the driftAlert property value. True if this consumer exceeds the drift alert threshold.
// returns a *bool when successful
func (m *ConsumerVersionEntry) GetDriftAlert() *bool {
	return m.driftAlert
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ConsumerVersionEntry) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["clientId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetClientId(val)
		}
		return nil
	}
	res["driftAlert"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDriftAlert(val)
		}
		return nil
	}
	res["versions"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateConsumerVersionEntry_versionsFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetVersions(val.(ConsumerVersionEntry_versionsable))
		}
		return nil
	}
	res["versionsBehind"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetVersionsBehind(val)
		}
		return nil
	}
	return res
}

// GetVersions gets the versions property value. Map of version string to fetch count.
// returns a ConsumerVersionEntry_versionsable when successful
func (m *ConsumerVersionEntry) GetVersions() ConsumerVersionEntry_versionsable {
	return m.versions
}

// GetVersionsBehind gets the versionsBehind property value. How many versions behind the latest this consumer is.
// returns a *int32 when successful
func (m *ConsumerVersionEntry) GetVersionsBehind() *int32 {
	return m.versionsBehind
}

// Serialize serializes information the current object
func (m *ConsumerVersionEntry) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("clientId", m.GetClientId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteBoolValue("driftAlert", m.GetDriftAlert())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("versions", m.GetVersions())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("versionsBehind", m.GetVersionsBehind())
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
func (m *ConsumerVersionEntry) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetClientId sets the clientId property value. The client application identifier.
func (m *ConsumerVersionEntry) SetClientId(value *string) {
	m.clientId = value
}

// SetDriftAlert sets the driftAlert property value. True if this consumer exceeds the drift alert threshold.
func (m *ConsumerVersionEntry) SetDriftAlert(value *bool) {
	m.driftAlert = value
}

// SetVersions sets the versions property value. Map of version string to fetch count.
func (m *ConsumerVersionEntry) SetVersions(value ConsumerVersionEntry_versionsable) {
	m.versions = value
}

// SetVersionsBehind sets the versionsBehind property value. How many versions behind the latest this consumer is.
func (m *ConsumerVersionEntry) SetVersionsBehind(value *int32) {
	m.versionsBehind = value
}

type ConsumerVersionEntryable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetClientId() *string
	GetDriftAlert() *bool
	GetVersions() ConsumerVersionEntry_versionsable
	GetVersionsBehind() *int32
	SetClientId(value *string)
	SetDriftAlert(value *bool)
	SetVersions(value ConsumerVersionEntry_versionsable)
	SetVersionsBehind(value *int32)
}
