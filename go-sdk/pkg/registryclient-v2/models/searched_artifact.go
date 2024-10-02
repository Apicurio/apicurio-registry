package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
	i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e "time"
)

// SearchedArtifact models a single artifact from the result set returned when searching for artifacts.
type SearchedArtifact struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The createdBy property
	createdBy *string
	// The createdOn property
	createdOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// The description property
	description *string
	// An ID of a single artifact group.
	groupId *string
	// The ID of a single artifact.
	id *string
	// The labels property
	labels []string
	// The modifiedBy property
	modifiedBy *string
	// The modifiedOn property
	modifiedOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// The name property
	name *string
	// Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
	state *ArtifactState
	// The type property
	typeEscaped *string
}

// NewSearchedArtifact instantiates a new SearchedArtifact and sets the default values.
func NewSearchedArtifact() *SearchedArtifact {
	m := &SearchedArtifact{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateSearchedArtifactFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateSearchedArtifactFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewSearchedArtifact(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *SearchedArtifact) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetCreatedBy gets the createdBy property value. The createdBy property
// returns a *string when successful
func (m *SearchedArtifact) GetCreatedBy() *string {
	return m.createdBy
}

// GetCreatedOn gets the createdOn property value. The createdOn property
// returns a *Time when successful
func (m *SearchedArtifact) GetCreatedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time {
	return m.createdOn
}

// GetDescription gets the description property value. The description property
// returns a *string when successful
func (m *SearchedArtifact) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *SearchedArtifact) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
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
			m.SetLabels(res)
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
	return res
}

// GetGroupId gets the groupId property value. An ID of a single artifact group.
// returns a *string when successful
func (m *SearchedArtifact) GetGroupId() *string {
	return m.groupId
}

// GetId gets the id property value. The ID of a single artifact.
// returns a *string when successful
func (m *SearchedArtifact) GetId() *string {
	return m.id
}

// GetLabels gets the labels property value. The labels property
// returns a []string when successful
func (m *SearchedArtifact) GetLabels() []string {
	return m.labels
}

// GetModifiedBy gets the modifiedBy property value. The modifiedBy property
// returns a *string when successful
func (m *SearchedArtifact) GetModifiedBy() *string {
	return m.modifiedBy
}

// GetModifiedOn gets the modifiedOn property value. The modifiedOn property
// returns a *Time when successful
func (m *SearchedArtifact) GetModifiedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time {
	return m.modifiedOn
}

// GetName gets the name property value. The name property
// returns a *string when successful
func (m *SearchedArtifact) GetName() *string {
	return m.name
}

// GetState gets the state property value. Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
// returns a *ArtifactState when successful
func (m *SearchedArtifact) GetState() *ArtifactState {
	return m.state
}

// GetTypeEscaped gets the type property value. The type property
// returns a *string when successful
func (m *SearchedArtifact) GetTypeEscaped() *string {
	return m.typeEscaped
}

// Serialize serializes information the current object
func (m *SearchedArtifact) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
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
	if m.GetLabels() != nil {
		err := writer.WriteCollectionOfStringValues("labels", m.GetLabels())
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
		err := writer.WriteAdditionalData(m.GetAdditionalData())
		if err != nil {
			return err
		}
	}
	return nil
}

// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *SearchedArtifact) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetCreatedBy sets the createdBy property value. The createdBy property
func (m *SearchedArtifact) SetCreatedBy(value *string) {
	m.createdBy = value
}

// SetCreatedOn sets the createdOn property value. The createdOn property
func (m *SearchedArtifact) SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
	m.createdOn = value
}

// SetDescription sets the description property value. The description property
func (m *SearchedArtifact) SetDescription(value *string) {
	m.description = value
}

// SetGroupId sets the groupId property value. An ID of a single artifact group.
func (m *SearchedArtifact) SetGroupId(value *string) {
	m.groupId = value
}

// SetId sets the id property value. The ID of a single artifact.
func (m *SearchedArtifact) SetId(value *string) {
	m.id = value
}

// SetLabels sets the labels property value. The labels property
func (m *SearchedArtifact) SetLabels(value []string) {
	m.labels = value
}

// SetModifiedBy sets the modifiedBy property value. The modifiedBy property
func (m *SearchedArtifact) SetModifiedBy(value *string) {
	m.modifiedBy = value
}

// SetModifiedOn sets the modifiedOn property value. The modifiedOn property
func (m *SearchedArtifact) SetModifiedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
	m.modifiedOn = value
}

// SetName sets the name property value. The name property
func (m *SearchedArtifact) SetName(value *string) {
	m.name = value
}

// SetState sets the state property value. Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
func (m *SearchedArtifact) SetState(value *ArtifactState) {
	m.state = value
}

// SetTypeEscaped sets the type property value. The type property
func (m *SearchedArtifact) SetTypeEscaped(value *string) {
	m.typeEscaped = value
}

type SearchedArtifactable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetCreatedBy() *string
	GetCreatedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetDescription() *string
	GetGroupId() *string
	GetId() *string
	GetLabels() []string
	GetModifiedBy() *string
	GetModifiedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetName() *string
	GetState() *ArtifactState
	GetTypeEscaped() *string
	SetCreatedBy(value *string)
	SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetDescription(value *string)
	SetGroupId(value *string)
	SetId(value *string)
	SetLabels(value []string)
	SetModifiedBy(value *string)
	SetModifiedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetName(value *string)
	SetState(value *ArtifactState)
	SetTypeEscaped(value *string)
}
