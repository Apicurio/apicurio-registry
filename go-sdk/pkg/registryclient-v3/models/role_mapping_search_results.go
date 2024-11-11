package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// RoleMappingSearchResults describes the response received when searching for artifacts.
type RoleMappingSearchResults struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The total number of role mappings that matched the query that produced the result set (may be more than the number of role mappings in the result set).
	count *int32
	// The role mappings returned in the result set.
	roleMappings []RoleMappingable
}

// NewRoleMappingSearchResults instantiates a new RoleMappingSearchResults and sets the default values.
func NewRoleMappingSearchResults() *RoleMappingSearchResults {
	m := &RoleMappingSearchResults{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateRoleMappingSearchResultsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateRoleMappingSearchResultsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewRoleMappingSearchResults(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *RoleMappingSearchResults) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetCount gets the count property value. The total number of role mappings that matched the query that produced the result set (may be more than the number of role mappings in the result set).
// returns a *int32 when successful
func (m *RoleMappingSearchResults) GetCount() *int32 {
	return m.count
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *RoleMappingSearchResults) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["roleMappings"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateRoleMappingFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]RoleMappingable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(RoleMappingable)
				}
			}
			m.SetRoleMappings(res)
		}
		return nil
	}
	return res
}

// GetRoleMappings gets the roleMappings property value. The role mappings returned in the result set.
// returns a []RoleMappingable when successful
func (m *RoleMappingSearchResults) GetRoleMappings() []RoleMappingable {
	return m.roleMappings
}

// Serialize serializes information the current object
func (m *RoleMappingSearchResults) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteInt32Value("count", m.GetCount())
		if err != nil {
			return err
		}
	}
	if m.GetRoleMappings() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetRoleMappings()))
		for i, v := range m.GetRoleMappings() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("roleMappings", cast)
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
func (m *RoleMappingSearchResults) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetCount sets the count property value. The total number of role mappings that matched the query that produced the result set (may be more than the number of role mappings in the result set).
func (m *RoleMappingSearchResults) SetCount(value *int32) {
	m.count = value
}

// SetRoleMappings sets the roleMappings property value. The role mappings returned in the result set.
func (m *RoleMappingSearchResults) SetRoleMappings(value []RoleMappingable) {
	m.roleMappings = value
}

type RoleMappingSearchResultsable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetCount() *int32
	GetRoleMappings() []RoleMappingable
	SetCount(value *int32)
	SetRoleMappings(value []RoleMappingable)
}
