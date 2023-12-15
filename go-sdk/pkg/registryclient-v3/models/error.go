package models

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// Error all error responses, whether `4xx` or `5xx` will include one of these as the responsebody.
type Error struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ApiError
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Full details about the error.  This might contain a server stack trace, for example.
	detail *string
	// The server-side error code.
	error_code *int32
	// The short error message.
	message *string
	// The error name - typically the classname of the exception thrown by the server.
	name *string
}

// NewError instantiates a new Error and sets the default values.
func NewError() *Error {
	m := &Error{
		ApiError: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewApiError(),
	}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateErrorFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
func CreateErrorFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewError(), nil
}

// Error the primary error message.
func (m *Error) Error() string {
	return m.ApiError.Error()
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *Error) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetDetail gets the detail property value. Full details about the error.  This might contain a server stack trace, for example.
func (m *Error) GetDetail() *string {
	return m.detail
}

// GetErrorCode gets the error_code property value. The server-side error code.
func (m *Error) GetErrorCode() *int32 {
	return m.error_code
}

// GetFieldDeserializers the deserialization information for the current model
func (m *Error) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["error_code"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetErrorCode(val)
		}
		return nil
	}
	res["message"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMessage(val)
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
	return res
}

// GetMessage gets the message property value. The short error message.
func (m *Error) GetMessage() *string {
	return m.message
}

// GetName gets the name property value. The error name - typically the classname of the exception thrown by the server.
func (m *Error) GetName() *string {
	return m.name
}

// Serialize serializes information the current object
func (m *Error) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("detail", m.GetDetail())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("error_code", m.GetErrorCode())
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
		err := writer.WriteStringValue("name", m.GetName())
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
func (m *Error) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetDetail sets the detail property value. Full details about the error.  This might contain a server stack trace, for example.
func (m *Error) SetDetail(value *string) {
	m.detail = value
}

// SetErrorCode sets the error_code property value. The server-side error code.
func (m *Error) SetErrorCode(value *int32) {
	m.error_code = value
}

// SetMessage sets the message property value. The short error message.
func (m *Error) SetMessage(value *string) {
	m.message = value
}

// SetName sets the name property value. The error name - typically the classname of the exception thrown by the server.
func (m *Error) SetName(value *string) {
	m.name = value
}

// Errorable
type Errorable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetDetail() *string
	GetErrorCode() *int32
	GetMessage() *string
	GetName() *string
	SetDetail(value *string)
	SetErrorCode(value *int32)
	SetMessage(value *string)
	SetName(value *string)
}
