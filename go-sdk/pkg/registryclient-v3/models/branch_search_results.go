package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// BranchSearchResults describes the response received when searching for branches.
type BranchSearchResults struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The branches returned in the result set.
	branches []SearchedBranchable
	// The total number of branches that matched the query that produced the result set (may be more than the number of branches in the result set).
	count *int32
}

// NewBranchSearchResults instantiates a new BranchSearchResults and sets the default values.
func NewBranchSearchResults() *BranchSearchResults {
	m := &BranchSearchResults{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateBranchSearchResultsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateBranchSearchResultsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewBranchSearchResults(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *BranchSearchResults) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetBranches gets the branches property value. The branches returned in the result set.
// returns a []SearchedBranchable when successful
func (m *BranchSearchResults) GetBranches() []SearchedBranchable {
	return m.branches
}

// GetCount gets the count property value. The total number of branches that matched the query that produced the result set (may be more than the number of branches in the result set).
// returns a *int32 when successful
func (m *BranchSearchResults) GetCount() *int32 {
	return m.count
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *BranchSearchResults) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["branches"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateSearchedBranchFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]SearchedBranchable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(SearchedBranchable)
				}
			}
			m.SetBranches(res)
		}
		return nil
	}
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
	return res
}

// Serialize serializes information the current object
func (m *BranchSearchResults) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetBranches() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetBranches()))
		for i, v := range m.GetBranches() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("branches", cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("count", m.GetCount())
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
func (m *BranchSearchResults) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetBranches sets the branches property value. The branches returned in the result set.
func (m *BranchSearchResults) SetBranches(value []SearchedBranchable) {
	m.branches = value
}

// SetCount sets the count property value. The total number of branches that matched the query that produced the result set (may be more than the number of branches in the result set).
func (m *BranchSearchResults) SetCount(value *int32) {
	m.count = value
}

type BranchSearchResultsable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetBranches() []SearchedBranchable
	GetCount() *int32
	SetBranches(value []SearchedBranchable)
	SetCount(value *int32)
}
