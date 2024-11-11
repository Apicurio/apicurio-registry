package models

// How to handle references when retrieving content.  References can either beleft unchanged (`PRESERVE`), re-written so they are valid in the context of theregistry (`REWRITE`), or fully dereferenced such that all externally referencedcontent is internalized (`DEREFERENCE`).
type HandleReferencesType int

const (
	PRESERVE_HANDLEREFERENCESTYPE HandleReferencesType = iota
	DEREFERENCE_HANDLEREFERENCESTYPE
	REWRITE_HANDLEREFERENCESTYPE
)

func (i HandleReferencesType) String() string {
	return []string{"PRESERVE", "DEREFERENCE", "REWRITE"}[i]
}
func ParseHandleReferencesType(v string) (any, error) {
	result := PRESERVE_HANDLEREFERENCESTYPE
	switch v {
	case "PRESERVE":
		result = PRESERVE_HANDLEREFERENCESTYPE
	case "DEREFERENCE":
		result = DEREFERENCE_HANDLEREFERENCESTYPE
	case "REWRITE":
		result = REWRITE_HANDLEREFERENCESTYPE
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeHandleReferencesType(values []HandleReferencesType) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i HandleReferencesType) isMultiValue() bool {
	return false
}
