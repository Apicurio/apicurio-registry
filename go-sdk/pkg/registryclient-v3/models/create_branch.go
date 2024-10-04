package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// CreateBranch
type CreateBranch struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The ID of a single artifact branch.
	branchId *string
	// The description property
	description *string
	// The versions property
	versions []string
}

// NewCreateBranch instantiates a new CreateBranch and sets the default values.
func NewCreateBranch() *CreateBranch {
	m := &CreateBranch{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateCreateBranchFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateCreateBranchFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewCreateBranch(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *CreateBranch) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetBranchId gets the branchId property value. The ID of a single artifact branch.
func (m *CreateBranch) GetBranchId() *string {
	return m.branchId
}

// GetDescription gets the description property value. The description property
func (m *CreateBranch) GetDescription() *string {
	return m.description
}

// GetFieldDeserializers the deserialization information for the current model
func (m *CreateBranch) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
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
	res["versions"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
			m.SetVersions(res)
		}
		return nil
	}
	return res
}

// GetVersions gets the versions property value. The versions property
func (m *CreateBranch) GetVersions() []string {
	return m.versions
}

// Serialize serializes information the current object
func (m *CreateBranch) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("branchId", m.GetBranchId())
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
	if m.GetVersions() != nil {
		err := writer.WriteCollectionOfStringValues("versions", m.GetVersions())
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
func (m *CreateBranch) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetBranchId sets the branchId property value. The ID of a single artifact branch.
func (m *CreateBranch) SetBranchId(value *string) {
	m.branchId = value
}

// SetDescription sets the description property value. The description property
func (m *CreateBranch) SetDescription(value *string) {
	m.description = value
}

// SetVersions sets the versions property value. The versions property
func (m *CreateBranch) SetVersions(value []string) {
	m.versions = value
}

// CreateBranchable
type CreateBranchable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetBranchId() *string
	GetDescription() *string
	GetVersions() []string
	SetBranchId(value *string)
	SetDescription(value *string)
	SetVersions(value []string)
}
