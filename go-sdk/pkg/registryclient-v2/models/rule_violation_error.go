package models

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// RuleViolationError all error responses, whether `4xx` or `5xx` will include one of these as the responsebody.
type RuleViolationError struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ApiError
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// List of rule violation causes.
	causes []RuleViolationCauseable
	// Full details about the error.  This might contain a server stack trace, for example.
	detail *string
	// The server-side error code.
	error_code *int32
	// The short error message.
	message *string
	// The error name - typically the classname of the exception thrown by the server.
	name *string
}

// NewRuleViolationError instantiates a new RuleViolationError and sets the default values.
func NewRuleViolationError() *RuleViolationError {
	m := &RuleViolationError{
		ApiError: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewApiError(),
	}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateRuleViolationErrorFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateRuleViolationErrorFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewRuleViolationError(), nil
}

// Error the primary error message.
// returns a string when successful
func (m *RuleViolationError) Error() string {
	return m.ApiError.Error()
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *RuleViolationError) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetCauses gets the causes property value. List of rule violation causes.
// returns a []RuleViolationCauseable when successful
func (m *RuleViolationError) GetCauses() []RuleViolationCauseable {
	return m.causes
}

// GetDetail gets the detail property value. Full details about the error.  This might contain a server stack trace, for example.
// returns a *string when successful
func (m *RuleViolationError) GetDetail() *string {
	return m.detail
}

// GetErrorCode gets the error_code property value. The server-side error code.
// returns a *int32 when successful
func (m *RuleViolationError) GetErrorCode() *int32 {
	return m.error_code
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *RuleViolationError) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["causes"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateRuleViolationCauseFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]RuleViolationCauseable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(RuleViolationCauseable)
				}
			}
			m.SetCauses(res)
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
// returns a *string when successful
func (m *RuleViolationError) GetMessage() *string {
	return m.message
}

// GetName gets the name property value. The error name - typically the classname of the exception thrown by the server.
// returns a *string when successful
func (m *RuleViolationError) GetName() *string {
	return m.name
}

// Serialize serializes information the current object
func (m *RuleViolationError) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetCauses() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetCauses()))
		for i, v := range m.GetCauses() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("causes", cast)
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
func (m *RuleViolationError) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetCauses sets the causes property value. List of rule violation causes.
func (m *RuleViolationError) SetCauses(value []RuleViolationCauseable) {
	m.causes = value
}

// SetDetail sets the detail property value. Full details about the error.  This might contain a server stack trace, for example.
func (m *RuleViolationError) SetDetail(value *string) {
	m.detail = value
}

// SetErrorCode sets the error_code property value. The server-side error code.
func (m *RuleViolationError) SetErrorCode(value *int32) {
	m.error_code = value
}

// SetMessage sets the message property value. The short error message.
func (m *RuleViolationError) SetMessage(value *string) {
	m.message = value
}

// SetName sets the name property value. The error name - typically the classname of the exception thrown by the server.
func (m *RuleViolationError) SetName(value *string) {
	m.name = value
}

type RuleViolationErrorable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetCauses() []RuleViolationCauseable
	GetDetail() *string
	GetErrorCode() *int32
	GetMessage() *string
	GetName() *string
	SetCauses(value []RuleViolationCauseable)
	SetDetail(value *string)
	SetErrorCode(value *int32)
	SetMessage(value *string)
	SetName(value *string)
}
