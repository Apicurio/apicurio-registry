package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArtifactUsageMetrics aggregated usage metrics for all versions of an artifact.
type ArtifactUsageMetrics struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifact ID.
	artifactId *string
	// The group ID of the artifact.
	groupId *string
	// Per-version usage metrics.
	versions []VersionUsageMetricsable
}

// NewArtifactUsageMetrics instantiates a new ArtifactUsageMetrics and sets the default values.
func NewArtifactUsageMetrics() *ArtifactUsageMetrics {
	m := &ArtifactUsageMetrics{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArtifactUsageMetricsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArtifactUsageMetricsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArtifactUsageMetrics(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArtifactUsageMetrics) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The artifact ID.
// returns a *string when successful
func (m *ArtifactUsageMetrics) GetArtifactId() *string {
	return m.artifactId
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArtifactUsageMetrics) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
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
	res["versions"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateVersionUsageMetricsFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]VersionUsageMetricsable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(VersionUsageMetricsable)
				}
			}
			m.SetVersions(res)
		}
		return nil
	}
	return res
}

// GetGroupId gets the groupId property value. The group ID of the artifact.
// returns a *string when successful
func (m *ArtifactUsageMetrics) GetGroupId() *string {
	return m.groupId
}

// GetVersions gets the versions property value. Per-version usage metrics.
// returns a []VersionUsageMetricsable when successful
func (m *ArtifactUsageMetrics) GetVersions() []VersionUsageMetricsable {
	return m.versions
}

// Serialize serializes information the current object
func (m *ArtifactUsageMetrics) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("artifactId", m.GetArtifactId())
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
	if m.GetVersions() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetVersions()))
		for i, v := range m.GetVersions() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("versions", cast)
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
func (m *ArtifactUsageMetrics) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The artifact ID.
func (m *ArtifactUsageMetrics) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetGroupId sets the groupId property value. The group ID of the artifact.
func (m *ArtifactUsageMetrics) SetGroupId(value *string) {
	m.groupId = value
}

// SetVersions sets the versions property value. Per-version usage metrics.
func (m *ArtifactUsageMetrics) SetVersions(value []VersionUsageMetricsable) {
	m.versions = value
}

type ArtifactUsageMetricsable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetGroupId() *string
	GetVersions() []VersionUsageMetricsable
	SetArtifactId(value *string)
	SetGroupId(value *string)
	SetVersions(value []VersionUsageMetricsable)
}
