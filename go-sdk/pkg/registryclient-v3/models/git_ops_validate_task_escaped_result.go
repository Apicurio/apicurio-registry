package models
// Validation result: `success` (all checks passed) or `failure` (validation errors found). Only present when state is `completed`.
type GitOpsValidateTask_result int

const (
    SUCCESS_GITOPSVALIDATETASK_RESULT GitOpsValidateTask_result = iota
    FAILURE_GITOPSVALIDATETASK_RESULT
)

func (i GitOpsValidateTask_result) String() string {
    return []string{"success", "failure"}[i]
}
func ParseGitOpsValidateTask_result(v string) (any, error) {
    result := SUCCESS_GITOPSVALIDATETASK_RESULT
    switch v {
        case "success":
            result = SUCCESS_GITOPSVALIDATETASK_RESULT
        case "failure":
            result = FAILURE_GITOPSVALIDATETASK_RESULT
        default:
            return nil, nil
    }
    return &result, nil
}
func SerializeGitOpsValidateTask_result(values []GitOpsValidateTask_result) []string {
    result := make([]string, len(values))
    for i, v := range values {
        result[i] = v.String()
    }
    return result
}
func (i GitOpsValidateTask_result) isMultiValue() bool {
    return false
}
