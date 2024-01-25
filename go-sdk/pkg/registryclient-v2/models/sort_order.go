package models
import (
    "errors"
)
// 
type SortOrder int

const (
    ASC_SORTORDER SortOrder = iota
    DESC_SORTORDER
)

func (i SortOrder) String() string {
    return []string{"asc", "desc"}[i]
}
func ParseSortOrder(v string) (any, error) {
    result := ASC_SORTORDER
    switch v {
        case "asc":
            result = ASC_SORTORDER
        case "desc":
            result = DESC_SORTORDER
        default:
            return 0, errors.New("Unknown SortOrder value: " + v)
    }
    return &result, nil
}
func SerializeSortOrder(values []SortOrder) []string {
    result := make([]string, len(values))
    for i, v := range values {
        result[i] = v.String()
    }
    return result
}
func (i SortOrder) isMultiValue() bool {
    return false
}
