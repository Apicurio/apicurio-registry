package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ContractRuleSearchResult a contract rule with its artifact coordinates, returned by tag search.
type ContractRuleSearchResult struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// The artifact ID containing the rule.
	artifactId *string
	// The global ID of the version (null for artifact-level rules).
	globalId *int64
	// The group ID of the artifact containing the rule.
	groupId *string
	// A single contract rule definition.
	rule ContractRuleable
	// The rule category (DOMAIN or MIGRATION).
	ruleCategory *ContractRuleSearchResult_ruleCategory
}

// NewContractRuleSearchResult instantiates a new ContractRuleSearchResult and sets the default values.
func NewContractRuleSearchResult() *ContractRuleSearchResult {
	m := &ContractRuleSearchResult{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateContractRuleSearchResultFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateContractRuleSearchResultFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewContractRuleSearchResult(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ContractRuleSearchResult) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactId gets the artifactId property value. The artifact ID containing the rule.
// returns a *string when successful
func (m *ContractRuleSearchResult) GetArtifactId() *string {
	return m.artifactId
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ContractRuleSearchResult) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
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
	res["globalId"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt64Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetGlobalId(val)
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
	res["rule"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateContractRuleFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetRule(val.(ContractRuleable))
		}
		return nil
	}
	res["ruleCategory"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetEnumValue(ParseContractRuleSearchResult_ruleCategory)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetRuleCategory(val.(*ContractRuleSearchResult_ruleCategory))
		}
		return nil
	}
	return res
}

// GetGlobalId gets the globalId property value. The global ID of the version (null for artifact-level rules).
// returns a *int64 when successful
func (m *ContractRuleSearchResult) GetGlobalId() *int64 {
	return m.globalId
}

// GetGroupId gets the groupId property value. The group ID of the artifact containing the rule.
// returns a *string when successful
func (m *ContractRuleSearchResult) GetGroupId() *string {
	return m.groupId
}

// GetRule gets the rule property value. A single contract rule definition.
// returns a ContractRuleable when successful
func (m *ContractRuleSearchResult) GetRule() ContractRuleable {
	return m.rule
}

// GetRuleCategory gets the ruleCategory property value. The rule category (DOMAIN or MIGRATION).
// returns a *ContractRuleSearchResult_ruleCategory when successful
func (m *ContractRuleSearchResult) GetRuleCategory() *ContractRuleSearchResult_ruleCategory {
	return m.ruleCategory
}

// Serialize serializes information the current object
func (m *ContractRuleSearchResult) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteStringValue("artifactId", m.GetArtifactId())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt64Value("globalId", m.GetGlobalId())
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
		err := writer.WriteObjectValue("rule", m.GetRule())
		if err != nil {
			return err
		}
	}
	if m.GetRuleCategory() != nil {
		cast := (*m.GetRuleCategory()).String()
		err := writer.WriteStringValue("ruleCategory", &cast)
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
func (m *ContractRuleSearchResult) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactId sets the artifactId property value. The artifact ID containing the rule.
func (m *ContractRuleSearchResult) SetArtifactId(value *string) {
	m.artifactId = value
}

// SetGlobalId sets the globalId property value. The global ID of the version (null for artifact-level rules).
func (m *ContractRuleSearchResult) SetGlobalId(value *int64) {
	m.globalId = value
}

// SetGroupId sets the groupId property value. The group ID of the artifact containing the rule.
func (m *ContractRuleSearchResult) SetGroupId(value *string) {
	m.groupId = value
}

// SetRule sets the rule property value. A single contract rule definition.
func (m *ContractRuleSearchResult) SetRule(value ContractRuleable) {
	m.rule = value
}

// SetRuleCategory sets the ruleCategory property value. The rule category (DOMAIN or MIGRATION).
func (m *ContractRuleSearchResult) SetRuleCategory(value *ContractRuleSearchResult_ruleCategory) {
	m.ruleCategory = value
}

type ContractRuleSearchResultable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactId() *string
	GetGlobalId() *int64
	GetGroupId() *string
	GetRule() ContractRuleable
	GetRuleCategory() *ContractRuleSearchResult_ruleCategory
	SetArtifactId(value *string)
	SetGlobalId(value *int64)
	SetGroupId(value *string)
	SetRule(value ContractRuleable)
	SetRuleCategory(value *ContractRuleSearchResult_ruleCategory)
}
