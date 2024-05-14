package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// UserInterfaceConfig defines the user interface configuration data type.
type UserInterfaceConfig struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The auth property
    auth UserInterfaceConfigAuthable
    // The features property
    features UserInterfaceConfigFeaturesable
    // The ui property
    ui UserInterfaceConfigUiable
}
// NewUserInterfaceConfig instantiates a new UserInterfaceConfig and sets the default values.
func NewUserInterfaceConfig()(*UserInterfaceConfig) {
    m := &UserInterfaceConfig{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateUserInterfaceConfigFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateUserInterfaceConfigFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewUserInterfaceConfig(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *UserInterfaceConfig) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetAuth gets the auth property value. The auth property
func (m *UserInterfaceConfig) GetAuth()(UserInterfaceConfigAuthable) {
    return m.auth
}
// GetFeatures gets the features property value. The features property
func (m *UserInterfaceConfig) GetFeatures()(UserInterfaceConfigFeaturesable) {
    return m.features
}
// GetFieldDeserializers the deserialization information for the current model
func (m *UserInterfaceConfig) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["auth"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateUserInterfaceConfigAuthFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetAuth(val.(UserInterfaceConfigAuthable))
        }
        return nil
    }
    res["features"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateUserInterfaceConfigFeaturesFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetFeatures(val.(UserInterfaceConfigFeaturesable))
        }
        return nil
    }
    res["ui"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateUserInterfaceConfigUiFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetUi(val.(UserInterfaceConfigUiable))
        }
        return nil
    }
    return res
}
// GetUi gets the ui property value. The ui property
func (m *UserInterfaceConfig) GetUi()(UserInterfaceConfigUiable) {
    return m.ui
}
// Serialize serializes information the current object
func (m *UserInterfaceConfig) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteObjectValue("auth", m.GetAuth())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteObjectValue("features", m.GetFeatures())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteObjectValue("ui", m.GetUi())
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
func (m *UserInterfaceConfig) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetAuth sets the auth property value. The auth property
func (m *UserInterfaceConfig) SetAuth(value UserInterfaceConfigAuthable)() {
    m.auth = value
}
// SetFeatures sets the features property value. The features property
func (m *UserInterfaceConfig) SetFeatures(value UserInterfaceConfigFeaturesable)() {
    m.features = value
}
// SetUi sets the ui property value. The ui property
func (m *UserInterfaceConfig) SetUi(value UserInterfaceConfigUiable)() {
    m.ui = value
}
// UserInterfaceConfigable 
type UserInterfaceConfigable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetAuth()(UserInterfaceConfigAuthable)
    GetFeatures()(UserInterfaceConfigFeaturesable)
    GetUi()(UserInterfaceConfigUiable)
    SetAuth(value UserInterfaceConfigAuthable)()
    SetFeatures(value UserInterfaceConfigFeaturesable)()
    SetUi(value UserInterfaceConfigUiable)()
}
