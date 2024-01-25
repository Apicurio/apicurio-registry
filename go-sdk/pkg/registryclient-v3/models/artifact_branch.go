package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ArtifactBranch 
type ArtifactBranch struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The ID of a single artifact.
    artifactId *string
    // The ID of a single artifact branch.
    branchId *string
    // An ID of a single artifact group.
    groupId *string
    // The versions property
    versions []string
}
// NewArtifactBranch instantiates a new ArtifactBranch and sets the default values.
func NewArtifactBranch()(*ArtifactBranch) {
    m := &ArtifactBranch{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateArtifactBranchFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateArtifactBranchFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewArtifactBranch(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *ArtifactBranch) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetArtifactId gets the artifactId property value. The ID of a single artifact.
func (m *ArtifactBranch) GetArtifactId()(*string) {
    return m.artifactId
}
// GetBranchId gets the branchId property value. The ID of a single artifact branch.
func (m *ArtifactBranch) GetBranchId()(*string) {
    return m.branchId
}
// GetFieldDeserializers the deserialization information for the current model
func (m *ArtifactBranch) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["artifactId"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetArtifactId(val)
        }
        return nil
    }
    res["branchId"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetBranchId(val)
        }
        return nil
    }
    res["groupId"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetGroupId(val)
        }
        return nil
    }
    res["versions"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
// GetGroupId gets the groupId property value. An ID of a single artifact group.
func (m *ArtifactBranch) GetGroupId()(*string) {
    return m.groupId
}
// GetVersions gets the versions property value. The versions property
func (m *ArtifactBranch) GetVersions()([]string) {
    return m.versions
}
// Serialize serializes information the current object
func (m *ArtifactBranch) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
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
        err := writer.WriteStringValue("groupId", m.GetGroupId())
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
func (m *ArtifactBranch) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetArtifactId sets the artifactId property value. The ID of a single artifact.
func (m *ArtifactBranch) SetArtifactId(value *string)() {
    m.artifactId = value
}
// SetBranchId sets the branchId property value. The ID of a single artifact branch.
func (m *ArtifactBranch) SetBranchId(value *string)() {
    m.branchId = value
}
// SetGroupId sets the groupId property value. An ID of a single artifact group.
func (m *ArtifactBranch) SetGroupId(value *string)() {
    m.groupId = value
}
// SetVersions sets the versions property value. The versions property
func (m *ArtifactBranch) SetVersions(value []string)() {
    m.versions = value
}
// ArtifactBranchable 
type ArtifactBranchable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetArtifactId()(*string)
    GetBranchId()(*string)
    GetGroupId()(*string)
    GetVersions()([]string)
    SetArtifactId(value *string)()
    SetBranchId(value *string)()
    SetGroupId(value *string)()
    SetVersions(value []string)()
}
