package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
	i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e "time"
)

// SearchedVersion models a single artifact from the result set returned when searching for artifacts.
type SearchedVersion struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The ID of a single artifact.
	artifactId *string
	// The artifactType property
	artifactType *string
	// The contentId property
	contentId *int64
	// The createdOn property
	createdOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// The description property
	description *string
	// The globalId property
	globalId *int64
	// An ID of a single artifact group.
	groupId *string
	// The name property
	name *string
	// The owner property
	owner *string
	// Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
	state *VersionState
	// A single version of an artifact.  Can be provided by the client when creating a new version,or it can be server-generated.  The value can be any string unique to the artifact, but it isrecommended to use a simple integer or a semver value.
	version *string
}

// NewSearchedVersion instantiates a new SearchedVersion and sets the default values.
func NewSearchedVersion() *SearchedVersion {
	m := &SearchedVersion{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateSearchedVersionFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateSearchedVersionFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewSearchedVersion(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *SearchedVersion) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The ID of a single artifact.
func (m *SearchedVersion) GetArtifactId() *string {
	return m.artifactId
}

// GetArtifactType gets the artifactType property value. The artifactType property
func (m *SearchedVersion) GetArtifactType() *string {
	return m.artifactType
}

// GetContentId gets the contentId property value. The contentId property
func (m *SearchedVersion) GetContentId() *int64 {
	return m.contentId
}

// GetCreatedOn gets the createdOn property value. The createdOn property
func (m *SearchedVersion) GetCreatedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time {
	return m.createdOn
}

// GetDescription gets the description property value. The description property
func (m *SearchedVersion) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
func (m *SearchedVersion) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["owner"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetOwner(val)
		}
		return nil
	}
	res["state"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseVersionState)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetState(val.(*VersionState))
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
func (m *SearchedVersion) GetGlobalId() *int64 {
	return m.globalId
}

// GetGroupId gets the groupId property value. An ID of a single artifact group.
func (m *SearchedVersion) GetGroupId() *string {
	return m.groupId
}

// GetName gets the name property value. The name property
func (m *SearchedVersion) GetName() *string {
	return m.name
}

// GetOwner gets the owner property value. The owner property
func (m *SearchedVersion) GetOwner() *string {
	return m.owner
}

// GetState gets the state property value. Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
func (m *SearchedVersion) GetState() *VersionState {
	return m.state
}

// GetVersion gets the version property value. A single version of an artifact.  Can be provided by the client when creating a new version,or it can be server-generated.  The value can be any string unique to the artifact, but it isrecommended to use a simple integer or a semver value.
func (m *SearchedVersion) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *SearchedVersion) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
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
		err := writer.WriteInt64Value("contentId", m.GetContentId())
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
		err := writer.WriteStringValue("name", m.GetName())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("owner", m.GetOwner())
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
func (m *SearchedVersion) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The ID of a single artifact.
func (m *SearchedVersion) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetArtifactType sets the artifactType property value. The artifactType property
func (m *SearchedVersion) SetArtifactType(value *string) {
	m.artifactType = value
}

// SetContentId sets the contentId property value. The contentId property
func (m *SearchedVersion) SetContentId(value *int64) {
	m.contentId = value
}

// SetCreatedOn sets the createdOn property value. The createdOn property
func (m *SearchedVersion) SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
	m.createdOn = value
}

// SetDescription sets the description property value. The description property
func (m *SearchedVersion) SetDescription(value *string) {
	m.description = value
}

// SetGlobalId sets the globalId property value. The globalId property
func (m *SearchedVersion) SetGlobalId(value *int64) {
	m.globalId = value
}

// SetGroupId sets the groupId property value. An ID of a single artifact group.
func (m *SearchedVersion) SetGroupId(value *string) {
	m.groupId = value
}

// SetName sets the name property value. The name property
func (m *SearchedVersion) SetName(value *string) {
	m.name = value
}

// SetOwner sets the owner property value. The owner property
func (m *SearchedVersion) SetOwner(value *string) {
	m.owner = value
}

// SetState sets the state property value. Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
func (m *SearchedVersion) SetState(value *VersionState) {
	m.state = value
}

// SetVersion sets the version property value. A single version of an artifact.  Can be provided by the client when creating a new version,or it can be server-generated.  The value can be any string unique to the artifact, but it isrecommended to use a simple integer or a semver value.
func (m *SearchedVersion) SetVersion(value *string) {
	m.version = value
}

// SearchedVersionable
type SearchedVersionable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetArtifactType() *string
	GetContentId() *int64
	GetCreatedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetDescription() *string
	GetGlobalId() *int64
	GetGroupId() *string
	GetName() *string
	GetOwner() *string
	GetState() *VersionState
	GetVersion() *string
	SetArtifactId(value *string)
	SetArtifactType(value *string)
	SetContentId(value *int64)
	SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetDescription(value *string)
	SetGlobalId(value *int64)
	SetGroupId(value *string)
	SetName(value *string)
	SetOwner(value *string)
	SetState(value *VersionState)
	SetVersion(value *string)
}
