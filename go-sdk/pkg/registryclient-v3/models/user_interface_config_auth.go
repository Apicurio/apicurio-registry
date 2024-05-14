package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// UserInterfaceConfigAuth 
type UserInterfaceConfigAuth struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The obacEnabled property
    obacEnabled *bool
    // User-defined name-value pairs. Name and value must be strings.
    options Labelsable
    // The rbacEnabled property
    rbacEnabled *bool
    // The type property
    typeEscaped *UserInterfaceConfigAuth_type
}
// NewUserInterfaceConfigAuth instantiates a new UserInterfaceConfigAuth and sets the default values.
func NewUserInterfaceConfigAuth()(*UserInterfaceConfigAuth) {
    m := &UserInterfaceConfigAuth{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateUserInterfaceConfigAuthFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateUserInterfaceConfigAuthFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewUserInterfaceConfigAuth(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *UserInterfaceConfigAuth) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetFieldDeserializers the deserialization information for the current model
func (m *UserInterfaceConfigAuth) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["obacEnabled"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetBoolValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetObacEnabled(val)
        }
        return nil
    }
    res["options"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateLabelsFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetOptions(val.(Labelsable))
        }
        return nil
    }
    res["rbacEnabled"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetBoolValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetRbacEnabled(val)
        }
        return nil
    }
    res["type"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetEnumValue(ParseUserInterfaceConfigAuth_type)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetTypeEscaped(val.(*UserInterfaceConfigAuth_type))
        }
        return nil
    }
    return res
}
// GetObacEnabled gets the obacEnabled property value. The obacEnabled property
func (m *UserInterfaceConfigAuth) GetObacEnabled()(*bool) {
    return m.obacEnabled
}
// GetOptions gets the options property value. User-defined name-value pairs. Name and value must be strings.
func (m *UserInterfaceConfigAuth) GetOptions()(Labelsable) {
    return m.options
}
// GetRbacEnabled gets the rbacEnabled property value. The rbacEnabled property
func (m *UserInterfaceConfigAuth) GetRbacEnabled()(*bool) {
    return m.rbacEnabled
}
// GetTypeEscaped gets the type property value. The type property
func (m *UserInterfaceConfigAuth) GetTypeEscaped()(*UserInterfaceConfigAuth_type) {
    return m.typeEscaped
}
// Serialize serializes information the current object
func (m *UserInterfaceConfigAuth) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteBoolValue("obacEnabled", m.GetObacEnabled())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteObjectValue("options", m.GetOptions())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteBoolValue("rbacEnabled", m.GetRbacEnabled())
        if err != nil {
            return err
        }
    }
    if m.GetTypeEscaped() != nil {
        cast := (*m.GetTypeEscaped()).String()
        err := writer.WriteStringValue("type", &cast)
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
func (m *UserInterfaceConfigAuth) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetObacEnabled sets the obacEnabled property value. The obacEnabled property
func (m *UserInterfaceConfigAuth) SetObacEnabled(value *bool)() {
    m.obacEnabled = value
}
// SetOptions sets the options property value. User-defined name-value pairs. Name and value must be strings.
func (m *UserInterfaceConfigAuth) SetOptions(value Labelsable)() {
    m.options = value
}
// SetRbacEnabled sets the rbacEnabled property value. The rbacEnabled property
func (m *UserInterfaceConfigAuth) SetRbacEnabled(value *bool)() {
    m.rbacEnabled = value
}
// SetTypeEscaped sets the type property value. The type property
func (m *UserInterfaceConfigAuth) SetTypeEscaped(value *UserInterfaceConfigAuth_type)() {
    m.typeEscaped = value
}
// UserInterfaceConfigAuthable 
type UserInterfaceConfigAuthable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetObacEnabled()(*bool)
    GetOptions()(Labelsable)
    GetRbacEnabled()(*bool)
    GetTypeEscaped()(*UserInterfaceConfigAuth_type)
    SetObacEnabled(value *bool)()
    SetOptions(value Labelsable)()
    SetRbacEnabled(value *bool)()
    SetTypeEscaped(value *UserInterfaceConfigAuth_type)()
}
