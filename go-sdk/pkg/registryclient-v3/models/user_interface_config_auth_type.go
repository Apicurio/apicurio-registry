package models

import (
	"errors"
)

// This endpoint is used by the user interface to retrieve UI specific configurationin a JSON payload.  This allows the UI and the backend to be configured in the same place (the backend process/pod).  When the UI loads, it will make an API callto this endpoint to determine what UI features and options are configured.
type UserInterfaceConfigAuth_type int

const (
	NONE_USERINTERFACECONFIGAUTH_TYPE UserInterfaceConfigAuth_type = iota
	OIDC_USERINTERFACECONFIGAUTH_TYPE
)

func (i UserInterfaceConfigAuth_type) String() string {
	return []string{"none", "oidc"}[i]
}
func ParseUserInterfaceConfigAuth_type(v string) (any, error) {
	result := NONE_USERINTERFACECONFIGAUTH_TYPE
	switch v {
	case "none":
		result = NONE_USERINTERFACECONFIGAUTH_TYPE
	case "oidc":
		result = OIDC_USERINTERFACECONFIGAUTH_TYPE
	default:
		return 0, errors.New("Unknown UserInterfaceConfigAuth_type value: " + v)
	}
	return &result, nil
}
func SerializeUserInterfaceConfigAuth_type(values []UserInterfaceConfigAuth_type) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i UserInterfaceConfigAuth_type) isMultiValue() bool {
	return false
}
