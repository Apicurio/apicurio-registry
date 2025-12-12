package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ReferenceGraphMetadata metadata about the reference graph.
type ReferenceGraphMetadata struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Whether the graph contains circular references.
	hasCycles *bool
	// The maximum depth reached in the graph.
	maxDepth *int32
	// The total number of edges in the graph.
	totalEdges *int32
	// The total number of nodes in the graph.
	totalNodes *int32
}

// NewReferenceGraphMetadata instantiates a new ReferenceGraphMetadata and sets the default values.
func NewReferenceGraphMetadata() *ReferenceGraphMetadata {
	m := &ReferenceGraphMetadata{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateReferenceGraphMetadataFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateReferenceGraphMetadataFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewReferenceGraphMetadata(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ReferenceGraphMetadata) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ReferenceGraphMetadata) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["hasCycles"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetHasCycles(val)
		}
		return nil
	}
	res["maxDepth"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMaxDepth(val)
		}
		return nil
	}
	res["totalEdges"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTotalEdges(val)
		}
		return nil
	}
	res["totalNodes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTotalNodes(val)
		}
		return nil
	}
	return res
}

// GetHasCycles gets the hasCycles property value. Whether the graph contains circular references.
// returns a *bool when successful
func (m *ReferenceGraphMetadata) GetHasCycles() *bool {
	return m.hasCycles
}

// GetMaxDepth gets the maxDepth property value. The maximum depth reached in the graph.
// returns a *int32 when successful
func (m *ReferenceGraphMetadata) GetMaxDepth() *int32 {
	return m.maxDepth
}

// GetTotalEdges gets the totalEdges property value. The total number of edges in the graph.
// returns a *int32 when successful
func (m *ReferenceGraphMetadata) GetTotalEdges() *int32 {
	return m.totalEdges
}

// GetTotalNodes gets the totalNodes property value. The total number of nodes in the graph.
// returns a *int32 when successful
func (m *ReferenceGraphMetadata) GetTotalNodes() *int32 {
	return m.totalNodes
}

// Serialize serializes information the current object
func (m *ReferenceGraphMetadata) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteBoolValue("hasCycles", m.GetHasCycles())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("maxDepth", m.GetMaxDepth())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("totalEdges", m.GetTotalEdges())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("totalNodes", m.GetTotalNodes())
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
func (m *ReferenceGraphMetadata) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetHasCycles sets the hasCycles property value. Whether the graph contains circular references.
func (m *ReferenceGraphMetadata) SetHasCycles(value *bool) {
	m.hasCycles = value
}

// SetMaxDepth sets the maxDepth property value. The maximum depth reached in the graph.
func (m *ReferenceGraphMetadata) SetMaxDepth(value *int32) {
	m.maxDepth = value
}

// SetTotalEdges sets the totalEdges property value. The total number of edges in the graph.
func (m *ReferenceGraphMetadata) SetTotalEdges(value *int32) {
	m.totalEdges = value
}

// SetTotalNodes sets the totalNodes property value. The total number of nodes in the graph.
func (m *ReferenceGraphMetadata) SetTotalNodes(value *int32) {
	m.totalNodes = value
}

type ReferenceGraphMetadataable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetHasCycles() *bool
	GetMaxDepth() *int32
	GetTotalEdges() *int32
	GetTotalNodes() *int32
	SetHasCycles(value *bool)
	SetMaxDepth(value *int32)
	SetTotalEdges(value *int32)
	SetTotalNodes(value *int32)
}
