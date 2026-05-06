package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// SchemaUsageEvent represents a single schema usage event reported by a SerDes client.
type SchemaUsageEvent struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifact ID.
	artifactId *string
	// Identifier of the client application reporting the usage event.
	clientId *string
	// The global ID of the artifact version.
	globalId *int64
	// The group ID of the artifact.
	groupId *string
	// The operation type (SERIALIZE or DESERIALIZE).
	operation *SchemaUsageEvent_operation
	// The epoch milliseconds when the schema resolution occurred.
	timestamp *int64
	// The version of the artifact.
	version *string
}

// NewSchemaUsageEvent instantiates a new SchemaUsageEvent and sets the default values.
func NewSchemaUsageEvent() *SchemaUsageEvent {
	m := &SchemaUsageEvent{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateSchemaUsageEventFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateSchemaUsageEventFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewSchemaUsageEvent(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *SchemaUsageEvent) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The artifact ID.
// returns a *string when successful
func (m *SchemaUsageEvent) GetArtifactId() *string {
	return m.artifactId
}

// GetClientId gets the clientId property value. Identifier of the client application reporting the usage event.
// returns a *string when successful
func (m *SchemaUsageEvent) GetClientId() *string {
	return m.clientId
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *SchemaUsageEvent) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["operation"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseSchemaUsageEvent_operation)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetOperation(val.(*SchemaUsageEvent_operation))
		}
		return nil
	}
	res["timestamp"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTimestamp(val)
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

// GetGlobalId gets the globalId property value. The global ID of the artifact version.
// returns a *int64 when successful
func (m *SchemaUsageEvent) GetGlobalId() *int64 {
	return m.globalId
}

// GetGroupId gets the groupId property value. The group ID of the artifact.
// returns a *string when successful
func (m *SchemaUsageEvent) GetGroupId() *string {
	return m.groupId
}

// GetOperation gets the operation property value. The operation type (SERIALIZE or DESERIALIZE).
// returns a *SchemaUsageEvent_operation when successful
func (m *SchemaUsageEvent) GetOperation() *SchemaUsageEvent_operation {
	return m.operation
}

// GetTimestamp gets the timestamp property value. The epoch milliseconds when the schema resolution occurred.
// returns a *int64 when successful
func (m *SchemaUsageEvent) GetTimestamp() *int64 {
	return m.timestamp
}

// GetVersion gets the version property value. The version of the artifact.
// returns a *string when successful
func (m *SchemaUsageEvent) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *SchemaUsageEvent) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("artifactId", m.GetArtifactId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("clientId", m.GetClientId())
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
		err := writer.WriteStringValue("groupId", m.GetGroupId())
		if err != nil {
			return err
		}
	}
	if m.GetOperation() != nil {
		cast := (*m.GetOperation()).String()
		err := writer.WriteStringValue("operation", &cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("timestamp", m.GetTimestamp())
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
func (m *SchemaUsageEvent) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The artifact ID.
func (m *SchemaUsageEvent) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetClientId sets the clientId property value. Identifier of the client application reporting the usage event.
func (m *SchemaUsageEvent) SetClientId(value *string) {
	m.clientId = value
}

// SetGlobalId sets the globalId property value. The global ID of the artifact version.
func (m *SchemaUsageEvent) SetGlobalId(value *int64) {
	m.globalId = value
}

// SetGroupId sets the groupId property value. The group ID of the artifact.
func (m *SchemaUsageEvent) SetGroupId(value *string) {
	m.groupId = value
}

// SetOperation sets the operation property value. The operation type (SERIALIZE or DESERIALIZE).
func (m *SchemaUsageEvent) SetOperation(value *SchemaUsageEvent_operation) {
	m.operation = value
}

// SetTimestamp sets the timestamp property value. The epoch milliseconds when the schema resolution occurred.
func (m *SchemaUsageEvent) SetTimestamp(value *int64) {
	m.timestamp = value
}

// SetVersion sets the version property value. The version of the artifact.
func (m *SchemaUsageEvent) SetVersion(value *string) {
	m.version = value
}

type SchemaUsageEventable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetClientId() *string
	GetGlobalId() *int64
	GetGroupId() *string
	GetOperation() *SchemaUsageEvent_operation
	GetTimestamp() *int64
	GetVersion() *string
	SetArtifactId(value *string)
	SetClientId(value *string)
	SetGlobalId(value *int64)
	SetGroupId(value *string)
	SetOperation(value *SchemaUsageEvent_operation)
	SetTimestamp(value *int64)
	SetVersion(value *string)
}
