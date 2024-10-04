package models

import (
	"errors"
)

type VersionSortBy int

const (
	VERSION_VERSIONSORTBY VersionSortBy = iota
	NAME_VERSIONSORTBY
	CREATEDON_VERSIONSORTBY
	MODIFIEDON_VERSIONSORTBY
	GLOBALID_VERSIONSORTBY
)

func (i VersionSortBy) String() string {
	return []string{"version", "name", "createdOn", "modifiedOn", "globalId"}[i]
}
func ParseVersionSortBy(v string) (any, error) {
	result := VERSION_VERSIONSORTBY
	switch v {
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
		return 0, errors.New("Unknown VersionSortBy value: " + v)
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
