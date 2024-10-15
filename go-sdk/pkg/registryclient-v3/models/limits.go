package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// Limits list of limitations on used resources, that are applied on the current instance of Registry.Keys represent the resource type and are suffixed by the corresponding unit.Values are integers. Only non-negative values are allowed, with the exception of -1, which means that the limit is not applied.
type Limits struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The maxArtifactDescriptionLengthChars property
	maxArtifactDescriptionLengthChars *int64
	// The maxArtifactLabelsCount property
	maxArtifactLabelsCount *int64
	// The maxArtifactNameLengthChars property
	maxArtifactNameLengthChars *int64
	// The maxArtifactPropertiesCount property
	maxArtifactPropertiesCount *int64
	// The maxArtifactsCount property
	maxArtifactsCount *int64
	// The maxLabelSizeBytes property
	maxLabelSizeBytes *int64
	// The maxPropertyKeySizeBytes property
	maxPropertyKeySizeBytes *int64
	// The maxPropertyValueSizeBytes property
	maxPropertyValueSizeBytes *int64
	// The maxRequestsPerSecondCount property
	maxRequestsPerSecondCount *int64
	// The maxSchemaSizeBytes property
	maxSchemaSizeBytes *int64
	// The maxTotalSchemasCount property
	maxTotalSchemasCount *int64
	// The maxVersionsPerArtifactCount property
	maxVersionsPerArtifactCount *int64
}

// NewLimits instantiates a new Limits and sets the default values.
func NewLimits() *Limits {
	m := &Limits{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateLimitsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateLimitsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewLimits(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *Limits) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *Limits) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["maxArtifactDescriptionLengthChars"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxArtifactDescriptionLengthChars(val)
		}
		return nil
	}
	res["maxArtifactLabelsCount"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxArtifactLabelsCount(val)
		}
		return nil
	}
	res["maxArtifactNameLengthChars"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxArtifactNameLengthChars(val)
		}
		return nil
	}
	res["maxArtifactPropertiesCount"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxArtifactPropertiesCount(val)
		}
		return nil
	}
	res["maxArtifactsCount"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxArtifactsCount(val)
		}
		return nil
	}
	res["maxLabelSizeBytes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxLabelSizeBytes(val)
		}
		return nil
	}
	res["maxPropertyKeySizeBytes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxPropertyKeySizeBytes(val)
		}
		return nil
	}
	res["maxPropertyValueSizeBytes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxPropertyValueSizeBytes(val)
		}
		return nil
	}
	res["maxRequestsPerSecondCount"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxRequestsPerSecondCount(val)
		}
		return nil
	}
	res["maxSchemaSizeBytes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxSchemaSizeBytes(val)
		}
		return nil
	}
	res["maxTotalSchemasCount"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxTotalSchemasCount(val)
		}
		return nil
	}
	res["maxVersionsPerArtifactCount"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxVersionsPerArtifactCount(val)
		}
		return nil
	}
	return res
}

// GetMaxArtifactDescriptionLengthChars gets the maxArtifactDescriptionLengthChars property value. The maxArtifactDescriptionLengthChars property
// returns a *int64 when successful
func (m *Limits) GetMaxArtifactDescriptionLengthChars() *int64 {
	return m.maxArtifactDescriptionLengthChars
}

// GetMaxArtifactLabelsCount gets the maxArtifactLabelsCount property value. The maxArtifactLabelsCount property
// returns a *int64 when successful
func (m *Limits) GetMaxArtifactLabelsCount() *int64 {
	return m.maxArtifactLabelsCount
}

// GetMaxArtifactNameLengthChars gets the maxArtifactNameLengthChars property value. The maxArtifactNameLengthChars property
// returns a *int64 when successful
func (m *Limits) GetMaxArtifactNameLengthChars() *int64 {
	return m.maxArtifactNameLengthChars
}

// GetMaxArtifactPropertiesCount gets the maxArtifactPropertiesCount property value. The maxArtifactPropertiesCount property
// returns a *int64 when successful
func (m *Limits) GetMaxArtifactPropertiesCount() *int64 {
	return m.maxArtifactPropertiesCount
}

