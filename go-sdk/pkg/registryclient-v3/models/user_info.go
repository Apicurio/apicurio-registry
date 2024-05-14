package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// UserInfo information about a single user.
type UserInfo struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The admin property
    admin *bool
    // The developer property
    developer *bool
    // The displayName property
    displayName *string
    // The username property
    username *string
    // The viewer property
    viewer *bool
}
// NewUserInfo instantiates a new UserInfo and sets the default values.
func NewUserInfo()(*UserInfo) {
    m := &UserInfo{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateUserInfoFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateUserInfoFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewUserInfo(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *UserInfo) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetAdmin gets the admin property value. The admin property
func (m *UserInfo) GetAdmin()(*bool) {
    return m.admin
}
// GetDeveloper gets the developer property value. The developer property
func (m *UserInfo) GetDeveloper()(*bool) {
    return m.developer
}
// GetDisplayName gets the displayName property value. The displayName property
func (m *UserInfo) GetDisplayName()(*string) {
    return m.displayName
}
// GetFieldDeserializers the deserialization information for the current model
func (m *UserInfo) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["admin"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetBoolValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetAdmin(val)
        }
        return nil
    }
    res["developer"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetBoolValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetDeveloper(val)
        }
        return nil
    }
    res["displayName"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetDisplayName(val)
        }
        return nil
    }
    res["username"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetUsername(val)
        }
        return nil
    }
    res["viewer"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetBoolValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetViewer(val)
        }
        return nil
    }
    return res
}
// GetUsername gets the username property value. The username property
func (m *UserInfo) GetUsername()(*string) {
    return m.username
}
// GetViewer gets the viewer property value. The viewer property
func (m *UserInfo) GetViewer()(*bool) {
    return m.viewer
}
// Serialize serializes information the current object
func (m *UserInfo) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteBoolValue("admin", m.GetAdmin())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteBoolValue("developer", m.GetDeveloper())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("displayName", m.GetDisplayName())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("username", m.GetUsername())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteBoolValue("viewer", m.GetViewer())
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
func (m *UserInfo) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetAdmin sets the admin property value. The admin property
func (m *UserInfo) SetAdmin(value *bool)() {
    m.admin = value
}
// SetDeveloper sets the developer property value. The developer property
func (m *UserInfo) SetDeveloper(value *bool)() {
    m.developer = value
}
// SetDisplayName sets the displayName property value. The displayName property
func (m *UserInfo) SetDisplayName(value *string)() {
    m.displayName = value
}
// SetUsername sets the username property value. The username property
func (m *UserInfo) SetUsername(value *string)() {
    m.username = value
}
// SetViewer sets the viewer property value. The viewer property
func (m *UserInfo) SetViewer(value *bool)() {
    m.viewer = value
}
// UserInfoable 
type UserInfoable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetAdmin()(*bool)
    GetDeveloper()(*bool)
    GetDisplayName()(*string)
    GetUsername()(*string)
    GetViewer()(*bool)
    SetAdmin(value *bool)()
    SetDeveloper(value *bool)()
    SetDisplayName(value *string)()
    SetUsername(value *string)()
    SetViewer(value *bool)()
}
