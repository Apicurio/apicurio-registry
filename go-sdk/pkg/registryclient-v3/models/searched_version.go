package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
	i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e "time"
)

// SearchedVersion models a single artifact from the result set returned when searching for artifacts.
type SearchedVersion struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The contentId property
	contentId *int64
	// The createdOn property
	createdOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// The description property
	description *string
	// The globalId property
	globalId *int64
	// The name property
	name *string
	// The owner property
	owner *string
	// The references property
	references []ArtifactReferenceable
	// Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
	state *VersionState
	// The type property
	typeEscaped *string
	// The version property
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
		val, err := n.GetEnumValue(ParseVersionState)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetState(val.(*VersionState))
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
func (m *SearchedVersion) GetGlobalId() *int64 {
	return m.globalId
}

// GetName gets the name property value. The name property
func (m *SearchedVersion) GetName() *string {
	return m.name
}

// GetOwner gets the owner property value. The owner property
func (m *SearchedVersion) GetOwner() *string {
	return m.owner
}

// GetReferences gets the references property value. The references property
func (m *SearchedVersion) GetReferences() []ArtifactReferenceable {
	return m.references
}

// GetState gets the state property value. Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
func (m *SearchedVersion) GetState() *VersionState {
	return m.state
}

// GetTypeEscaped gets the type property value. The type property
func (m *SearchedVersion) GetTypeEscaped() *string {
	return m.typeEscaped
}

// GetVersion gets the version property value. The version property
func (m *SearchedVersion) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *SearchedVersion) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
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
func (m *SearchedVersion) SetAdditionalData(value map[string]any) {
	m.additionalData = value
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

// SetName sets the name property value. The name property
func (m *SearchedVersion) SetName(value *string) {
	m.name = value
}

// SetOwner sets the owner property value. The owner property
func (m *SearchedVersion) SetOwner(value *string) {
	m.owner = value
}

// SetReferences sets the references property value. The references property
func (m *SearchedVersion) SetReferences(value []ArtifactReferenceable) {
	m.references = value
}

// SetState sets the state property value. Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
func (m *SearchedVersion) SetState(value *VersionState) {
	m.state = value
}

// SetTypeEscaped sets the type property value. The type property
func (m *SearchedVersion) SetTypeEscaped(value *string) {
	m.typeEscaped = value
}

// SetVersion sets the version property value. The version property
func (m *SearchedVersion) SetVersion(value *string) {
	m.version = value
}

// SearchedVersionable
type SearchedVersionable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetContentId() *int64
	GetCreatedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetDescription() *string
	GetGlobalId() *int64
	GetName() *string
	GetOwner() *string
	GetReferences() []ArtifactReferenceable
	GetState() *VersionState
	GetTypeEscaped() *string
	GetVersion() *string
	SetContentId(value *int64)
	SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetDescription(value *string)
	SetGlobalId(value *int64)
	SetName(value *string)
	SetOwner(value *string)
	SetReferences(value []ArtifactReferenceable)
	SetState(value *VersionState)
	SetTypeEscaped(value *string)
	SetVersion(value *string)
}
