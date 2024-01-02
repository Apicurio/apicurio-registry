package models

import (
	"errors"
)

type SortBy int

const (
	NAME_SORTBY SortBy = iota
	CREATEDON_SORTBY
)

func (i SortBy) String() string {
	return []string{"name", "createdOn"}[i]
}
func ParseSortBy(v string) (any, error) {
	result := NAME_SORTBY
	switch v {
	case "name":
		result = NAME_SORTBY
	case "createdOn":
		result = CREATEDON_SORTBY
	default:
		return 0, errors.New("Unknown SortBy value: " + v)
	}
	return &result, nil
}
func SerializeSortBy(values []SortBy) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i SortBy) isMultiValue() bool {
	return false
}
