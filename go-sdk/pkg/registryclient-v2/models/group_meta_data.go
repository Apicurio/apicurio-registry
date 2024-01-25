package models

import (
    i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e "time"
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// GroupMetaData 
type GroupMetaData struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The createdBy property
    createdBy *string
    // The createdOn property
    createdOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
    // The description property
    description *string
    // An ID of a single artifact group.
    id *string
    // The modifiedBy property
    modifiedBy *string
    // The modifiedOn property
    modifiedOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
    // User-defined name-value pairs. Name and value must be strings.
    properties Propertiesable
}
// NewGroupMetaData instantiates a new GroupMetaData and sets the default values.
func NewGroupMetaData()(*GroupMetaData) {
    m := &GroupMetaData{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateGroupMetaDataFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateGroupMetaDataFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewGroupMetaData(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *GroupMetaData) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetCreatedBy gets the createdBy property value. The createdBy property
func (m *GroupMetaData) GetCreatedBy()(*string) {
    return m.createdBy
}
// GetCreatedOn gets the createdOn property value. The createdOn property
func (m *GroupMetaData) GetCreatedOn()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
    return m.createdOn
}
// GetDescription gets the description property value. The description property
func (m *GroupMetaData) GetDescription()(*string) {
    return m.description
}
// GetFieldDeserializers the deserialization information for the current model
func (m *GroupMetaData) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["createdBy"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetCreatedBy(val)
        }
        return nil
    }
    res["createdOn"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetTimeValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetCreatedOn(val)
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
    res["id"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetId(val)
        }
        return nil
    }
    res["modifiedBy"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetModifiedBy(val)
        }
        return nil
    }
    res["modifiedOn"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetTimeValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetModifiedOn(val)
        }
        return nil
    }
    res["properties"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreatePropertiesFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetProperties(val.(Propertiesable))
        }
        return nil
    }
    return res
}
// GetId gets the id property value. An ID of a single artifact group.
func (m *GroupMetaData) GetId()(*string) {
    return m.id
}
// GetModifiedBy gets the modifiedBy property value. The modifiedBy property
func (m *GroupMetaData) GetModifiedBy()(*string) {
    return m.modifiedBy
}
// GetModifiedOn gets the modifiedOn property value. The modifiedOn property
func (m *GroupMetaData) GetModifiedOn()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
    return m.modifiedOn
}
// GetProperties gets the properties property value. User-defined name-value pairs. Name and value must be strings.
func (m *GroupMetaData) GetProperties()(Propertiesable) {
    return m.properties
}
// Serialize serializes information the current object
func (m *GroupMetaData) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteStringValue("createdBy", m.GetCreatedBy())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteTimeValue("createdOn", m.GetCreatedOn())
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
        err := writer.WriteStringValue("id", m.GetId())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("modifiedBy", m.GetModifiedBy())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteTimeValue("modifiedOn", m.GetModifiedOn())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteObjectValue("properties", m.GetProperties())
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
func (m *GroupMetaData) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetCreatedBy sets the createdBy property value. The createdBy property
func (m *GroupMetaData) SetCreatedBy(value *string)() {
    m.createdBy = value
}
// SetCreatedOn sets the createdOn property value. The createdOn property
func (m *GroupMetaData) SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)() {
    m.createdOn = value
}
// SetDescription sets the description property value. The description property
func (m *GroupMetaData) SetDescription(value *string)() {
    m.description = value
}
// SetId sets the id property value. An ID of a single artifact group.
func (m *GroupMetaData) SetId(value *string)() {
    m.id = value
}
// SetModifiedBy sets the modifiedBy property value. The modifiedBy property
func (m *GroupMetaData) SetModifiedBy(value *string)() {
    m.modifiedBy = value
}
// SetModifiedOn sets the modifiedOn property value. The modifiedOn property
func (m *GroupMetaData) SetModifiedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)() {
    m.modifiedOn = value
}
// SetProperties sets the properties property value. User-defined name-value pairs. Name and value must be strings.
func (m *GroupMetaData) SetProperties(value Propertiesable)() {
    m.properties = value
}
// GroupMetaDataable 
type GroupMetaDataable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetCreatedBy()(*string)
    GetCreatedOn()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
    GetDescription()(*string)
    GetId()(*string)
    GetModifiedBy()(*string)
    GetModifiedOn()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
    GetProperties()(Propertiesable)
    SetCreatedBy(value *string)()
    SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)()
    SetDescription(value *string)()
    SetId(value *string)()
    SetModifiedBy(value *string)()
    SetModifiedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)()
    SetProperties(value Propertiesable)()
}
