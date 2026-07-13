package groups

import (
    i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e "time"
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

type ItemArtifactsItemContractAudit struct {
    // The action property
    action *string
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The artifactId property
    artifactId *string
    // The auditId property
    auditId *int32
    // The createdOn property
    createdOn *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
    // The details property
    details *string
    // The groupId property
    groupId *string
    // The principal property
    principal *string
    // The version property
    version *string
}
// NewItemArtifactsItemContractAudit instantiates a new ItemArtifactsItemContractAudit and sets the default values.
func NewItemArtifactsItemContractAudit()(*ItemArtifactsItemContractAudit) {
    m := &ItemArtifactsItemContractAudit{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateItemArtifactsItemContractAuditFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateItemArtifactsItemContractAuditFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewItemArtifactsItemContractAudit(), nil
}
// GetAction gets the action property value. The action property
// returns a *string when successful
func (m *ItemArtifactsItemContractAudit) GetAction()(*string) {
    return m.action
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ItemArtifactsItemContractAudit) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetArtifactId gets the artifactId property value. The artifactId property
// returns a *string when successful
func (m *ItemArtifactsItemContractAudit) GetArtifactId()(*string) {
    return m.artifactId
}
// GetAuditId gets the auditId property value. The auditId property
// returns a *int32 when successful
func (m *ItemArtifactsItemContractAudit) GetAuditId()(*int32) {
    return m.auditId
}
// GetCreatedOn gets the createdOn property value. The createdOn property
// returns a *Time when successful
func (m *ItemArtifactsItemContractAudit) GetCreatedOn()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
    return m.createdOn
}
// GetDetails gets the details property value. The details property
// returns a *string when successful
func (m *ItemArtifactsItemContractAudit) GetDetails()(*string) {
    return m.details
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ItemArtifactsItemContractAudit) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
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
    res["auditId"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetAuditId(val)
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
    res["details"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetDetails(val)
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
    res["principal"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetPrincipal(val)
        }
        return nil
    }
    res["version"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetVersion(val)
        }
        return nil
    }
    return res
}
// GetGroupId gets the groupId property value. The groupId property
// returns a *string when successful
func (m *ItemArtifactsItemContractAudit) GetGroupId()(*string) {
    return m.groupId
}
// GetPrincipal gets the principal property value. The principal property
// returns a *string when successful
func (m *ItemArtifactsItemContractAudit) GetPrincipal()(*string) {
    return m.principal
}
// GetVersion gets the version property value. The version property
// returns a *string when successful
func (m *ItemArtifactsItemContractAudit) GetVersion()(*string) {
    return m.version
}
// Serialize serializes information the current object
func (m *ItemArtifactsItemContractAudit) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteStringValue("action", m.GetAction())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("artifactId", m.GetArtifactId())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteInt32Value("auditId", m.GetAuditId())
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
        err := writer.WriteStringValue("details", m.GetDetails())
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
    {
        err := writer.WriteStringValue("principal", m.GetPrincipal())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("version", m.GetVersion())
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
func (m *ItemArtifactsItemContractAudit) SetAction(value *string)() {
    m.action = value
}
// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *ItemArtifactsItemContractAudit) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetArtifactId sets the artifactId property value. The artifactId property
func (m *ItemArtifactsItemContractAudit) SetArtifactId(value *string)() {
    m.artifactId = value
}
// SetAuditId sets the auditId property value. The auditId property
func (m *ItemArtifactsItemContractAudit) SetAuditId(value *int32)() {
    m.auditId = value
}
// SetCreatedOn sets the createdOn property value. The createdOn property
func (m *ItemArtifactsItemContractAudit) SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)() {
    m.createdOn = value
}
// SetDetails sets the details property value. The details property
func (m *ItemArtifactsItemContractAudit) SetDetails(value *string)() {
    m.details = value
}
// SetGroupId sets the groupId property value. The groupId property
func (m *ItemArtifactsItemContractAudit) SetGroupId(value *string)() {
    m.groupId = value
}
// SetPrincipal sets the principal property value. The principal property
func (m *ItemArtifactsItemContractAudit) SetPrincipal(value *string)() {
    m.principal = value
}
// SetVersion sets the version property value. The version property
func (m *ItemArtifactsItemContractAudit) SetVersion(value *string)() {
    m.version = value
}
type ItemArtifactsItemContractAuditable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetAction()(*string)
    GetArtifactId()(*string)
    GetAuditId()(*int32)
    GetCreatedOn()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
    GetDetails()(*string)
    GetGroupId()(*string)
    GetPrincipal()(*string)
    GetVersion()(*string)
    SetAction(value *string)()
    SetArtifactId(value *string)()
    SetAuditId(value *int32)()
    SetCreatedOn(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)()
    SetDetails(value *string)()
    SetGroupId(value *string)()
    SetPrincipal(value *string)()
    SetVersion(value *string)()
}
