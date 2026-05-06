package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// UsageSummary global usage summary counts across all artifacts.
type UsageSummary struct {
	// Number of schema versions classified as ACTIVE (recently fetched).
	active *int32
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Number of schema versions classified as DEAD (not fetched in a long time or never).
	dead *int32
	// Number of schema versions classified as STALE (not recently fetched).
	stale *int32
}

// NewUsageSummary instantiates a new UsageSummary and sets the default values.
func NewUsageSummary() *UsageSummary {
	m := &UsageSummary{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateUsageSummaryFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateUsageSummaryFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewUsageSummary(), nil
}

// GetActive gets the active property value. Number of schema versions classified as ACTIVE (recently fetched).
// returns a *int32 when successful
func (m *UsageSummary) GetActive() *int32 {
	return m.active
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *UsageSummary) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetDead gets the dead property value. Number of schema versions classified as DEAD (not fetched in a long time or never).
// returns a *int32 when successful
func (m *UsageSummary) GetDead() *int32 {
	return m.dead
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *UsageSummary) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["active"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetActive(val)
		}
		return nil
	}
	res["dead"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDead(val)
		}
		return nil
	}
	res["stale"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetStale(val)
		}
		return nil
	}
	return res
}

// GetStale gets the stale property value. Number of schema versions classified as STALE (not recently fetched).
// returns a *int32 when successful
func (m *UsageSummary) GetStale() *int32 {
	return m.stale
}

// Serialize serializes information the current object
func (m *UsageSummary) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteInt32Value("active", m.GetActive())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("dead", m.GetDead())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("stale", m.GetStale())
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

// SetActive sets the active property value. Number of schema versions classified as ACTIVE (recently fetched).
func (m *UsageSummary) SetActive(value *int32) {
	m.active = value
}

// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *UsageSummary) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetDead sets the dead property value. Number of schema versions classified as DEAD (not fetched in a long time or never).
func (m *UsageSummary) SetDead(value *int32) {
	m.dead = value
}

// SetStale sets the stale property value. Number of schema versions classified as STALE (not recently fetched).
func (m *UsageSummary) SetStale(value *int32) {
	m.stale = value
}

type UsageSummaryable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetActive() *int32
	GetDead() *int32
	GetStale() *int32
	SetActive(value *int32)
	SetDead(value *int32)
	SetStale(value *int32)
}
