package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// VersionSearchResults describes the response received when searching for artifacts.
type VersionSearchResults struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The total number of versions that matched the query (may be more than the number of versionsreturned in the result set).
    count *int32
    // The collection of artifact versions returned in the result set.
    versions []SearchedVersionable
}
// NewVersionSearchResults instantiates a new VersionSearchResults and sets the default values.
func NewVersionSearchResults()(*VersionSearchResults) {
    m := &VersionSearchResults{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateVersionSearchResultsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateVersionSearchResultsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewVersionSearchResults(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *VersionSearchResults) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetCount gets the count property value. The total number of versions that matched the query (may be more than the number of versionsreturned in the result set).
func (m *VersionSearchResults) GetCount()(*int32) {
    return m.count
}
// GetFieldDeserializers the deserialization information for the current model
func (m *VersionSearchResults) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["count"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetCount(val)
        }
        return nil
    }
    res["versions"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetCollectionOfObjectValues(CreateSearchedVersionFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            res := make([]SearchedVersionable, len(val))
            for i, v := range val {
                if v != nil {
                    res[i] = v.(SearchedVersionable)
                }
            }
            m.SetVersions(res)
        }
        return nil
    }
    return res
}
// GetVersions gets the versions property value. The collection of artifact versions returned in the result set.
func (m *VersionSearchResults) GetVersions()([]SearchedVersionable) {
    return m.versions
}
// Serialize serializes information the current object
func (m *VersionSearchResults) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteInt32Value("count", m.GetCount())
        if err != nil {
            return err
        }
    }
    if m.GetVersions() != nil {
        cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetVersions()))
        for i, v := range m.GetVersions() {
            if v != nil {
                cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
            }
        }
        err := writer.WriteCollectionOfObjectValues("versions", cast)
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
func (m *VersionSearchResults) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetCount sets the count property value. The total number of versions that matched the query (may be more than the number of versionsreturned in the result set).
func (m *VersionSearchResults) SetCount(value *int32)() {
    m.count = value
}
// SetVersions sets the versions property value. The collection of artifact versions returned in the result set.
func (m *VersionSearchResults) SetVersions(value []SearchedVersionable)() {
    m.versions = value
}
// VersionSearchResultsable 
type VersionSearchResultsable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetCount()(*int32)
    GetVersions()([]SearchedVersionable)
    SetCount(value *int32)()
    SetVersions(value []SearchedVersionable)()
}
