package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
	i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e "time"
)

// ArtifactMetaData
type ArtifactMetaData struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The contentId property
	contentId *int64
	// The createdBy property
	createdBy *string
	// The createdOn property
	createdOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// The description property
	description *string
	// The globalId property
	globalId *int64
	// An ID of a single artifact group.
	groupId *string
	// The ID of a single artifact.
	id *string
	// User-defined name-value pairs. Name and value must be strings.
	labels Labelsable
	// The modifiedBy property
	modifiedBy *string
	// The modifiedOn property
	modifiedOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// The name property
	name *string
	// The references property
	references []ArtifactReferenceable
	// Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
	state *ArtifactState
	// The type property
	typeEscaped *string
	// The version property
	version *string
}

// NewArtifactMetaData instantiates a new ArtifactMetaData and sets the default values.
func NewArtifactMetaData() *ArtifactMetaData {
	m := &ArtifactMetaData{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArtifactMetaDataFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateArtifactMetaDataFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArtifactMetaData(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *ArtifactMetaData) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetContentId gets the contentId property value. The contentId property
func (m *ArtifactMetaData) GetContentId() *int64 {
	return m.contentId
}

// GetCreatedBy gets the createdBy property value. The createdBy property
func (m *ArtifactMetaData) GetCreatedBy() *string {
	return m.createdBy
}

// GetCreatedOn gets the createdOn property value. The createdOn property
func (m *ArtifactMetaData) GetCreatedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time {
	return m.createdOn
}

// GetDescription gets the description property value. The description property
func (m *ArtifactMetaData) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
func (m *ArtifactMetaData) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["contentId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetContentId(val)
		}
		return nil
	}
	res["createdBy"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetCreatedBy(val)
		}
		return nil
	}
	res["createdOn"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetTimeValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetCreatedOn(val)
		}
		return nil
	}
	res["description"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDescription(val)
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
	res["labels"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateLabelsFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetLabels(val.(Labelsable))
		}
		return nil
	}
	res["modifiedBy"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetModifiedBy(val)
		}
		return nil
	}
	res["modifiedOn"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetTimeValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetModifiedOn(val)
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
	res["references"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateArtifactReferenceFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]ArtifactReferenceable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(ArtifactReferenceable)
				}
			}
			m.SetReferences(res)
		}
		return nil
	}
	res["state"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseArtifactState)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetState(val.(*ArtifactState))
		}
		return nil
	}
	res["type"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTypeEscaped(val)
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

// GetGlobalId gets the globalId property value. The globalId property
func (m *ArtifactMetaData) GetGlobalId() *int64 {
	return m.globalId
}

// GetGroupId gets the groupId property value. An ID of a single artifact group.
func (m *ArtifactMetaData) GetGroupId() *string {
	return m.groupId
}

// GetId gets the id property value. The ID of a single artifact.
func (m *ArtifactMetaData) GetId() *string {
	return m.id
}

// GetLabels gets the labels property value. User-defined name-value pairs. Name and value must be strings.
func (m *ArtifactMetaData) GetLabels() Labelsable {
	return m.labels
}

// GetModifiedBy gets the modifiedBy property value. The modifiedBy property
func (m *ArtifactMetaData) GetModifiedBy() *string {
	return m.modifiedBy
}

// GetModifiedOn gets the modifiedOn property value. The modifiedOn property
func (m *ArtifactMetaData) GetModifiedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time {
	return m.modifiedOn
}

// GetName gets the name property value. The name property
func (m *ArtifactMetaData) GetName() *string {
	return m.name
}

// GetReferences gets the references property value. The references property
func (m *ArtifactMetaData) GetReferences() []ArtifactReferenceable {
	return m.references
}

// GetState gets the state property value. Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
func (m *ArtifactMetaData) GetState() *ArtifactState {
	return m.state
}

// GetTypeEscaped gets the type property value. The type property
func (m *ArtifactMetaData) GetTypeEscaped() *string {
	return m.typeEscaped
}

// GetVersion gets the version property value. The version property
func (m *ArtifactMetaData) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *ArtifactMetaData) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteInt64Value("contentId", m.GetContentId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("createdBy", m.GetCreatedBy())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteTimeValue("createdOn", m.GetCreatedOn())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("description", m.GetDescription())
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
	{
		err := writer.WriteStringValue("id", m.GetId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("labels", m.GetLabels())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("modifiedBy", m.GetModifiedBy())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteTimeValue("modifiedOn", m.GetModifiedOn())
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
	if m.GetReferences() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetReferences()))
		for i, v := range m.GetReferences() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("references", cast)
		if err != nil {
			return err
		}
	}
	if m.GetState() != nil {
		cast := (*m.GetState()).String()
		err := writer.WriteStringValue("state", &cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("type", m.GetTypeEscaped())
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
func (m *ArtifactMetaData) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetContentId sets the contentId property value. The contentId property
func (m *ArtifactMetaData) SetContentId(value *int64) {
	m.contentId = value
}

// SetCreatedBy sets the createdBy property value. The createdBy property
func (m *ArtifactMetaData) SetCreatedBy(value *string) {
	m.createdBy = value
}

// SetCreatedOn sets the createdOn property value. The createdOn property
func (m *ArtifactMetaData) SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
	m.createdOn = value
}

// SetDescription sets the description property value. The description property
func (m *ArtifactMetaData) SetDescription(value *string) {
	m.description = value
}

// SetGlobalId sets the globalId property value. The globalId property
func (m *ArtifactMetaData) SetGlobalId(value *int64) {
	m.globalId = value
}

// SetGroupId sets the groupId property value. An ID of a single artifact group.
func (m *ArtifactMetaData) SetGroupId(value *string) {
	m.groupId = value
}

// SetId sets the id property value. The ID of a single artifact.
func (m *ArtifactMetaData) SetId(value *string) {
	m.id = value
}

// SetLabels sets the labels property value. User-defined name-value pairs. Name and value must be strings.
func (m *ArtifactMetaData) SetLabels(value Labelsable) {
	m.labels = value
}

// SetModifiedBy sets the modifiedBy property value. The modifiedBy property
func (m *ArtifactMetaData) SetModifiedBy(value *string) {
	m.modifiedBy = value
}

// SetModifiedOn sets the modifiedOn property value. The modifiedOn property
func (m *ArtifactMetaData) SetModifiedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
	m.modifiedOn = value
}

