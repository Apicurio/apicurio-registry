package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// GroupSearchResults describes the response received when searching for groups.
type GroupSearchResults struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The total number of groups that matched the query that produced the result set (may be more than the number of groups in the result set).
	count *int32
	// The groups returned in the result set.
	groups []SearchedGroupable
}

// NewGroupSearchResults instantiates a new GroupSearchResults and sets the default values.
func NewGroupSearchResults() *GroupSearchResults {
	m := &GroupSearchResults{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateGroupSearchResultsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateGroupSearchResultsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewGroupSearchResults(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *GroupSearchResults) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetCount gets the count property value. The total number of groups that matched the query that produced the result set (may be more than the number of groups in the result set).
// returns a *int32 when successful
func (m *GroupSearchResults) GetCount() *int32 {
	return m.count
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *GroupSearchResults) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["count"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetCount(val)
		}
		return nil
	}
	res["groups"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateSearchedGroupFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]SearchedGroupable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(SearchedGroupable)
				}
			}
			m.SetGroups(res)
		}
		return nil
	}
	return res
}

// GetGroups gets the groups property value. The groups returned in the result set.
// returns a []SearchedGroupable when successful
func (m *GroupSearchResults) GetGroups() []SearchedGroupable {
	return m.groups
}

// Serialize serializes information the current object
func (m *GroupSearchResults) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteInt32Value("count", m.GetCount())
		if err != nil {
			return err
		}
	}
	if m.GetGroups() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetGroups()))
		for i, v := range m.GetGroups() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("groups", cast)
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
func (m *GroupSearchResults) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetCount sets the count property value. The total number of groups that matched the query that produced the result set (may be more than the number of groups in the result set).
func (m *GroupSearchResults) SetCount(value *int32) {
	m.count = value
}

// SetGroups sets the groups property value. The groups returned in the result set.
func (m *GroupSearchResults) SetGroups(value []SearchedGroupable) {
	m.groups = value
}

type GroupSearchResultsable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetCount() *int32
	GetGroups() []SearchedGroupable
	SetCount(value *int32)
	SetGroups(value []SearchedGroupable)
}