// GetMaxArtifactsCount gets the maxArtifactsCount property value. The maxArtifactsCount property
// returns a *int64 when successful
func (m *Limits) GetMaxArtifactsCount() *int64 {
	return m.maxArtifactsCount
}

// GetMaxLabelSizeBytes gets the maxLabelSizeBytes property value. The maxLabelSizeBytes property
// returns a *int64 when successful
func (m *Limits) GetMaxLabelSizeBytes() *int64 {
	return m.maxLabelSizeBytes
}

// GetMaxPropertyKeySizeBytes gets the maxPropertyKeySizeBytes property value. The maxPropertyKeySizeBytes property
// returns a *int64 when successful
func (m *Limits) GetMaxPropertyKeySizeBytes() *int64 {
	return m.maxPropertyKeySizeBytes
}

// GetMaxPropertyValueSizeBytes gets the maxPropertyValueSizeBytes property value. The maxPropertyValueSizeBytes property
// returns a *int64 when successful
func (m *Limits) GetMaxPropertyValueSizeBytes() *int64 {
	return m.maxPropertyValueSizeBytes
}

// GetMaxRequestsPerSecondCount gets the maxRequestsPerSecondCount property value. The maxRequestsPerSecondCount property
// returns a *int64 when successful
func (m *Limits) GetMaxRequestsPerSecondCount() *int64 {
	return m.maxRequestsPerSecondCount
}

// GetMaxSchemaSizeBytes gets the maxSchemaSizeBytes property value. The maxSchemaSizeBytes property
// returns a *int64 when successful
func (m *Limits) GetMaxSchemaSizeBytes() *int64 {
	return m.maxSchemaSizeBytes
}

// GetMaxTotalSchemasCount gets the maxTotalSchemasCount property value. The maxTotalSchemasCount property
// returns a *int64 when successful
func (m *Limits) GetMaxTotalSchemasCount() *int64 {
	return m.maxTotalSchemasCount
}

// GetMaxVersionsPerArtifactCount gets the maxVersionsPerArtifactCount property value. The maxVersionsPerArtifactCount property
// returns a *int64 when successful
func (m *Limits) GetMaxVersionsPerArtifactCount() *int64 {
	return m.maxVersionsPerArtifactCount
}

// Serialize serializes information the current object
func (m *Limits) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteInt64Value("maxArtifactDescriptionLengthChars", m.GetMaxArtifactDescriptionLengthChars())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("maxArtifactLabelsCount", m.GetMaxArtifactLabelsCount())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("maxArtifactNameLengthChars", m.GetMaxArtifactNameLengthChars())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("maxArtifactPropertiesCount", m.GetMaxArtifactPropertiesCount())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("maxArtifactsCount", m.GetMaxArtifactsCount())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("maxLabelSizeBytes", m.GetMaxLabelSizeBytes())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("maxPropertyKeySizeBytes", m.GetMaxPropertyKeySizeBytes())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("maxPropertyValueSizeBytes", m.GetMaxPropertyValueSizeBytes())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("maxRequestsPerSecondCount", m.GetMaxRequestsPerSecondCount())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("maxSchemaSizeBytes", m.GetMaxSchemaSizeBytes())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("maxTotalSchemasCount", m.GetMaxTotalSchemasCount())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("maxVersionsPerArtifactCount", m.GetMaxVersionsPerArtifactCount())
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
func (m *Limits) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetMaxArtifactDescriptionLengthChars sets the maxArtifactDescriptionLengthChars property value. The maxArtifactDescriptionLengthChars property
func (m *Limits) SetMaxArtifactDescriptionLengthChars(value *int64) {
	m.maxArtifactDescriptionLengthChars = value
}

// SetMaxArtifactLabelsCount sets the maxArtifactLabelsCount property value. The maxArtifactLabelsCount property
func (m *Limits) SetMaxArtifactLabelsCount(value *int64) {
	m.maxArtifactLabelsCount = value
}

