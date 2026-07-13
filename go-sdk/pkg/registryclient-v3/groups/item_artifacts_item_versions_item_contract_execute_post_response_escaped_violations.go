package groups

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type ItemArtifactsItemVersionsItemContractExecutePostResponse_violations struct {
    // The action property
    action *string
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The message property
    message *string
    // The ruleName property
    ruleName *string
}
// NewItemArtifactsItemVersionsItemContractExecutePostResponse_violations instantiates a new ItemArtifactsItemVersionsItemContractExecutePostResponse_violations and sets the default values.
func NewItemArtifactsItemVersionsItemContractExecutePostResponse_violations()(*ItemArtifactsItemVersionsItemContractExecutePostResponse_violations) {
    m := &ItemArtifactsItemVersionsItemContractExecutePostResponse_violations{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateItemArtifactsItemVersionsItemContractExecutePostResponse_violationsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemVersionsItemContractExecutePostResponse_violationsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemVersionsItemContractExecutePostResponse_violations(), nil
}
// GetAction gets the action property value. The action property
// returns a *string when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse_violations) GetAction()(*string) {
    return m.action
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse_violations) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse_violations) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["action"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetAction(val)
        }
        return nil
    }
    res["message"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetMessage(val)
        }
        return nil
    }
    res["ruleName"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetRuleName(val)
        }
        return nil
    }
    return res
}
// GetMessage gets the message property value. The message property
// returns a *string when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse_violations) GetMessage()(*string) {
    return m.message
}
// GetRuleName gets the ruleName property value. The ruleName property
// returns a *string when successful
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse_violations) GetRuleName()(*string) {
    return m.ruleName
}
// Serialize serializes information the current object
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse_violations) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteStringValue("action", m.GetAction())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("message", m.GetMessage())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("ruleName", m.GetRuleName())
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
// SetAction sets the action property value. The action property
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse_violations) SetAction(value *string)() {
    m.action = value
}
// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse_violations) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetMessage sets the message property value. The message property
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse_violations) SetMessage(value *string)() {
    m.message = value
}
// SetRuleName sets the ruleName property value. The ruleName property
func (m *ItemArtifactsItemVersionsItemContractExecutePostResponse_violations) SetRuleName(value *string)() {
    m.ruleName = value
}
type ItemArtifactsItemVersionsItemContractExecutePostResponse_violationsable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetAction()(*string)
    GetMessage()(*string)
    GetRuleName()(*string)
    SetAction(value *string)()
    SetMessage(value *string)()
    SetRuleName(value *string)()
}
