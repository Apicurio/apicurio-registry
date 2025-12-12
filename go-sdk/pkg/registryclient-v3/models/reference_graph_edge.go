package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ReferenceGraphEdge an edge in the reference graph representing a reference from one artifact to another.
type ReferenceGraphEdge struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The name of the reference as defined in the source artifact.
	name *string
	// The ID of the source node (the artifact that has the reference).
	sourceNodeId *string
	// The ID of the target node (the artifact being referenced).
	targetNodeId *string
}

// NewReferenceGraphEdge instantiates a new ReferenceGraphEdge and sets the default values.
func NewReferenceGraphEdge() *ReferenceGraphEdge {
	m := &ReferenceGraphEdge{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateReferenceGraphEdgeFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateReferenceGraphEdgeFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewReferenceGraphEdge(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ReferenceGraphEdge) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ReferenceGraphEdge) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["name"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetName(val)
		}
		return nil
	}
	res["sourceNodeId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSourceNodeId(val)
		}
		return nil
	}
	res["targetNodeId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTargetNodeId(val)
		}
		return nil
	}
	return res
}

// GetName gets the name property value. The name of the reference as defined in the source artifact.
// returns a *string when successful
func (m *ReferenceGraphEdge) GetName() *string {
	return m.name
}

// GetSourceNodeId gets the sourceNodeId property value. The ID of the source node (the artifact that has the reference).
// returns a *string when successful
func (m *ReferenceGraphEdge) GetSourceNodeId() *string {
	return m.sourceNodeId
}

// GetTargetNodeId gets the targetNodeId property value. The ID of the target node (the artifact being referenced).
// returns a *string when successful
func (m *ReferenceGraphEdge) GetTargetNodeId() *string {
	return m.targetNodeId
}

// Serialize serializes information the current object
func (m *ReferenceGraphEdge) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("name", m.GetName())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("sourceNodeId", m.GetSourceNodeId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("targetNodeId", m.GetTargetNodeId())
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
func (m *ReferenceGraphEdge) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetName sets the name property value. The name of the reference as defined in the source artifact.
func (m *ReferenceGraphEdge) SetName(value *string) {
	m.name = value
}

// SetSourceNodeId sets the sourceNodeId property value. The ID of the source node (the artifact that has the reference).
func (m *ReferenceGraphEdge) SetSourceNodeId(value *string) {
	m.sourceNodeId = value
}

// SetTargetNodeId sets the targetNodeId property value. The ID of the target node (the artifact being referenced).
func (m *ReferenceGraphEdge) SetTargetNodeId(value *string) {
	m.targetNodeId = value
}

type ReferenceGraphEdgeable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetName() *string
	GetSourceNodeId() *string
	GetTargetNodeId() *string
	SetName(value *string)
	SetSourceNodeId(value *string)
	SetTargetNodeId(value *string)
}
