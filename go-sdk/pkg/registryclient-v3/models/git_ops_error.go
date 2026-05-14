package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// GitOpsError describes an error that occurred during a GitOps sync attempt.
type GitOpsError struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The file path or location where the error occurred. Absent if the error is not file-specific.
	context *string
	// A human-readable description of the error.
	detail *string
	// The source ID (e.g., repository ID) where the error occurred. Absent for global errors not tied to a specific source.
	source *string
}

// NewGitOpsError instantiates a new GitOpsError and sets the default values.
func NewGitOpsError() *GitOpsError {
	m := &GitOpsError{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateGitOpsErrorFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateGitOpsErrorFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewGitOpsError(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *GitOpsError) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetContext gets the context property value. The file path or location where the error occurred. Absent if the error is not file-specific.
// returns a *string when successful
func (m *GitOpsError) GetContext() *string {
	return m.context
}

// GetDetail gets the detail property value. A human-readable description of the error.
// returns a *string when successful
func (m *GitOpsError) GetDetail() *string {
	return m.detail
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *GitOpsError) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["context"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetContext(val)
		}
		return nil
	}
	res["detail"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDetail(val)
		}
		return nil
	}
	res["source"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSource(val)
		}
		return nil
	}
	return res
}

// GetSource gets the source property value. The source ID (e.g., repository ID) where the error occurred. Absent for global errors not tied to a specific source.
// returns a *string when successful
func (m *GitOpsError) GetSource() *string {
	return m.source
}

// Serialize serializes information the current object
func (m *GitOpsError) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("context", m.GetContext())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("detail", m.GetDetail())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("source", m.GetSource())
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
func (m *GitOpsError) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetContext sets the context property value. The file path or location where the error occurred. Absent if the error is not file-specific.
func (m *GitOpsError) SetContext(value *string) {
	m.context = value
}

// SetDetail sets the detail property value. A human-readable description of the error.
func (m *GitOpsError) SetDetail(value *string) {
	m.detail = value
}

// SetSource sets the source property value. The source ID (e.g., repository ID) where the error occurred. Absent for global errors not tied to a specific source.
func (m *GitOpsError) SetSource(value *string) {
	m.source = value
}

type GitOpsErrorable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetContext() *string
	GetDetail() *string
	GetSource() *string
	SetContext(value *string)
	SetDetail(value *string)
	SetSource(value *string)
}
