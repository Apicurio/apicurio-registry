package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ConsumerVersionHeatmap consumer version adoption heatmap for an artifact.
type ConsumerVersionHeatmap struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifactId property
	artifactId *string
	// Per-consumer version usage entries.
	consumers []ConsumerVersionEntryable
	// The groupId property
	groupId *string
	// Ordered list of version strings.
	versions []string
}

// NewConsumerVersionHeatmap instantiates a new ConsumerVersionHeatmap and sets the default values.
func NewConsumerVersionHeatmap() *ConsumerVersionHeatmap {
	m := &ConsumerVersionHeatmap{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateConsumerVersionHeatmapFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateConsumerVersionHeatmapFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewConsumerVersionHeatmap(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ConsumerVersionHeatmap) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The artifactId property
// returns a *string when successful
func (m *ConsumerVersionHeatmap) GetArtifactId() *string {
	return m.artifactId
}

// GetConsumers gets the consumers property value. Per-consumer version usage entries.
// returns a []ConsumerVersionEntryable when successful
func (m *ConsumerVersionHeatmap) GetConsumers() []ConsumerVersionEntryable {
	return m.consumers
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ConsumerVersionHeatmap) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["consumers"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateConsumerVersionEntryFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]ConsumerVersionEntryable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(ConsumerVersionEntryable)
				}
			}
			m.SetConsumers(res)
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
			m.SetVersions(res)
		}
		return nil
	}
	return res
}

// GetGroupId gets the groupId property value. The groupId property
// returns a *string when successful
func (m *ConsumerVersionHeatmap) GetGroupId() *string {
	return m.groupId
}

// GetVersions gets the versions property value. Ordered list of version strings.
// returns a []string when successful
func (m *ConsumerVersionHeatmap) GetVersions() []string {
	return m.versions
}

// Serialize serializes information the current object
func (m *ConsumerVersionHeatmap) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("artifactId", m.GetArtifactId())
		if err != nil {
			return err
		}
	}
	if m.GetConsumers() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetConsumers()))
		for i, v := range m.GetConsumers() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("consumers", cast)
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
		err := writer.WriteCollectionOfStringValues("versions", m.GetVersions())
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
func (m *ConsumerVersionHeatmap) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The artifactId property
func (m *ConsumerVersionHeatmap) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetConsumers sets the consumers property value. Per-consumer version usage entries.
func (m *ConsumerVersionHeatmap) SetConsumers(value []ConsumerVersionEntryable) {
	m.consumers = value
}

// SetGroupId sets the groupId property value. The groupId property
func (m *ConsumerVersionHeatmap) SetGroupId(value *string) {
	m.groupId = value
}

// SetVersions sets the versions property value. Ordered list of version strings.
func (m *ConsumerVersionHeatmap) SetVersions(value []string) {
	m.versions = value
}

type ConsumerVersionHeatmapable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetConsumers() []ConsumerVersionEntryable
	GetGroupId() *string
	GetVersions() []string
	SetArtifactId(value *string)
	SetConsumers(value []ConsumerVersionEntryable)
	SetGroupId(value *string)
	SetVersions(value []string)
}
