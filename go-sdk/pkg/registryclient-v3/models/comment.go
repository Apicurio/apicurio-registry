package models

import (
    i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e "time"
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// Comment 
type Comment struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The commentId property
    commentId *string
    // The createdBy property
    createdBy *string
    // The createdOn property
    createdOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
    // The value property
    value *string
}
// NewComment instantiates a new Comment and sets the default values.
func NewComment()(*Comment) {
    m := &Comment{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateCommentFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateCommentFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewComment(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *Comment) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetCommentId gets the commentId property value. The commentId property
func (m *Comment) GetCommentId()(*string) {
    return m.commentId
}
// GetCreatedBy gets the createdBy property value. The createdBy property
func (m *Comment) GetCreatedBy()(*string) {
    return m.createdBy
}
// GetCreatedOn gets the createdOn property value. The createdOn property
func (m *Comment) GetCreatedOn()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
    return m.createdOn
}
// GetFieldDeserializers the deserialization information for the current model
func (m *Comment) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["commentId"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetCommentId(val)
        }
        return nil
    }
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
    res["value"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetValue(val)
        }
        return nil
    }
    return res
}
// GetValue gets the value property value. The value property
func (m *Comment) GetValue()(*string) {
    return m.value
}
// Serialize serializes information the current object
func (m *Comment) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteStringValue("commentId", m.GetCommentId())
        if err != nil {
            return err
        }
    }
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
        err := writer.WriteStringValue("value", m.GetValue())
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
func (m *Comment) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetCommentId sets the commentId property value. The commentId property
func (m *Comment) SetCommentId(value *string)() {
    m.commentId = value
}
// SetCreatedBy sets the createdBy property value. The createdBy property
func (m *Comment) SetCreatedBy(value *string)() {
    m.createdBy = value
}
// SetCreatedOn sets the createdOn property value. The createdOn property
func (m *Comment) SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)() {
    m.createdOn = value
}
// SetValue sets the value property value. The value property
func (m *Comment) SetValue(value *string)() {
    m.value = value
}
// Commentable 
type Commentable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetCommentId()(*string)
    GetCreatedBy()(*string)
    GetCreatedOn()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
    GetValue()(*string)
    SetCommentId(value *string)()
    SetCreatedBy(value *string)()
    SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)()
    SetValue(value *string)()
}
