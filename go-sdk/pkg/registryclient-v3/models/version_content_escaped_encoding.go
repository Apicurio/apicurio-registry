package models

// Optional encoding for the content property. When set to 'base64', the content value will be base64-decoded by the server before processing.
type VersionContent_encoding int

const (
	BASE64_VERSIONCONTENT_ENCODING VersionContent_encoding = iota
)

func (i VersionContent_encoding) String() string {
	return []string{"base64"}[i]
}
func ParseVersionContent_encoding(v string) (any, error) {
	result := BASE64_VERSIONCONTENT_ENCODING
	switch v {
	case "base64":
		result = BASE64_VERSIONCONTENT_ENCODING
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeVersionContent_encoding(values []VersionContent_encoding) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i VersionContent_encoding) isMultiValue() bool {
	return false
}
