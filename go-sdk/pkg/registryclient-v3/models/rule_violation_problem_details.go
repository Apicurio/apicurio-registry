package models

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// RuleViolationProblemDetails all error responses, whether `4xx` or `5xx` will include one of these as the responsebody.
type RuleViolationProblemDetails struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ApiError
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// List of rule violation causes.
	causes []RuleViolationCauseable
	// A human-readable explanation specific to this occurrence of the problem.
	detail *string
	// A URI reference that identifies the specific occurrence of the problem.
	instance *string
	// The name of the error (typically a server exception class name).
	name *string
	// The HTTP status code.
	status *int32
	// A short, human-readable summary of the problem type.
	title *string
	// A URI reference [RFC3986] that identifies the problem type.
	typeEscaped *string
}

// NewRuleViolationProblemDetails instantiates a new RuleViolationProblemDetails and sets the default values.
func NewRuleViolationProblemDetails() *RuleViolationProblemDetails {
	m := &RuleViolationProblemDetails{
		ApiError: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewApiError(),
	}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateRuleViolationProblemDetailsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateRuleViolationProblemDetailsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewRuleViolationProblemDetails(), nil
}

// Error the primary error message.
// returns a string when successful
func (m *RuleViolationProblemDetails) Error() string {
	return m.ApiError.Error()
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *RuleViolationProblemDetails) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetCauses gets the causes property value. List of rule violation causes.
// returns a []RuleViolationCauseable when successful
func (m *RuleViolationProblemDetails) GetCauses() []RuleViolationCauseable {
	return m.causes
}

// GetDetail gets the detail property value. A human-readable explanation specific to this occurrence of the problem.
// returns a *string when successful
func (m *RuleViolationProblemDetails) GetDetail() *string {
	return m.detail
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *RuleViolationProblemDetails) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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

// GetInstance gets the instance property value. A URI reference that identifies the specific occurrence of the problem.
// returns a *string when successful
func (m *RuleViolationProblemDetails) GetInstance() *string {
	return m.instance
}

// GetName gets the name property value. The name of the error (typically a server exception class name).
// returns a *string when successful
func (m *RuleViolationProblemDetails) GetName() *string {
	return m.name
}

// GetStatus gets the status property value. The HTTP status code.
// returns a *int32 when successful
func (m *RuleViolationProblemDetails) GetStatus() *int32 {
	return m.status
}

// GetTitle gets the title property value. A short, human-readable summary of the problem type.
// returns a *string when successful
func (m *RuleViolationProblemDetails) GetTitle() *string {
	return m.title
}

// GetTypeEscaped gets the type property value. A URI reference [RFC3986] that identifies the problem type.
// returns a *string when successful
func (m *RuleViolationProblemDetails) GetTypeEscaped() *string {
	return m.typeEscaped
}

// Serialize serializes information the current object
func (m *RuleViolationProblemDetails) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
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
func (m *RuleViolationProblemDetails) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetCauses sets the causes property value. List of rule violation causes.
func (m *RuleViolationProblemDetails) SetCauses(value []RuleViolationCauseable) {
	m.causes = value
}

// SetDetail sets the detail property value. A human-readable explanation specific to this occurrence of the problem.
func (m *RuleViolationProblemDetails) SetDetail(value *string) {
	m.detail = value
}

// SetInstance sets the instance property value. A URI reference that identifies the specific occurrence of the problem.
func (m *RuleViolationProblemDetails) SetInstance(value *string) {
	m.instance = value
}

// SetName sets the name property value. The name of the error (typically a server exception class name).
func (m *RuleViolationProblemDetails) SetName(value *string) {
	m.name = value
}

// SetStatus sets the status property value. The HTTP status code.
func (m *RuleViolationProblemDetails) SetStatus(value *int32) {
	m.status = value
}

// SetTitle sets the title property value. A short, human-readable summary of the problem type.
func (m *RuleViolationProblemDetails) SetTitle(value *string) {
	m.title = value
}

// SetTypeEscaped sets the type property value. A URI reference [RFC3986] that identifies the problem type.
func (m *RuleViolationProblemDetails) SetTypeEscaped(value *string) {
	m.typeEscaped = value
}

type RuleViolationProblemDetailsable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetCauses() []RuleViolationCauseable
	GetDetail() *string
	GetInstance() *string
	GetName() *string
	GetStatus() *int32
	GetTitle() *string
	GetTypeEscaped() *string
	SetCauses(value []RuleViolationCauseable)
	SetDetail(value *string)
	SetInstance(value *string)
	SetName(value *string)
	SetStatus(value *int32)
	SetTitle(value *string)
	SetTypeEscaped(value *string)
}
