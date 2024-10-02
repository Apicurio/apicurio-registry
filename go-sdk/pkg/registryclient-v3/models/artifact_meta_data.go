package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
	i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e "time"
)

type ArtifactMetaData struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The ID of a single artifact.
	artifactId *string
	// The artifactType property
	artifactType *string
	// The createdOn property
	createdOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// The description property
	description *string
	// An ID of a single artifact group.
	groupId *string
	// User-defined name-value pairs. Name and value must be strings.
	labels Labelsable
	// The modifiedBy property
	modifiedBy *string
	// The modifiedOn property
	modifiedOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// The name property
	name *string
	// The owner property
	owner *string
}

// NewArtifactMetaData instantiates a new ArtifactMetaData and sets the default values.
func NewArtifactMetaData() *ArtifactMetaData {
	m := &ArtifactMetaData{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArtifactMetaDataFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateArtifactMetaDataFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArtifactMetaData(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ArtifactMetaData) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The ID of a single artifact.
// returns a *string when successful
func (m *ArtifactMetaData) GetArtifactId() *string {
	return m.artifactId
}

// GetArtifactType gets the artifactType property value. The artifactType property
// returns a *string when successful
func (m *ArtifactMetaData) GetArtifactType() *string {
	return m.artifactType
}

// GetCreatedOn gets the createdOn property value. The createdOn property
// returns a *Time when successful
func (m *ArtifactMetaData) GetCreatedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time {
	return m.createdOn
}

// GetDescription gets the description property value. The description property
// returns a *string when successful
func (m *ArtifactMetaData) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ArtifactMetaData) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	return res
}

// GetGroupId gets the groupId property value. An ID of a single artifact group.
// returns a *string when successful
func (m *ArtifactMetaData) GetGroupId() *string {
	return m.groupId
}

// GetLabels gets the labels property value. User-defined name-value pairs. Name and value must be strings.
// returns a Labelsable when successful
func (m *ArtifactMetaData) GetLabels() Labelsable {
	return m.labels
}

// GetModifiedBy gets the modifiedBy property value. The modifiedBy property
// returns a *string when successful
func (m *ArtifactMetaData) GetModifiedBy() *string {
	return m.modifiedBy
}

// GetModifiedOn gets the modifiedOn property value. The modifiedOn property
// returns a *Time when successful
func (m *ArtifactMetaData) GetModifiedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time {
	return m.modifiedOn
}

// GetName gets the name property value. The name property
// returns a *string when successful
func (m *ArtifactMetaData) GetName() *string {
	return m.name
}

// GetOwner gets the owner property value. The owner property
// returns a *string when successful
func (m *ArtifactMetaData) GetOwner() *string {
	return m.owner
}

// Serialize serializes information the current object
func (m *ArtifactMetaData) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
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
		err := writer.WriteStringValue("groupId", m.GetGroupId())
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
	{
		err := writer.WriteStringValue("owner", m.GetOwner())
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

// SetArtifactId sets the artifactId property value. The ID of a single artifact.
func (m *ArtifactMetaData) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetArtifactType sets the artifactType property value. The artifactType property
func (m *ArtifactMetaData) SetArtifactType(value *string) {
	m.artifactType = value
}

// SetCreatedOn sets the createdOn property value. The createdOn property
func (m *ArtifactMetaData) SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
	m.createdOn = value
}

// SetDescription sets the description property value. The description property
func (m *ArtifactMetaData) SetDescription(value *string) {
	m.description = value
}

// SetGroupId sets the groupId property value. An ID of a single artifact group.
func (m *ArtifactMetaData) SetGroupId(value *string) {
	m.groupId = value
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

// SetOwner sets the owner property value. The owner property
func (m *ArtifactMetaData) SetOwner(value *string) {
	m.owner = value
}

type ArtifactMetaDataable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetArtifactType() *string
	GetCreatedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetDescription() *string
	GetGroupId() *string
	GetLabels() Labelsable
	GetModifiedBy() *string
	GetModifiedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetName() *string
	GetOwner() *string
	SetArtifactId(value *string)
	SetArtifactType(value *string)
	SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetDescription(value *string)
	SetGroupId(value *string)
	SetLabels(value Labelsable)
	SetModifiedBy(value *string)
	SetModifiedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetName(value *string)
	SetOwner(value *string)
}
