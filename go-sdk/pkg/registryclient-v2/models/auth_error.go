package models

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AuthError error response returned when authentication fails (401) or authorization is denied (403).
type AuthError struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ApiError
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// A human-readable explanation specific to this occurrence of the problem. Typically includes the exception class name and message.
	detail *string
	// A URI reference that identifies the specific occurrence of the problem (optional).
	instance *string
	// The name of the error, typically the server exception class name.
	name *string
	// The HTTP status code (401 for Unauthorized, 403 for Forbidden).
	status *int32
	// A short, human-readable summary of the problem type.
	title *string
	// A URI reference [RFC3986] that identifies the problem type (optional).
	typeEscaped *string
}

// NewAuthError instantiates a new AuthError and sets the default values.
func NewAuthError() *AuthError {
	m := &AuthError{
		ApiError: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewApiError(),
	}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateAuthErrorFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAuthErrorFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewAuthError(), nil
}

// Error the primary error message.
// returns a string when successful
func (m *AuthError) Error() string {
	return m.ApiError.Error()
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AuthError) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetDetail gets the detail property value. A human-readable explanation specific to this occurrence of the problem. Typically includes the exception class name and message.
// returns a *string when successful
func (m *AuthError) GetDetail() *string {
	return m.detail
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AuthError) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
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
	res["instance"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetInstance(val)
		}
		return nil
	}
	res["name"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetName(val)
		}
		return nil
	}
	res["status"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetStatus(val)
		}
		return nil
	}
	res["title"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTitle(val)
		}
		return nil
	}
	res["type"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetTypeEscaped(val)
		}
		return nil
	}
	return res
}

// GetInstance gets the instance property value. A URI reference that identifies the specific occurrence of the problem (optional).
// returns a *string when successful
func (m *AuthError) GetInstance() *string {
	return m.instance
}

// GetName gets the name property value. The name of the error, typically the server exception class name.
// returns a *string when successful
func (m *AuthError) GetName() *string {
	return m.name
}

// GetStatus gets the status property value. The HTTP status code (401 for Unauthorized, 403 for Forbidden).
// returns a *int32 when successful
func (m *AuthError) GetStatus() *int32 {
	return m.status
}

// GetTitle gets the title property value. A short, human-readable summary of the problem type.
// returns a *string when successful
func (m *AuthError) GetTitle() *string {
	return m.title
}

// GetTypeEscaped gets the type property value. A URI reference [RFC3986] that identifies the problem type (optional).
// returns a *string when successful
func (m *AuthError) GetTypeEscaped() *string {
	return m.typeEscaped
}

// Serialize serializes information the current object
func (m *AuthError) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("detail", m.GetDetail())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("instance", m.GetInstance())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("name", m.GetName())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("status", m.GetStatus())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("title", m.GetTitle())
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
		err := writer.WriteAdditionalData(m.GetAdditionalData())
		if err != nil {
			return err
		}
	}
	return nil
}

// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *AuthError) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetDetail sets the detail property value. A human-readable explanation specific to this occurrence of the problem. Typically includes the exception class name and message.
func (m *AuthError) SetDetail(value *string) {
	m.detail = value
}

// SetInstance sets the instance property value. A URI reference that identifies the specific occurrence of the problem (optional).
func (m *AuthError) SetInstance(value *string) {
	m.instance = value
}

// SetName sets the name property value. The name of the error, typically the server exception class name.
func (m *AuthError) SetName(value *string) {
	m.name = value
}

// SetStatus sets the status property value. The HTTP status code (401 for Unauthorized, 403 for Forbidden).
func (m *AuthError) SetStatus(value *int32) {
	m.status = value
}

// SetTitle sets the title property value. A short, human-readable summary of the problem type.
func (m *AuthError) SetTitle(value *string) {
	m.title = value
}

// SetTypeEscaped sets the type property value. A URI reference [RFC3986] that identifies the problem type (optional).
func (m *AuthError) SetTypeEscaped(value *string) {
	m.typeEscaped = value
}

type AuthErrorable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetDetail() *string
	GetInstance() *string
	GetName() *string
	GetStatus() *int32
	GetTitle() *string
	GetTypeEscaped() *string
	SetDetail(value *string)
	SetInstance(value *string)
	SetName(value *string)
	SetStatus(value *int32)
	SetTitle(value *string)
	SetTypeEscaped(value *string)
}
