package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
	i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e "time"
)

// SearchedBranch
type SearchedBranch struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The ID of a single artifact.
	artifactId *string
	// The ID of a single artifact branch.
	branchId *string
	// The createdOn property
	createdOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// The description property
	description *string
	// An ID of a single artifact group.
	groupId *string
	// The modifiedBy property
	modifiedBy *string
	// The modifiedOn property
	modifiedOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// The owner property
	owner *string
	// The userDefined property
	userDefined *bool
}

// NewSearchedBranch instantiates a new SearchedBranch and sets the default values.
func NewSearchedBranch() *SearchedBranch {
	m := &SearchedBranch{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateSearchedBranchFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateSearchedBranchFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewSearchedBranch(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *SearchedBranch) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The ID of a single artifact.
func (m *SearchedBranch) GetArtifactId() *string {
	return m.artifactId
}

// GetBranchId gets the branchId property value. The ID of a single artifact branch.
func (m *SearchedBranch) GetBranchId() *string {
	return m.branchId
}

// GetCreatedOn gets the createdOn property value. The createdOn property
func (m *SearchedBranch) GetCreatedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time {
	return m.createdOn
}

// GetDescription gets the description property value. The description property
func (m *SearchedBranch) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
func (m *SearchedBranch) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["branchId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetBranchId(val)
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
	res["userDefined"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetUserDefined(val)
		}
		return nil
	}
	return res
}

// GetGroupId gets the groupId property value. An ID of a single artifact group.
func (m *SearchedBranch) GetGroupId() *string {
	return m.groupId
}

// GetModifiedBy gets the modifiedBy property value. The modifiedBy property
func (m *SearchedBranch) GetModifiedBy() *string {
	return m.modifiedBy
}

// GetModifiedOn gets the modifiedOn property value. The modifiedOn property
func (m *SearchedBranch) GetModifiedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time {
	return m.modifiedOn
}

// GetOwner gets the owner property value. The owner property
func (m *SearchedBranch) GetOwner() *string {
	return m.owner
}

// GetUserDefined gets the userDefined property value. The userDefined property
func (m *SearchedBranch) GetUserDefined() *bool {
	return m.userDefined
}

// Serialize serializes information the current object
func (m *SearchedBranch) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("artifactId", m.GetArtifactId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("branchId", m.GetBranchId())
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
		err := writer.WriteStringValue("owner", m.GetOwner())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteBoolValue("userDefined", m.GetUserDefined())
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
func (m *SearchedBranch) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The ID of a single artifact.
func (m *SearchedBranch) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetBranchId sets the branchId property value. The ID of a single artifact branch.
func (m *SearchedBranch) SetBranchId(value *string) {
	m.branchId = value
}

// SetCreatedOn sets the createdOn property value. The createdOn property
func (m *SearchedBranch) SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
	m.createdOn = value
}

// SetDescription sets the description property value. The description property
func (m *SearchedBranch) SetDescription(value *string) {
	m.description = value
}

// SetGroupId sets the groupId property value. An ID of a single artifact group.
func (m *SearchedBranch) SetGroupId(value *string) {
	m.groupId = value
}

// SetModifiedBy sets the modifiedBy property value. The modifiedBy property
func (m *SearchedBranch) SetModifiedBy(value *string) {
	m.modifiedBy = value
}

// SetModifiedOn sets the modifiedOn property value. The modifiedOn property
func (m *SearchedBranch) SetModifiedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
	m.modifiedOn = value
}

// SetOwner sets the owner property value. The owner property
func (m *SearchedBranch) SetOwner(value *string) {
	m.owner = value
}

// SetUserDefined sets the userDefined property value. The userDefined property
func (m *SearchedBranch) SetUserDefined(value *bool) {
	m.userDefined = value
}

// SearchedBranchable
type SearchedBranchable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetBranchId() *string
	GetCreatedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetDescription() *string
	GetGroupId() *string
	GetModifiedBy() *string
	GetModifiedOn() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetOwner() *string
	GetUserDefined() *bool
	SetArtifactId(value *string)
	SetBranchId(value *string)
	SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetDescription(value *string)
	SetGroupId(value *string)
	SetModifiedBy(value *string)
	SetModifiedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetOwner(value *string)
	SetUserDefined(value *bool)
}
