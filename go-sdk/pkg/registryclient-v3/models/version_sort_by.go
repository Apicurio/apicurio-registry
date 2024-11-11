package models

type VersionSortBy int

const (
	GROUPID_VERSIONSORTBY VersionSortBy = iota
	ARTIFACTID_VERSIONSORTBY
	VERSION_VERSIONSORTBY
	NAME_VERSIONSORTBY
	CREATEDON_VERSIONSORTBY
	MODIFIEDON_VERSIONSORTBY
	GLOBALID_VERSIONSORTBY
)

func (i VersionSortBy) String() string {
	return []string{"groupId", "artifactId", "version", "name", "createdOn", "modifiedOn", "globalId"}[i]
}
func ParseVersionSortBy(v string) (any, error) {
	result := GROUPID_VERSIONSORTBY
	switch v {
	case "groupId":
		result = GROUPID_VERSIONSORTBY
	case "artifactId":
		result = ARTIFACTID_VERSIONSORTBY
	case "version":
		result = VERSION_VERSIONSORTBY
	case "name":
		result = NAME_VERSIONSORTBY
	case "createdOn":
		result = CREATEDON_VERSIONSORTBY
	case "modifiedOn":
		result = MODIFIEDON_VERSIONSORTBY
	case "globalId":
		result = GLOBALID_VERSIONSORTBY
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeVersionSortBy(values []VersionSortBy) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i VersionSortBy) isMultiValue() bool {
	return false
}
