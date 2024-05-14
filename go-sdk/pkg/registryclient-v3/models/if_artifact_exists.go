package models

import (
	"errors"
)

type IfArtifactExists int

const (
	FAIL_IFARTIFACTEXISTS IfArtifactExists = iota
	CREATE_VERSION_IFARTIFACTEXISTS
	FIND_OR_CREATE_VERSION_IFARTIFACTEXISTS
)

func (i IfArtifactExists) String() string {
	return []string{"FAIL", "CREATE_VERSION", "FIND_OR_CREATE_VERSION"}[i]
}
func ParseIfArtifactExists(v string) (any, error) {
	result := FAIL_IFARTIFACTEXISTS
	switch v {
	case "FAIL":
		result = FAIL_IFARTIFACTEXISTS
	case "CREATE_VERSION":
		result = CREATE_VERSION_IFARTIFACTEXISTS
	case "FIND_OR_CREATE_VERSION":
		result = FIND_OR_CREATE_VERSION_IFARTIFACTEXISTS
	default:
		return 0, errors.New("Unknown IfArtifactExists value: " + v)
	}
	return &result, nil
}
func SerializeIfArtifactExists(values []IfArtifactExists) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i IfArtifactExists) isMultiValue() bool {
	return false
}
