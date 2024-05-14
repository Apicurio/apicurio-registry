package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// UserInterfaceConfigUi 
type UserInterfaceConfigUi struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The contextPath property
    contextPath *string
    // The navPrefixPath property
    navPrefixPath *string
    // The oaiDocsUrl property
    oaiDocsUrl *string
}
// NewUserInterfaceConfigUi instantiates a new UserInterfaceConfigUi and sets the default values.
func NewUserInterfaceConfigUi()(*UserInterfaceConfigUi) {
    m := &UserInterfaceConfigUi{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateUserInterfaceConfigUiFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateUserInterfaceConfigUiFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewUserInterfaceConfigUi(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *UserInterfaceConfigUi) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetContextPath gets the contextPath property value. The contextPath property
func (m *UserInterfaceConfigUi) GetContextPath()(*string) {
    return m.contextPath
}
// GetFieldDeserializers the deserialization information for the current model
func (m *UserInterfaceConfigUi) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["contextPath"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetContextPath(val)
        }
        return nil
    }
    res["navPrefixPath"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetNavPrefixPath(val)
        }
        return nil
    }
    res["oaiDocsUrl"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetOaiDocsUrl(val)
        }
        return nil
    }
    return res
}
// GetNavPrefixPath gets the navPrefixPath property value. The navPrefixPath property
func (m *UserInterfaceConfigUi) GetNavPrefixPath()(*string) {
    return m.navPrefixPath
}
// GetOaiDocsUrl gets the oaiDocsUrl property value. The oaiDocsUrl property
func (m *UserInterfaceConfigUi) GetOaiDocsUrl()(*string) {
    return m.oaiDocsUrl
}
// Serialize serializes information the current object
func (m *UserInterfaceConfigUi) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteStringValue("contextPath", m.GetContextPath())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("navPrefixPath", m.GetNavPrefixPath())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("oaiDocsUrl", m.GetOaiDocsUrl())
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
func (m *UserInterfaceConfigUi) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetContextPath sets the contextPath property value. The contextPath property
func (m *UserInterfaceConfigUi) SetContextPath(value *string)() {
    m.contextPath = value
}
// SetNavPrefixPath sets the navPrefixPath property value. The navPrefixPath property
func (m *UserInterfaceConfigUi) SetNavPrefixPath(value *string)() {
    m.navPrefixPath = value
}
// SetOaiDocsUrl sets the oaiDocsUrl property value. The oaiDocsUrl property
func (m *UserInterfaceConfigUi) SetOaiDocsUrl(value *string)() {
    m.oaiDocsUrl = value
}
// UserInterfaceConfigUiable 
type UserInterfaceConfigUiable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetContextPath()(*string)
    GetNavPrefixPath()(*string)
    GetOaiDocsUrl()(*string)
    SetContextPath(value *string)()
    SetNavPrefixPath(value *string)()
    SetOaiDocsUrl(value *string)()
}