// SetMaxArtifactNameLengthChars sets the maxArtifactNameLengthChars property value. The maxArtifactNameLengthChars property
func (m *Limits) SetMaxArtifactNameLengthChars(value *int64) {
	m.maxArtifactNameLengthChars = value
}

// SetMaxArtifactPropertiesCount sets the maxArtifactPropertiesCount property value. The maxArtifactPropertiesCount property
func (m *Limits) SetMaxArtifactPropertiesCount(value *int64) {
	m.maxArtifactPropertiesCount = value
}

// SetMaxArtifactsCount sets the maxArtifactsCount property value. The maxArtifactsCount property
func (m *Limits) SetMaxArtifactsCount(value *int64) {
	m.maxArtifactsCount = value
}

// SetMaxLabelSizeBytes sets the maxLabelSizeBytes property value. The maxLabelSizeBytes property
func (m *Limits) SetMaxLabelSizeBytes(value *int64) {
	m.maxLabelSizeBytes = value
}

// SetMaxPropertyKeySizeBytes sets the maxPropertyKeySizeBytes property value. The maxPropertyKeySizeBytes property
func (m *Limits) SetMaxPropertyKeySizeBytes(value *int64) {
	m.maxPropertyKeySizeBytes = value
}

// SetMaxPropertyValueSizeBytes sets the maxPropertyValueSizeBytes property value. The maxPropertyValueSizeBytes property
func (m *Limits) SetMaxPropertyValueSizeBytes(value *int64) {
	m.maxPropertyValueSizeBytes = value
}

// SetMaxRequestsPerSecondCount sets the maxRequestsPerSecondCount property value. The maxRequestsPerSecondCount property
func (m *Limits) SetMaxRequestsPerSecondCount(value *int64) {
	m.maxRequestsPerSecondCount = value
}

// SetMaxSchemaSizeBytes sets the maxSchemaSizeBytes property value. The maxSchemaSizeBytes property
func (m *Limits) SetMaxSchemaSizeBytes(value *int64) {
	m.maxSchemaSizeBytes = value
}

// SetMaxTotalSchemasCount sets the maxTotalSchemasCount property value. The maxTotalSchemasCount property
func (m *Limits) SetMaxTotalSchemasCount(value *int64) {
	m.maxTotalSchemasCount = value
}

// SetMaxVersionsPerArtifactCount sets the maxVersionsPerArtifactCount property value. The maxVersionsPerArtifactCount property
func (m *Limits) SetMaxVersionsPerArtifactCount(value *int64) {
	m.maxVersionsPerArtifactCount = value
}

type Limitsable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetMaxArtifactDescriptionLengthChars() *int64
	GetMaxArtifactLabelsCount() *int64
	GetMaxArtifactNameLengthChars() *int64
	GetMaxArtifactPropertiesCount() *int64
	GetMaxArtifactsCount() *int64
	GetMaxLabelSizeBytes() *int64
	GetMaxPropertyKeySizeBytes() *int64
	GetMaxPropertyValueSizeBytes() *int64
	GetMaxRequestsPerSecondCount() *int64
	GetMaxSchemaSizeBytes() *int64
	GetMaxTotalSchemasCount() *int64
	GetMaxVersionsPerArtifactCount() *int64
	SetMaxArtifactDescriptionLengthChars(value *int64)
	SetMaxArtifactLabelsCount(value *int64)
	SetMaxArtifactNameLengthChars(value *int64)
	SetMaxArtifactPropertiesCount(value *int64)
	SetMaxArtifactsCount(value *int64)
	SetMaxLabelSizeBytes(value *int64)
	SetMaxPropertyKeySizeBytes(value *int64)
	SetMaxPropertyValueSizeBytes(value *int64)
	SetMaxRequestsPerSecondCount(value *int64)
	SetMaxSchemaSizeBytes(value *int64)
	SetMaxTotalSchemasCount(value *int64)
	SetMaxVersionsPerArtifactCount(value *int64)
}
