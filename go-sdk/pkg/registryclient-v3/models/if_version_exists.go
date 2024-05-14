package models
import (
    "errors"
)
// 
type IfVersionExists int

const (
    FAIL_IFVERSIONEXISTS IfVersionExists = iota
    CREATE_IFVERSIONEXISTS
    FIND_OR_CREATE_IFVERSIONEXISTS
)

func (i IfVersionExists) String() string {
    return []string{"FAIL", "CREATE", "FIND_OR_CREATE"}[i]
}
func ParseIfVersionExists(v string) (any, error) {
    result := FAIL_IFVERSIONEXISTS
    switch v {
        case "FAIL":
            result = FAIL_IFVERSIONEXISTS
        case "CREATE":
            result = CREATE_IFVERSIONEXISTS
        case "FIND_OR_CREATE":
            result = FIND_OR_CREATE_IFVERSIONEXISTS
        default:
            return 0, errors.New("Unknown IfVersionExists value: " + v)
    }
    return &result, nil
}
func SerializeIfVersionExists(values []IfVersionExists) []string {
    result := make([]string, len(values))
    for i, v := range values {
        result[i] = v.String()
    }
    return result
}
func (i IfVersionExists) isMultiValue() bool {
    return false
}
