package models

import (
	"errors"
)

type RuleType int

const (
	VALIDITY_RULETYPE RuleType = iota
	COMPATIBILITY_RULETYPE
	INTEGRITY_RULETYPE
)

func (i RuleType) String() string {
	return []string{"VALIDITY", "COMPATIBILITY", "INTEGRITY"}[i]
}
func ParseRuleType(v string) (any, error) {
	result := VALIDITY_RULETYPE
	switch v {
	case "VALIDITY":
		result = VALIDITY_RULETYPE
	case "COMPATIBILITY":
		result = COMPATIBILITY_RULETYPE
	case "INTEGRITY":
		result = INTEGRITY_RULETYPE
	default:
		return 0, errors.New("Unknown RuleType value: " + v)
	}
	return &result, nil
}
func SerializeRuleType(values []RuleType) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i RuleType) isMultiValue() bool {
	return false
}
