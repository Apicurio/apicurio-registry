package models

type ArtifactSortBy int

const (
	ARTIFACTID_ARTIFACTSORTBY ArtifactSortBy = iota
	CREATEDON_ARTIFACTSORTBY
	MODIFIEDON_ARTIFACTSORTBY
	ARTIFACTTYPE_ARTIFACTSORTBY
	NAME_ARTIFACTSORTBY
)

func (i ArtifactSortBy) String() string {
	return []string{"artifactId", "createdOn", "modifiedOn", "artifactType", "name"}[i]
}
func ParseArtifactSortBy(v string) (any, error) {
	result := ARTIFACTID_ARTIFACTSORTBY
	switch v {
	case "artifactId":
		result = ARTIFACTID_ARTIFACTSORTBY
	case "createdOn":
		result = CREATEDON_ARTIFACTSORTBY
	case "modifiedOn":
		result = MODIFIEDON_ARTIFACTSORTBY
	case "artifactType":
		result = ARTIFACTTYPE_ARTIFACTSORTBY
	case "name":
		result = NAME_ARTIFACTSORTBY
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeArtifactSortBy(values []ArtifactSortBy) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ArtifactSortBy) isMultiValue() bool {
	return false
}
