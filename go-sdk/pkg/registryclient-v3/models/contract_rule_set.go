package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// ContractRuleSet a set of contract rules, divided into domain and migration categories.
type ContractRuleSet struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Rules for domain validation.
	domainRules []ContractRuleable
	// Rules for version migration.
	migrationRules []ContractRuleable
}

// NewContractRuleSet instantiates a new ContractRuleSet and sets the default values.
func NewContractRuleSet() *ContractRuleSet {
	m := &ContractRuleSet{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateContractRuleSetFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateContractRuleSetFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewContractRuleSet(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *ContractRuleSet) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetDomainRules gets the domainRules property value. Rules for domain validation.
// returns a []ContractRuleable when successful
func (m *ContractRuleSet) GetDomainRules() []ContractRuleable {
	return m.domainRules
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *ContractRuleSet) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["domainRules"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateContractRuleFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]ContractRuleable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(ContractRuleable)
				}
			}
			m.SetDomainRules(res)
		}
		return nil
	}
	res["migrationRules"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfObjectValues(CreateContractRuleFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]ContractRuleable, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = v.(ContractRuleable)
				}
			}
			m.SetMigrationRules(res)
		}
		return nil
	}
	return res
}

// GetMigrationRules gets the migrationRules property value. Rules for version migration.
// returns a []ContractRuleable when successful
func (m *ContractRuleSet) GetMigrationRules() []ContractRuleable {
	return m.migrationRules
}

// Serialize serializes information the current object
func (m *ContractRuleSet) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	if m.GetDomainRules() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetDomainRules()))
		for i, v := range m.GetDomainRules() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("domainRules", cast)
		if err != nil {
			return err
		}
	}
	if m.GetMigrationRules() != nil {
		cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetMigrationRules()))
		for i, v := range m.GetMigrationRules() {
			if v != nil {
				cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
			}
		}
		err := writer.WriteCollectionOfObjectValues("migrationRules", cast)
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
func (m *ContractRuleSet) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetDomainRules sets the domainRules property value. Rules for domain validation.
func (m *ContractRuleSet) SetDomainRules(value []ContractRuleable) {
	m.domainRules = value
}

// SetMigrationRules sets the migrationRules property value. Rules for version migration.
func (m *ContractRuleSet) SetMigrationRules(value []ContractRuleable) {
	m.migrationRules = value
}

type ContractRuleSetable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetDomainRules() []ContractRuleable
	GetMigrationRules() []ContractRuleable
	SetDomainRules(value []ContractRuleable)
	SetMigrationRules(value []ContractRuleable)
}
