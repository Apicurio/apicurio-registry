package models

// Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
type VersionState int

const (
	ENABLED_VERSIONSTATE VersionState = iota
	DISABLED_VERSIONSTATE
	DEPRECATED_VERSIONSTATE
	DRAFT_VERSIONSTATE
)

func (i VersionState) String() string {
	return []string{"ENABLED", "DISABLED", "DEPRECATED", "DRAFT"}[i]
}
func ParseVersionState(v string) (any, error) {
	result := ENABLED_VERSIONSTATE
	switch v {
	case "ENABLED":
		result = ENABLED_VERSIONSTATE
	case "DISABLED":
		result = DISABLED_VERSIONSTATE
	case "DEPRECATED":
		result = DEPRECATED_VERSIONSTATE
	case "DRAFT":
		result = DRAFT_VERSIONSTATE
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeVersionState(values []VersionState) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i VersionState) isMultiValue() bool {
	return false
}
