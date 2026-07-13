package models

import (
    i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e "time"
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// GitOpsValidateTask represents a dry-run validation task with its current state and results.
type GitOpsValidateTask struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // Number of artifacts loaded during validation. Only present when state is `completed`.
    artifactCount *int32
    // ISO 8601 timestamp of when the task completed. Only present when state is `completed` or `failed`.
    completedAt *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
    // ISO 8601 timestamp of when the task was created.
    createdAt *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
    // Validation errors. Empty if validation passed.
    errors []GitOpsErrorable
    // Number of groups loaded during validation. Only present when state is `completed`.
    groupCount *int32
    // Git ref being validated.
    ref *string
    // Repository ID being validated.
    repoId *string
    // Validation result: `success` (all checks passed) or `failure` (validation errors found). Only present when state is `completed`.
    result *GitOpsValidateTask_result
    // Current task state: `pending` (queued, waiting for capacity), `submitted` (request sent to sidecar), `fetching` (sidecar is cloning), `validating` (registry is loading and validating), `completed` (finished with results), `failed` (an error occurred).
    state *GitOpsValidateTask_state
    // Unique identifier for the validation task.
    taskId *string
    // Validation type (`pull` or `push`).
    typeEscaped *string
    // Number of artifact versions loaded during validation. Only present when state is `completed`.
    versionCount *int32
}
// NewGitOpsValidateTask instantiates a new GitOpsValidateTask and sets the default values.
func NewGitOpsValidateTask()(*GitOpsValidateTask) {
    m := &GitOpsValidateTask{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateGitOpsValidateTaskFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateGitOpsValidateTaskFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewGitOpsValidateTask(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *GitOpsValidateTask) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetArtifactCount gets the artifactCount property value. Number of artifacts loaded during validation. Only present when state is `completed`.
// returns a *int32 when successful
func (m *GitOpsValidateTask) GetArtifactCount()(*int32) {
    return m.artifactCount
}
// GetCompletedAt gets the completedAt property value. ISO 8601 timestamp of when the task completed. Only present when state is `completed` or `failed`.
// returns a *Time when successful
func (m *GitOpsValidateTask) GetCompletedAt()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
    return m.completedAt
}
// GetCreatedAt gets the createdAt property value. ISO 8601 timestamp of when the task was created.
// returns a *Time when successful
func (m *GitOpsValidateTask) GetCreatedAt()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
    return m.createdAt
}
// GetErrors gets the errors property value. Validation errors. Empty if validation passed.
// returns a []GitOpsErrorable when successful
func (m *GitOpsValidateTask) GetErrors()([]GitOpsErrorable) {
    return m.errors
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *GitOpsValidateTask) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["artifactCount"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetArtifactCount(val)
        }
        return nil
    }
    res["completedAt"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetTimeValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetCompletedAt(val)
        }
        return nil
    }
    res["createdAt"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetTimeValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetCreatedAt(val)
        }
        return nil
    }
    res["errors"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetCollectionOfObjectValues(CreateGitOpsErrorFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            res := make([]GitOpsErrorable, len(val))
            for i, v := range val {
                if v != nil {
                    res[i] = v.(GitOpsErrorable)
                }
            }
            m.SetErrors(res)
        }
        return nil
    }
    res["groupCount"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetGroupCount(val)
        }
        return nil
    }
    res["ref"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetRef(val)
        }
        return nil
    }
    res["repoId"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetRepoId(val)
        }
        return nil
    }
    res["result"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetEnumValue(ParseGitOpsValidateTask_result)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetResult(val.(*GitOpsValidateTask_result))
        }
        return nil
    }
    res["state"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetEnumValue(ParseGitOpsValidateTask_state)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetState(val.(*GitOpsValidateTask_state))
        }
        return nil
    }
    res["taskId"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetTaskId(val)
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
    res["versionCount"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetVersionCount(val)
        }
        return nil
    }
    return res
}
// GetGroupCount gets the groupCount property value. Number of groups loaded during validation. Only present when state is `completed`.
// returns a *int32 when successful
func (m *GitOpsValidateTask) GetGroupCount()(*int32) {
    return m.groupCount
}
// GetRef gets the ref property value. Git ref being validated.
// returns a *string when successful
func (m *GitOpsValidateTask) GetRef()(*string) {
    return m.ref
}
// GetRepoId gets the repoId property value. Repository ID being validated.
// returns a *string when successful
func (m *GitOpsValidateTask) GetRepoId()(*string) {
    return m.repoId
}
// GetResult gets the result property value. Validation result: `success` (all checks passed) or `failure` (validation errors found). Only present when state is `completed`.
// returns a *GitOpsValidateTask_result when successful
func (m *GitOpsValidateTask) GetResult()(*GitOpsValidateTask_result) {
    return m.result
}
// GetState gets the state property value. Current task state: `pending` (queued, waiting for capacity), `submitted` (request sent to sidecar), `fetching` (sidecar is cloning), `validating` (registry is loading and validating), `completed` (finished with results), `failed` (an error occurred).
// returns a *GitOpsValidateTask_state when successful
func (m *GitOpsValidateTask) GetState()(*GitOpsValidateTask_state) {
    return m.state
}
// GetTaskId gets the taskId property value. Unique identifier for the validation task.
// returns a *string when successful
func (m *GitOpsValidateTask) GetTaskId()(*string) {
    return m.taskId
}
// GetTypeEscaped gets the type property value. Validation type (`pull` or `push`).
// returns a *string when successful
func (m *GitOpsValidateTask) GetTypeEscaped()(*string) {
    return m.typeEscaped
}
// GetVersionCount gets the versionCount property value. Number of artifact versions loaded during validation. Only present when state is `completed`.
// returns a *int32 when successful
func (m *GitOpsValidateTask) GetVersionCount()(*int32) {
    return m.versionCount
}
// Serialize serializes information the current object
func (m *GitOpsValidateTask) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteInt32Value("artifactCount", m.GetArtifactCount())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteTimeValue("completedAt", m.GetCompletedAt())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteTimeValue("createdAt", m.GetCreatedAt())
        if err != nil {
            return err
        }
    }
    if m.GetErrors() != nil {
        cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetErrors()))
        for i, v := range m.GetErrors() {
            if v != nil {
                cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
            }
        }
        err := writer.WriteCollectionOfObjectValues("errors", cast)
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteInt32Value("groupCount", m.GetGroupCount())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("ref", m.GetRef())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("repoId", m.GetRepoId())
        if err != nil {
            return err
        }
    }
    if m.GetResult() != nil {
        cast := (*m.GetResult()).String()
        err := writer.WriteStringValue("result", &cast)
        if err != nil {
            return err
        }
    }
    if m.GetState() != nil {
        cast := (*m.GetState()).String()
        err := writer.WriteStringValue("state", &cast)
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("taskId", m.GetTaskId())
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
        err := writer.WriteInt32Value("versionCount", m.GetVersionCount())
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
func (m *GitOpsValidateTask) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetArtifactCount sets the artifactCount property value. Number of artifacts loaded during validation. Only present when state is `completed`.
func (m *GitOpsValidateTask) SetArtifactCount(value *int32)() {
    m.artifactCount = value
}
// SetCompletedAt sets the completedAt property value. ISO 8601 timestamp of when the task completed. Only present when state is `completed` or `failed`.
func (m *GitOpsValidateTask) SetCompletedAt(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)() {
    m.completedAt = value
}
// SetCreatedAt sets the createdAt property value. ISO 8601 timestamp of when the task was created.
func (m *GitOpsValidateTask) SetCreatedAt(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)() {
    m.createdAt = value
}
// SetErrors sets the errors property value. Validation errors. Empty if validation passed.
func (m *GitOpsValidateTask) SetErrors(value []GitOpsErrorable)() {
    m.errors = value
}
// SetGroupCount sets the groupCount property value. Number of groups loaded during validation. Only present when state is `completed`.
func (m *GitOpsValidateTask) SetGroupCount(value *int32)() {
    m.groupCount = value
}
// SetRef sets the ref property value. Git ref being validated.
func (m *GitOpsValidateTask) SetRef(value *string)() {
    m.ref = value
}
// SetRepoId sets the repoId property value. Repository ID being validated.
func (m *GitOpsValidateTask) SetRepoId(value *string)() {
    m.repoId = value
}
// SetResult sets the result property value. Validation result: `success` (all checks passed) or `failure` (validation errors found). Only present when state is `completed`.
func (m *GitOpsValidateTask) SetResult(value *GitOpsValidateTask_result)() {
    m.result = value
}
// SetState sets the state property value. Current task state: `pending` (queued, waiting for capacity), `submitted` (request sent to sidecar), `fetching` (sidecar is cloning), `validating` (registry is loading and validating), `completed` (finished with results), `failed` (an error occurred).
func (m *GitOpsValidateTask) SetState(value *GitOpsValidateTask_state)() {
    m.state = value
}
// SetTaskId sets the taskId property value. Unique identifier for the validation task.
func (m *GitOpsValidateTask) SetTaskId(value *string)() {
    m.taskId = value
}
// SetTypeEscaped sets the type property value. Validation type (`pull` or `push`).
func (m *GitOpsValidateTask) SetTypeEscaped(value *string)() {
    m.typeEscaped = value
}
// SetVersionCount sets the versionCount property value. Number of artifact versions loaded during validation. Only present when state is `completed`.
func (m *GitOpsValidateTask) SetVersionCount(value *int32)() {
    m.versionCount = value
}
type GitOpsValidateTaskable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetArtifactCount()(*int32)
    GetCompletedAt()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
    GetCreatedAt()(*i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
    GetErrors()([]GitOpsErrorable)
    GetGroupCount()(*int32)
    GetRef()(*string)
    GetRepoId()(*string)
    GetResult()(*GitOpsValidateTask_result)
    GetState()(*GitOpsValidateTask_state)
    GetTaskId()(*string)
    GetTypeEscaped()(*string)
    GetVersionCount()(*int32)
    SetArtifactCount(value *int32)()
    SetCompletedAt(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)()
    SetCreatedAt(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)()
    SetErrors(value []GitOpsErrorable)()
    SetGroupCount(value *int32)()
    SetRef(value *string)()
    SetRepoId(value *string)()
    SetResult(value *GitOpsValidateTask_result)()
    SetState(value *GitOpsValidateTask_state)()
    SetTaskId(value *string)()
    SetTypeEscaped(value *string)()
    SetVersionCount(value *int32)()
}
