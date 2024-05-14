package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// CreateArtifact data sent when creating a new artifact.
type CreateArtifact struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The ID of a single artifact.
    artifactId *string
    // The description property
    description *string
    // The firstVersion property
    firstVersion CreateVersionable
    // User-defined name-value pairs. Name and value must be strings.
    labels Labelsable
    // The name property
    name *string
    // The type property
    typeEscaped *string
}
// NewCreateArtifact instantiates a new CreateArtifact and sets the default values.
func NewCreateArtifact()(*CreateArtifact) {
    m := &CreateArtifact{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateCreateArtifactFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateCreateArtifactFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewCreateArtifact(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *CreateArtifact) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetArtifactId gets the artifactId property value. The ID of a single artifact.
func (m *CreateArtifact) GetArtifactId()(*string) {
    return m.artifactId
}
// GetDescription gets the description property value. The description property
func (m *CreateArtifact) GetDescription()(*string) {
    return m.description
}
// GetFieldDeserializers the deserialization information for the current model
func (m *CreateArtifact) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
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
    res["description"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetDescription(val)
        }
        return nil
    }
    res["firstVersion"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateCreateVersionFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetFirstVersion(val.(CreateVersionable))
        }
        return nil
    }
    res["labels"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateLabelsFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetLabels(val.(Labelsable))
        }
        return nil
    }
    res["name"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetName(val)
        }
        return nil
    }
    res["type"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
// GetFirstVersion gets the firstVersion property value. The firstVersion property
func (m *CreateArtifact) GetFirstVersion()(CreateVersionable) {
    return m.firstVersion
}
// GetLabels gets the labels property value. User-defined name-value pairs. Name and value must be strings.
func (m *CreateArtifact) GetLabels()(Labelsable) {
    return m.labels
}
// GetName gets the name property value. The name property
func (m *CreateArtifact) GetName()(*string) {
    return m.name
}
// GetTypeEscaped gets the type property value. The type property
func (m *CreateArtifact) GetTypeEscaped()(*string) {
    return m.typeEscaped
}
// Serialize serializes information the current object
func (m *CreateArtifact) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteStringValue("artifactId", m.GetArtifactId())
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
        err := writer.WriteObjectValue("firstVersion", m.GetFirstVersion())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteObjectValue("labels", m.GetLabels())
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
func (m *CreateArtifact) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetArtifactId sets the artifactId property value. The ID of a single artifact.
func (m *CreateArtifact) SetArtifactId(value *string)() {
    m.artifactId = value
}
// SetDescription sets the description property value. The description property
func (m *CreateArtifact) SetDescription(value *string)() {
    m.description = value
}
// SetFirstVersion sets the firstVersion property value. The firstVersion property
func (m *CreateArtifact) SetFirstVersion(value CreateVersionable)() {
    m.firstVersion = value
}
// SetLabels sets the labels property value. User-defined name-value pairs. Name and value must be strings.
func (m *CreateArtifact) SetLabels(value Labelsable)() {
    m.labels = value
}
// SetName sets the name property value. The name property
func (m *CreateArtifact) SetName(value *string)() {
    m.name = value
}
// SetTypeEscaped sets the type property value. The type property
func (m *CreateArtifact) SetTypeEscaped(value *string)() {
    m.typeEscaped = value
}
// CreateArtifactable 
type CreateArtifactable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetArtifactId()(*string)
    GetDescription()(*string)
    GetFirstVersion()(CreateVersionable)
    GetLabels()(Labelsable)
    GetName()(*string)
    GetTypeEscaped()(*string)
    SetArtifactId(value *string)()
    SetDescription(value *string)()
    SetFirstVersion(value CreateVersionable)()
    SetLabels(value Labelsable)()
    SetName(value *string)()
    SetTypeEscaped(value *string)()
}
