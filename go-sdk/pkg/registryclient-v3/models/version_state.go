package models
// Describes the state of an artifact or artifact version.* ENABLED* DISABLED* DEPRECATED* DRAFT* SUNSET — Signals that a migration deadline has passed and the version will be removed. Requires transitioning through DEPRECATED first. Added in 3.3.0.
type VersionState int

const (
    ENABLED_VERSIONSTATE VersionState = iota
    DISABLED_VERSIONSTATE
    DEPRECATED_VERSIONSTATE
    DRAFT_VERSIONSTATE
    SUNSET_VERSIONSTATE
)

func (i VersionState) String() string {
    return []string{"ENABLED", "DISABLED", "DEPRECATED", "DRAFT", "SUNSET"}[i]
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
        case "SUNSET":
            result = SUNSET_VERSIONSTATE
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
