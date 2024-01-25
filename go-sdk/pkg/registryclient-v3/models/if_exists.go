package models

import (
	"errors"
)

type IfExists int

const (
	FAIL_IFEXISTS IfExists = iota
	UPDATE_IFEXISTS
	RETURNESCAPED_IFEXISTS
	RETURN_OR_UPDATE_IFEXISTS
)

func (i IfExists) String() string {
	return []string{"FAIL", "UPDATE", "RETURN", "RETURN_OR_UPDATE"}[i]
}
func ParseIfExists(v string) (any, error) {
	result := FAIL_IFEXISTS
	switch v {
	case "FAIL":
		result = FAIL_IFEXISTS
	case "UPDATE":
		result = UPDATE_IFEXISTS
	case "RETURN":
		result = RETURNESCAPED_IFEXISTS
	case "RETURN_OR_UPDATE":
		result = RETURN_OR_UPDATE_IFEXISTS
	default:
		return 0, errors.New("Unknown IfExists value: " + v)
	}
	return &result, nil
}
func SerializeIfExists(values []IfExists) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i IfExists) isMultiValue() bool {
	return false
}
