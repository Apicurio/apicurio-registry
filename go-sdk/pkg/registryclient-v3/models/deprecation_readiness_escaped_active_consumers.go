package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type DeprecationReadiness_activeConsumers struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The clientId property
	clientId *string
	// The fetchCount property
	fetchCount *int64
	// The lastFetched property
	lastFetched *int64
}

// NewDeprecationReadiness_activeConsumers instantiates a new DeprecationReadiness_activeConsumers and sets the default values.
func NewDeprecationReadiness_activeConsumers() *DeprecationReadiness_activeConsumers {
	m := &DeprecationReadiness_activeConsumers{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateDeprecationReadiness_activeConsumersFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateDeprecationReadiness_activeConsumersFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewDeprecationReadiness_activeConsumers(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *DeprecationReadiness_activeConsumers) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetClientId gets the clientId property value. The clientId property
// returns a *string when successful
func (m *DeprecationReadiness_activeConsumers) GetClientId() *string {
	return m.clientId
}

// GetFetchCount gets the fetchCount property value. The fetchCount property
// returns a *int64 when successful
func (m *DeprecationReadiness_activeConsumers) GetFetchCount() *int64 {
	return m.fetchCount
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *DeprecationReadiness_activeConsumers) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["fetchCount"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetFetchCount(val)
		}
		return nil
	}
	res["lastFetched"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetLastFetched(val)
		}
		return nil
	}
	return res
}

// GetLastFetched gets the lastFetched property value. The lastFetched property
// returns a *int64 when successful
func (m *DeprecationReadiness_activeConsumers) GetLastFetched() *int64 {
	return m.lastFetched
}

// Serialize serializes information the current object
func (m *DeprecationReadiness_activeConsumers) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("clientId", m.GetClientId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("fetchCount", m.GetFetchCount())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("lastFetched", m.GetLastFetched())
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
func (m *DeprecationReadiness_activeConsumers) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetClientId sets the clientId property value. The clientId property
func (m *DeprecationReadiness_activeConsumers) SetClientId(value *string) {
	m.clientId = value
}

// SetFetchCount sets the fetchCount property value. The fetchCount property
func (m *DeprecationReadiness_activeConsumers) SetFetchCount(value *int64) {
	m.fetchCount = value
}

// SetLastFetched sets the lastFetched property value. The lastFetched property
func (m *DeprecationReadiness_activeConsumers) SetLastFetched(value *int64) {
	m.lastFetched = value
}

type DeprecationReadiness_activeConsumersable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetClientId() *string
	GetFetchCount() *int64
	GetLastFetched() *int64
	SetClientId(value *string)
	SetFetchCount(value *int64)
	SetLastFetched(value *int64)
}
