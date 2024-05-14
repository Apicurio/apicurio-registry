package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArtifactSearchResults describes the response received when searching for artifacts.
type ArtifactSearchResults struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifacts returned in the result set.
	artifacts []SearchedArtifactable
	// The total number of artifacts that matched the query that produced the result set (may be more than the number of artifacts in the result set).
	count *int32
}

// NewArtifactSearchResults instantiates a new ArtifactSearchResults and sets the default values.
func NewArtifactSearchResults() *ArtifactSearchResults {
	m := &ArtifactSearchResults{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateArtifactSearchResultsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateArtifactSearchResultsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewArtifactSearchResults(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *ArtifactSearchResults) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifacts gets the artifacts property value. The artifacts returned in the result set.
func (m *ArtifactSearchResults) GetArtifacts() []SearchedArtifactable {
	return m.artifacts
}

// GetCount gets the count property value. The total number of artifacts that matched the query that produced the result set (may be more than the number of artifacts in the result set).
func (m *ArtifactSearchResults) GetCount() *int32 {
	return m.count
}

// GetFieldDeserializers the deserialization information for the current model
func (m *ArtifactSearchResults) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["artifacts"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateSearchedArtifactFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]SearchedArtifactable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(SearchedArtifactable)
				}
			}
			m.SetArtifacts(res)
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
func (m *ArtifactSearchResults) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetArtifacts() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetArtifacts()))
		for i, v := range m.GetArtifacts() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("artifacts", cast)
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
func (m *ArtifactSearchResults) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifacts sets the artifacts property value. The artifacts returned in the result set.
func (m *ArtifactSearchResults) SetArtifacts(value []SearchedArtifactable) {
	m.artifacts = value
}

// SetCount sets the count property value. The total number of artifacts that matched the query that produced the result set (may be more than the number of artifacts in the result set).
func (m *ArtifactSearchResults) SetCount(value *int32) {
	m.count = value
}

// ArtifactSearchResultsable
type ArtifactSearchResultsable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifacts() []SearchedArtifactable
	GetCount() *int32
	SetArtifacts(value []SearchedArtifactable)
	SetCount(value *int32)
}
