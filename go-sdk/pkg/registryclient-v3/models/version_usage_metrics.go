package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// VersionUsageMetrics usage metrics for a specific artifact version.
type VersionUsageMetrics struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Classification of schema usage based on last fetch time.
	classification *UsageClassification
	// List of client IDs that fetched this version.
	clients []string
	// Epoch milliseconds of the first recorded fetch.
	firstFetchedOn *int64
	// The global ID of the artifact version.
	globalId *int64
	// Epoch milliseconds of the most recent fetch.
	lastFetchedOn *int64
	// Total number of schema resolution events for this version.
	totalFetches *int64
	// Number of distinct clients that fetched this version.
	uniqueClients *int32
	// The version string.
	version *string
}

// NewVersionUsageMetrics instantiates a new VersionUsageMetrics and sets the default values.
func NewVersionUsageMetrics() *VersionUsageMetrics {
	m := &VersionUsageMetrics{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateVersionUsageMetricsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateVersionUsageMetricsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewVersionUsageMetrics(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *VersionUsageMetrics) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetClassification gets the classification property value. Classification of schema usage based on last fetch time.
// returns a *UsageClassification when successful
func (m *VersionUsageMetrics) GetClassification() *UsageClassification {
	return m.classification
}

// GetClients gets the clients property value. List of client IDs that fetched this version.
// returns a []string when successful
func (m *VersionUsageMetrics) GetClients() []string {
	return m.clients
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *VersionUsageMetrics) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["classification"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseUsageClassification)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetClassification(val.(*UsageClassification))
		}
		return nil
	}
	res["clients"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfPrimitiveValues("string")
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]string, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = *(v.(*string))
				}
			}
			m.SetClients(res)
		}
		return nil
	}
	res["firstFetchedOn"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetFirstFetchedOn(val)
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
	res["lastFetchedOn"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetLastFetchedOn(val)
		}
		return nil
	}
	res["totalFetches"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTotalFetches(val)
		}
		return nil
	}
	res["uniqueClients"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetUniqueClients(val)
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

// GetFirstFetchedOn gets the firstFetchedOn property value. Epoch milliseconds of the first recorded fetch.
// returns a *int64 when successful
func (m *VersionUsageMetrics) GetFirstFetchedOn() *int64 {
	return m.firstFetchedOn
}

// GetGlobalId gets the globalId property value. The global ID of the artifact version.
// returns a *int64 when successful
func (m *VersionUsageMetrics) GetGlobalId() *int64 {
	return m.globalId
}

// GetLastFetchedOn gets the lastFetchedOn property value. Epoch milliseconds of the most recent fetch.
// returns a *int64 when successful
func (m *VersionUsageMetrics) GetLastFetchedOn() *int64 {
	return m.lastFetchedOn
}

// GetTotalFetches gets the totalFetches property value. Total number of schema resolution events for this version.
// returns a *int64 when successful
func (m *VersionUsageMetrics) GetTotalFetches() *int64 {
	return m.totalFetches
}

// GetUniqueClients gets the uniqueClients property value. Number of distinct clients that fetched this version.
// returns a *int32 when successful
func (m *VersionUsageMetrics) GetUniqueClients() *int32 {
	return m.uniqueClients
}

// GetVersion gets the version property value. The version string.
// returns a *string when successful
func (m *VersionUsageMetrics) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *VersionUsageMetrics) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetClassification() != nil {
		cast := (*m.GetClassification()).String()
		err := writer.WriteStringValue("classification", &cast)
		if err != nil {
			return err
		}
	}
	if m.GetClients() != nil {
		err := writer.WriteCollectionOfStringValues("clients", m.GetClients())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("firstFetchedOn", m.GetFirstFetchedOn())
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
		err := writer.WriteInt64Value("lastFetchedOn", m.GetLastFetchedOn())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("totalFetches", m.GetTotalFetches())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("uniqueClients", m.GetUniqueClients())
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

// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *VersionUsageMetrics) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetClassification sets the classification property value. Classification of schema usage based on last fetch time.
func (m *VersionUsageMetrics) SetClassification(value *UsageClassification) {
	m.classification = value
}

// SetClients sets the clients property value. List of client IDs that fetched this version.
func (m *VersionUsageMetrics) SetClients(value []string) {
	m.clients = value
}

// SetFirstFetchedOn sets the firstFetchedOn property value. Epoch milliseconds of the first recorded fetch.
func (m *VersionUsageMetrics) SetFirstFetchedOn(value *int64) {
	m.firstFetchedOn = value
}

// SetGlobalId sets the globalId property value. The global ID of the artifact version.
func (m *VersionUsageMetrics) SetGlobalId(value *int64) {
	m.globalId = value
}

// SetLastFetchedOn sets the lastFetchedOn property value. Epoch milliseconds of the most recent fetch.
func (m *VersionUsageMetrics) SetLastFetchedOn(value *int64) {
	m.lastFetchedOn = value
}

// SetTotalFetches sets the totalFetches property value. Total number of schema resolution events for this version.
func (m *VersionUsageMetrics) SetTotalFetches(value *int64) {
	m.totalFetches = value
}

// SetUniqueClients sets the uniqueClients property value. Number of distinct clients that fetched this version.
func (m *VersionUsageMetrics) SetUniqueClients(value *int32) {
	m.uniqueClients = value
}

// SetVersion sets the version property value. The version string.
func (m *VersionUsageMetrics) SetVersion(value *string) {
	m.version = value
}

type VersionUsageMetricsable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetClassification() *UsageClassification
	GetClients() []string
	GetFirstFetchedOn() *int64
	GetGlobalId() *int64
	GetLastFetchedOn() *int64
	GetTotalFetches() *int64
	GetUniqueClients() *int32
	GetVersion() *string
	SetClassification(value *UsageClassification)
	SetClients(value []string)
	SetFirstFetchedOn(value *int64)
	SetGlobalId(value *int64)
	SetLastFetchedOn(value *int64)
	SetTotalFetches(value *int64)
	SetUniqueClients(value *int32)
	SetVersion(value *string)
}
