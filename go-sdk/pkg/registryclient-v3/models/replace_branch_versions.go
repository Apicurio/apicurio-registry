package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ReplaceBranchVersions
type ReplaceBranchVersions struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The versions property
	versions []string
}

// NewReplaceBranchVersions instantiates a new ReplaceBranchVersions and sets the default values.
func NewReplaceBranchVersions() *ReplaceBranchVersions {
	m := &ReplaceBranchVersions{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateReplaceBranchVersionsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateReplaceBranchVersionsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewReplaceBranchVersions(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *ReplaceBranchVersions) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetFieldDeserializers the deserialization information for the current model
func (m *ReplaceBranchVersions) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
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
func (m *ReplaceBranchVersions) GetVersions() []string {
	return m.versions
}

// Serialize serializes information the current object
func (m *ReplaceBranchVersions) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
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
func (m *ReplaceBranchVersions) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetVersions sets the versions property value. The versions property
func (m *ReplaceBranchVersions) SetVersions(value []string) {
	m.versions = value
}

// ReplaceBranchVersionsable
type ReplaceBranchVersionsable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetVersions() []string
	SetVersions(value []string)
}
