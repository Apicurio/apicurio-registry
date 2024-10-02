package models

type GroupSortBy int

const (
	GROUPID_GROUPSORTBY GroupSortBy = iota
	CREATEDON_GROUPSORTBY
)

func (i GroupSortBy) String() string {
	return []string{"groupId", "createdOn"}[i]
}
func ParseGroupSortBy(v string) (any, error) {
	result := GROUPID_GROUPSORTBY
	switch v {
	case "groupId":
		result = GROUPID_GROUPSORTBY
	case "createdOn":
		result = CREATEDON_GROUPSORTBY
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeGroupSortBy(values []GroupSortBy) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i GroupSortBy) isMultiValue() bool {
	return false
}
