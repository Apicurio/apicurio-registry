package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// RenderPromptResponse response from rendering a prompt template.
type RenderPromptResponse struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifact ID.
	artifactId *string
	// The group ID of the artifact.
	groupId *string
	// The rendered prompt with all variables substituted.
	rendered *string
	// Any validation errors encountered when validating variables against the template schema.
	validationErrors []RenderValidationErrorable
	// The version of the artifact that was rendered.
	version *string
}

// NewRenderPromptResponse instantiates a new RenderPromptResponse and sets the default values.
func NewRenderPromptResponse() *RenderPromptResponse {
	m := &RenderPromptResponse{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateRenderPromptResponseFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateRenderPromptResponseFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewRenderPromptResponse(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *RenderPromptResponse) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The artifact ID.
// returns a *string when successful
func (m *RenderPromptResponse) GetArtifactId() *string {
	return m.artifactId
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *RenderPromptResponse) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["artifactId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetArtifactId(val)
		}
		return nil
	}
	res["groupId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetGroupId(val)
		}
		return nil
	}
	res["rendered"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetRendered(val)
		}
		return nil
	}
	res["validationErrors"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateRenderValidationErrorFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]RenderValidationErrorable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(RenderValidationErrorable)
				}
			}
			m.SetValidationErrors(res)
		}
		return nil
	}
	res["version"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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

// GetGroupId gets the groupId property value. The group ID of the artifact.
// returns a *string when successful
func (m *RenderPromptResponse) GetGroupId() *string {
	return m.groupId
}

// GetRendered gets the rendered property value. The rendered prompt with all variables substituted.
// returns a *string when successful
func (m *RenderPromptResponse) GetRendered() *string {
	return m.rendered
}

// GetValidationErrors gets the validationErrors property value. Any validation errors encountered when validating variables against the template schema.
// returns a []RenderValidationErrorable when successful
func (m *RenderPromptResponse) GetValidationErrors() []RenderValidationErrorable {
	return m.validationErrors
}

// GetVersion gets the version property value. The version of the artifact that was rendered.
// returns a *string when successful
func (m *RenderPromptResponse) GetVersion() *string {
	return m.version
}

// Serialize serializes information the current object
func (m *RenderPromptResponse) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("artifactId", m.GetArtifactId())
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
		err := writer.WriteStringValue("rendered", m.GetRendered())
		if err != nil {
			return err
		}
	}
	if m.GetValidationErrors() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetValidationErrors()))
		for i, v := range m.GetValidationErrors() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("validationErrors", cast)
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

// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *RenderPromptResponse) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The artifact ID.
func (m *RenderPromptResponse) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetGroupId sets the groupId property value. The group ID of the artifact.
func (m *RenderPromptResponse) SetGroupId(value *string) {
	m.groupId = value
}

// SetRendered sets the rendered property value. The rendered prompt with all variables substituted.
func (m *RenderPromptResponse) SetRendered(value *string) {
	m.rendered = value
}

// SetValidationErrors sets the validationErrors property value. Any validation errors encountered when validating variables against the template schema.
func (m *RenderPromptResponse) SetValidationErrors(value []RenderValidationErrorable) {
	m.validationErrors = value
}

// SetVersion sets the version property value. The version of the artifact that was rendered.
func (m *RenderPromptResponse) SetVersion(value *string) {
	m.version = value
}

type RenderPromptResponseable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetGroupId() *string
	GetRendered() *string
	GetValidationErrors() []RenderValidationErrorable
	GetVersion() *string
	SetArtifactId(value *string)
	SetGroupId(value *string)
	SetRendered(value *string)
	SetValidationErrors(value []RenderValidationErrorable)
	SetVersion(value *string)
}
