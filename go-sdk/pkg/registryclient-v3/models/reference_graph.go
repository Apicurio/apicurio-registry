package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ReferenceGraph a graph representation of artifact references.
type ReferenceGraph struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// All edges (references) in the graph.
	edges []ReferenceGraphEdgeable
	// Metadata about the reference graph.
	metadata ReferenceGraphMetadataable
	// All nodes in the graph, including the root.
	nodes []ReferenceGraphNodeable
	// A node in the reference graph representing an artifact version.
	root ReferenceGraphNodeable
}

// NewReferenceGraph instantiates a new ReferenceGraph and sets the default values.
func NewReferenceGraph() *ReferenceGraph {
	m := &ReferenceGraph{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateReferenceGraphFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateReferenceGraphFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewReferenceGraph(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ReferenceGraph) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetEdges gets the edges property value. All edges (references) in the graph.
// returns a []ReferenceGraphEdgeable when successful
func (m *ReferenceGraph) GetEdges() []ReferenceGraphEdgeable {
	return m.edges
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ReferenceGraph) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["edges"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateReferenceGraphEdgeFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]ReferenceGraphEdgeable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(ReferenceGraphEdgeable)
				}
			}
			m.SetEdges(res)
		}
		return nil
	}
	res["metadata"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateReferenceGraphMetadataFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMetadata(val.(ReferenceGraphMetadataable))
		}
		return nil
	}
	res["nodes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateReferenceGraphNodeFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]ReferenceGraphNodeable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(ReferenceGraphNodeable)
				}
			}
			m.SetNodes(res)
		}
		return nil
	}
	res["root"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateReferenceGraphNodeFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetRoot(val.(ReferenceGraphNodeable))
		}
		return nil
	}
	return res
}

// GetMetadata gets the metadata property value. Metadata about the reference graph.
// returns a ReferenceGraphMetadataable when successful
func (m *ReferenceGraph) GetMetadata() ReferenceGraphMetadataable {
	return m.metadata
}

// GetNodes gets the nodes property value. All nodes in the graph, including the root.
// returns a []ReferenceGraphNodeable when successful
func (m *ReferenceGraph) GetNodes() []ReferenceGraphNodeable {
	return m.nodes
}

// GetRoot gets the root property value. A node in the reference graph representing an artifact version.
// returns a ReferenceGraphNodeable when successful
func (m *ReferenceGraph) GetRoot() ReferenceGraphNodeable {
	return m.root
}

// Serialize serializes information the current object
func (m *ReferenceGraph) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetEdges() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetEdges()))
		for i, v := range m.GetEdges() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("edges", cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("metadata", m.GetMetadata())
		if err != nil {
			return err
		}
	}
	if m.GetNodes() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetNodes()))
		for i, v := range m.GetNodes() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("nodes", cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("root", m.GetRoot())
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
func (m *ReferenceGraph) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetEdges sets the edges property value. All edges (references) in the graph.
func (m *ReferenceGraph) SetEdges(value []ReferenceGraphEdgeable) {
	m.edges = value
}

// SetMetadata sets the metadata property value. Metadata about the reference graph.
func (m *ReferenceGraph) SetMetadata(value ReferenceGraphMetadataable) {
	m.metadata = value
}

// SetNodes sets the nodes property value. All nodes in the graph, including the root.
func (m *ReferenceGraph) SetNodes(value []ReferenceGraphNodeable) {
	m.nodes = value
}

// SetRoot sets the root property value. A node in the reference graph representing an artifact version.
func (m *ReferenceGraph) SetRoot(value ReferenceGraphNodeable) {
	m.root = value
}

type ReferenceGraphable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetEdges() []ReferenceGraphEdgeable
	GetMetadata() ReferenceGraphMetadataable
	GetNodes() []ReferenceGraphNodeable
	GetRoot() ReferenceGraphNodeable
	SetEdges(value []ReferenceGraphEdgeable)
	SetMetadata(value ReferenceGraphMetadataable)
	SetNodes(value []ReferenceGraphNodeable)
	SetRoot(value ReferenceGraphNodeable)
}
