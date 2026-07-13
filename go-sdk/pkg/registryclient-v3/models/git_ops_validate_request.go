package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// GitOpsValidateRequest request body for creating a dry-run validation task.
type GitOpsValidateRequest struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // Git ref to validate (branch name, tag, or PR ref like `refs/pull/42/head`).
    ref *string
    // Repository ID to validate against. Must match a configured repository.
    repoId *string
    // Validation type. Currently only `pull` is supported.
    typeEscaped *GitOpsValidateRequest_type
}
// NewGitOpsValidateRequest instantiates a new GitOpsValidateRequest and sets the default values.
func NewGitOpsValidateRequest()(*GitOpsValidateRequest) {
    m := &GitOpsValidateRequest{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateGitOpsValidateRequestFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateGitOpsValidateRequestFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewGitOpsValidateRequest(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *GitOpsValidateRequest) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *GitOpsValidateRequest) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
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
    res["type"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetEnumValue(ParseGitOpsValidateRequest_type)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetTypeEscaped(val.(*GitOpsValidateRequest_type))
        }
        return nil
    }
    return res
}
// GetRef gets the ref property value. Git ref to validate (branch name, tag, or PR ref like `refs/pull/42/head`).
// returns a *string when successful
func (m *GitOpsValidateRequest) GetRef()(*string) {
    return m.ref
}
// GetRepoId gets the repoId property value. Repository ID to validate against. Must match a configured repository.
// returns a *string when successful
func (m *GitOpsValidateRequest) GetRepoId()(*string) {
    return m.repoId
}
// GetTypeEscaped gets the type property value. Validation type. Currently only `pull` is supported.
// returns a *GitOpsValidateRequest_type when successful
func (m *GitOpsValidateRequest) GetTypeEscaped()(*GitOpsValidateRequest_type) {
    return m.typeEscaped
}
// Serialize serializes information the current object
func (m *GitOpsValidateRequest) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
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
func (m *GitOpsValidateRequest) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetRef sets the ref property value. Git ref to validate (branch name, tag, or PR ref like `refs/pull/42/head`).
func (m *GitOpsValidateRequest) SetRef(value *string)() {
    m.ref = value
}
// SetRepoId sets the repoId property value. Repository ID to validate against. Must match a configured repository.
func (m *GitOpsValidateRequest) SetRepoId(value *string)() {
    m.repoId = value
}
// SetTypeEscaped sets the type property value. Validation type. Currently only `pull` is supported.
func (m *GitOpsValidateRequest) SetTypeEscaped(value *GitOpsValidateRequest_type)() {
    m.typeEscaped = value
}
type GitOpsValidateRequestable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetRef()(*string)
    GetRepoId()(*string)
    GetTypeEscaped()(*GitOpsValidateRequest_type)
    SetRef(value *string)()
    SetRepoId(value *string)()
    SetTypeEscaped(value *GitOpsValidateRequest_type)()
}