// SetName sets the name property value. The name property
func (m *ArtifactMetaData) SetName(value *string) {
	m.name = value
}

// SetReferences sets the references property value. The references property
func (m *ArtifactMetaData) SetReferences(value []ArtifactReferenceable) {
	m.references = value
}

// SetState sets the state property value. Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
func (m *ArtifactMetaData) SetState(value *ArtifactState) {
	m.state = value
}

// SetTypeEscaped sets the type property value. The type property
func (m *ArtifactMetaData) SetTypeEscaped(value *string) {
	m.typeEscaped = value
}

// SetVersion sets the version property value. The version property
func (m *ArtifactMetaData) SetVersion(value *string) {
	m.version = value
}

// ArtifactMetaDataable
type ArtifactMetaDataable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetContentId() *int64
	GetCreatedBy() *string
	GetCreatedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetDescription() *string
	GetGlobalId() *int64
	GetGroupId() *string
	GetId() *string
	GetLabels() Labelsable
	GetModifiedBy() *string
	GetModifiedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetName() *string
	GetReferences() []ArtifactReferenceable
	GetState() *ArtifactState
	GetTypeEscaped() *string
	GetVersion() *string
	SetContentId(value *int64)
	SetCreatedBy(value *string)
	SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetDescription(value *string)
	SetGlobalId(value *int64)
	SetGroupId(value *string)
	SetId(value *string)
	SetLabels(value Labelsable)
	SetModifiedBy(value *string)
	SetModifiedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetName(value *string)
	SetReferences(value []ArtifactReferenceable)
	SetState(value *ArtifactState)
	SetTypeEscaped(value *string)
	SetVersion(value *string)
}
