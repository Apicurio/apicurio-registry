package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ContractRule a single contract rule definition.
type ContractRule struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Whether the rule is disabled.
	disabled *bool
	// The rule expression.
	expr *string
	// The rule kind.
	kind *ContractRule_kind
	// When the rule is applied.
	mode *ContractRule_mode
	// The rule name.
	name *string
	// Action on rule failure.
	onFailure *ContractRule_onFailure
	// Action on rule success.
	onSuccess *ContractRule_onSuccess
	// Rule parameters.
	params ContractRule_paramsable
	// Tags for categorizing the rule.
	tags []string
	// Rule executor type (CEL, CEL_FIELD, ENCRYPT, etc.).
	typeEscaped *string
}

// NewContractRule instantiates a new ContractRule and sets the default values.
func NewContractRule() *ContractRule {
	m := &ContractRule{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateContractRuleFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateContractRuleFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewContractRule(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ContractRule) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetDisabled gets the disabled property value. Whether the rule is disabled.
// returns a *bool when successful
func (m *ContractRule) GetDisabled() *bool {
	return m.disabled
}

// GetExpr gets the expr property value. The rule expression.
// returns a *string when successful
func (m *ContractRule) GetExpr() *string {
	return m.expr
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ContractRule) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["disabled"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetBoolValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetDisabled(val)
		}
		return nil
	}
	res["expr"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetExpr(val)
		}
		return nil
	}
	res["kind"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseContractRule_kind)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetKind(val.(*ContractRule_kind))
		}
		return nil
	}
	res["mode"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseContractRule_mode)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetMode(val.(*ContractRule_mode))
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
	res["onFailure"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseContractRule_onFailure)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetOnFailure(val.(*ContractRule_onFailure))
		}
		return nil
	}
	res["onSuccess"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseContractRule_onSuccess)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetOnSuccess(val.(*ContractRule_onSuccess))
		}
		return nil
	}
	res["params"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateContractRule_paramsFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetParams(val.(ContractRule_paramsable))
		}
		return nil
	}
	res["tags"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfPrimitiveValues("string")
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]string, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = *(v.(*string))
				}
			}
			m.SetTags(res)
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

// GetKind gets the kind property value. The rule kind.
// returns a *ContractRule_kind when successful
func (m *ContractRule) GetKind() *ContractRule_kind {
	return m.kind
}

// GetMode gets the mode property value. When the rule is applied.
// returns a *ContractRule_mode when successful
func (m *ContractRule) GetMode() *ContractRule_mode {
	return m.mode
}

// GetName gets the name property value. The rule name.
// returns a *string when successful
func (m *ContractRule) GetName() *string {
	return m.name
}

// GetOnFailure gets the onFailure property value. Action on rule failure.
// returns a *ContractRule_onFailure when successful
func (m *ContractRule) GetOnFailure() *ContractRule_onFailure {
	return m.onFailure
}

// GetOnSuccess gets the onSuccess property value. Action on rule success.
// returns a *ContractRule_onSuccess when successful
func (m *ContractRule) GetOnSuccess() *ContractRule_onSuccess {
	return m.onSuccess
}

// GetParams gets the params property value. Rule parameters.
// returns a ContractRule_paramsable when successful
func (m *ContractRule) GetParams() ContractRule_paramsable {
	return m.params
}

// GetTags gets the tags property value. Tags for categorizing the rule.
// returns a []string when successful
func (m *ContractRule) GetTags() []string {
	return m.tags
}

// GetTypeEscaped gets the type property value. Rule executor type (CEL, CEL_FIELD, ENCRYPT, etc.).
// returns a *string when successful
func (m *ContractRule) GetTypeEscaped() *string {
	return m.typeEscaped
}

// Serialize serializes information the current object
func (m *ContractRule) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteBoolValue("disabled", m.GetDisabled())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("expr", m.GetExpr())
		if err != nil {
			return err
		}
	}
	if m.GetKind() != nil {
		cast := (*m.GetKind()).String()
		err := writer.WriteStringValue("kind", &cast)
		if err != nil {
			return err
		}
	}
	if m.GetMode() != nil {
		cast := (*m.GetMode()).String()
		err := writer.WriteStringValue("mode", &cast)
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
	if m.GetOnFailure() != nil {
		cast := (*m.GetOnFailure()).String()
		err := writer.WriteStringValue("onFailure", &cast)
		if err != nil {
			return err
		}
	}
	if m.GetOnSuccess() != nil {
		cast := (*m.GetOnSuccess()).String()
		err := writer.WriteStringValue("onSuccess", &cast)
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("params", m.GetParams())
		if err != nil {
			return err
		}
	}
	if m.GetTags() != nil {
		err := writer.WriteCollectionOfStringValues("tags", m.GetTags())
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
func (m *ContractRule) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetDisabled sets the disabled property value. Whether the rule is disabled.
func (m *ContractRule) SetDisabled(value *bool) {
	m.disabled = value
}

// SetExpr sets the expr property value. The rule expression.
func (m *ContractRule) SetExpr(value *string) {
	m.expr = value
}

// SetKind sets the kind property value. The rule kind.
func (m *ContractRule) SetKind(value *ContractRule_kind) {
	m.kind = value
}

// SetMode sets the mode property value. When the rule is applied.
func (m *ContractRule) SetMode(value *ContractRule_mode) {
	m.mode = value
}

// SetName sets the name property value. The rule name.
func (m *ContractRule) SetName(value *string) {
	m.name = value
}

// SetOnFailure sets the onFailure property value. Action on rule failure.
func (m *ContractRule) SetOnFailure(value *ContractRule_onFailure) {
	m.onFailure = value
}

// SetOnSuccess sets the onSuccess property value. Action on rule success.
func (m *ContractRule) SetOnSuccess(value *ContractRule_onSuccess) {
	m.onSuccess = value
}

// SetParams sets the params property value. Rule parameters.
func (m *ContractRule) SetParams(value ContractRule_paramsable) {
	m.params = value
}

// SetTags sets the tags property value. Tags for categorizing the rule.
func (m *ContractRule) SetTags(value []string) {
	m.tags = value
}

// SetTypeEscaped sets the type property value. Rule executor type (CEL, CEL_FIELD, ENCRYPT, etc.).
func (m *ContractRule) SetTypeEscaped(value *string) {
	m.typeEscaped = value
}

type ContractRuleable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetDisabled() *bool
	GetExpr() *string
	GetKind() *ContractRule_kind
	GetMode() *ContractRule_mode
	GetName() *string
	GetOnFailure() *ContractRule_onFailure
	GetOnSuccess() *ContractRule_onSuccess
	GetParams() ContractRule_paramsable
	GetTags() []string
	GetTypeEscaped() *string
	SetDisabled(value *bool)
	SetExpr(value *string)
	SetKind(value *ContractRule_kind)
	SetMode(value *ContractRule_mode)
	SetName(value *string)
	SetOnFailure(value *ContractRule_onFailure)
	SetOnSuccess(value *ContractRule_onSuccess)
	SetParams(value ContractRule_paramsable)
	SetTags(value []string)
	SetTypeEscaped(value *string)
}
