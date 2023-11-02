import { RuleViolationCause } from "@models/RuleViolationCause.model.ts";

export interface ApiError {
    causes: RuleViolationCause[] | null
    message: string
    error_code: number
    detail: string
    name: string
}
