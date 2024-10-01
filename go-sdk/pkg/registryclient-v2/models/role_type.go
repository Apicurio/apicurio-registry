package models

type RoleType int

const (
	READ_ONLY_ROLETYPE RoleType = iota
	DEVELOPER_ROLETYPE
	ADMIN_ROLETYPE
)

func (i RoleType) String() string {
	return []string{"READ_ONLY", "DEVELOPER", "ADMIN"}[i]
}
func ParseRoleType(v string) (any, error) {
	result := READ_ONLY_ROLETYPE
	switch v {
	case "READ_ONLY":
		result = READ_ONLY_ROLETYPE
	case "DEVELOPER":
		result = DEVELOPER_ROLETYPE
	case "ADMIN":
		result = ADMIN_ROLETYPE
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeRoleType(values []RoleType) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i RoleType) isMultiValue() bool {
	return false
}
