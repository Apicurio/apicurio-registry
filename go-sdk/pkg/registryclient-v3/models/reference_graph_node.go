package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ReferenceGraphNode a node in the reference graph representing an artifact version.
type ReferenceGraphNode struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifact ID.
	artifactId *string
	// The type of the artifact.
	artifactType *string
	// The group ID of the artifact.
	groupId *string
	// A unique identifier for this node within the graph.
	id *string
	// Whether this node is part of a circular reference.
	isCycleNode *bool
	// Whether this node is the root node of the graph.
	isRoot *bool
	// The name of the artifact version.
	name *string
	// The version of the artifact.
	version *string
}

// NewReferenceGraphNode instantiates a new ReferenceGraphNode and sets the default values.
func NewReferenceGraphNode() *ReferenceGraphNode {
	m := &ReferenceGraphNode{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateReferenceGraphNodeFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateReferenceGraphNodeFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewReferenceGraphNode(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ReferenceGraphNode) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The artifact ID.
// returns a *string when successful
func (m *ReferenceGraphNode) GetArtifactId() *string {
	return m.artifactId
}

// GetArtifactType gets the artifactType property value. The type of the artifact.
// returns a *string when successful
func (m *ReferenceGraphNode) GetArtifactType() *string {
	return m.artifactType
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ReferenceGraphNode) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["artifactType"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetArtifactType(val)
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
	res["id"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetId(val)
		}
		return nil
	}
	res["isCycleNode"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetIsCycleNode(val)
		}
		return nil
	}
	res["isRoot"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetIsRoot(val)
		}
		return nil
	}
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

// GetGroupId gets the groupId property value. The group ID of the artifact.
// returns a *string when successful
func (m *ReferenceGraphNode) GetGroupId() *string {
	return m.groupId
}

// GetId gets the id property value. A unique identifier for this node within the graph.
// returns a *string when successful
func (m *ReferenceGraphNode) GetId() *string {
	return m.id
}

// GetIsCycleNode gets the isCycleNode property value. Whether this node is part of a circular reference.
// returns a *bool when successful
func (m *ReferenceGraphNode) GetIsCycleNode() *bool {
	return m.isCycleNode
}

// GetIsRoot gets the isRoot property value. Whether this node is the root node of the graph.
// returns a *bool when successful
func (m *ReferenceGraphNode) GetIsRoot() *bool {
	return m.isRoot
}

// GetName gets the name property value. The name of the artifact version.
// returns a *string when successful
func (m *ReferenceGraphNode) GetName() *string {
	return m.name
}

// GetVersion gets the version property value. The version of the artifact.
// returns a *string when successful
func (m *ReferenceGraphNode) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *ReferenceGraphNode) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("artifactId", m.GetArtifactId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("artifactType", m.GetArtifactType())
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
	{
		err := writer.WriteStringValue("id", m.GetId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteBoolValue("isCycleNode", m.GetIsCycleNode())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteBoolValue("isRoot", m.GetIsRoot())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("name", m.GetName())
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
func (m *ReferenceGraphNode) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The artifact ID.
func (m *ReferenceGraphNode) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetArtifactType sets the artifactType property value. The type of the artifact.
func (m *ReferenceGraphNode) SetArtifactType(value *string) {
	m.artifactType = value
}

// SetGroupId sets the groupId property value. The group ID of the artifact.
func (m *ReferenceGraphNode) SetGroupId(value *string) {
	m.groupId = value
}

// SetId sets the id property value. A unique identifier for this node within the graph.
func (m *ReferenceGraphNode) SetId(value *string) {
	m.id = value
}

// SetIsCycleNode sets the isCycleNode property value. Whether this node is part of a circular reference.
func (m *ReferenceGraphNode) SetIsCycleNode(value *bool) {
	m.isCycleNode = value
}

// SetIsRoot sets the isRoot property value. Whether this node is the root node of the graph.
func (m *ReferenceGraphNode) SetIsRoot(value *bool) {
	m.isRoot = value
}

// SetName sets the name property value. The name of the artifact version.
func (m *ReferenceGraphNode) SetName(value *string) {
	m.name = value
}

// SetVersion sets the version property value. The version of the artifact.
func (m *ReferenceGraphNode) SetVersion(value *string) {
	m.version = value
}

type ReferenceGraphNodeable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetArtifactType() *string
	GetGroupId() *string
	GetId() *string
	GetIsCycleNode() *bool
	GetIsRoot() *bool
	GetName() *string
	GetVersion() *string
	SetArtifactId(value *string)
	SetArtifactType(value *string)
	SetGroupId(value *string)
	SetId(value *string)
	SetIsCycleNode(value *bool)
	SetIsRoot(value *bool)
	SetName(value *string)
	SetVersion(value *string)
}
