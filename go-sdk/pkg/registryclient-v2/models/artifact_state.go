package models

// Describes the state of an artifact or artifact version.  The following statesare possible:* ENABLED* DISABLED* DEPRECATED
type ArtifactState int

const (
	ENABLED_ARTIFACTSTATE ArtifactState = iota
	DISABLED_ARTIFACTSTATE
	DEPRECATED_ARTIFACTSTATE
)

func (i ArtifactState) String() string {
	return []string{"ENABLED", "DISABLED", "DEPRECATED"}[i]
}
func ParseArtifactState(v string) (any, error) {
	result := ENABLED_ARTIFACTSTATE
	switch v {
	case "ENABLED":
		result = ENABLED_ARTIFACTSTATE
	case "DISABLED":
		result = DISABLED_ARTIFACTSTATE
	case "DEPRECATED":
		result = DEPRECATED_ARTIFACTSTATE
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeArtifactState(values []ArtifactState) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ArtifactState) isMultiValue() bool {
	return false
}
