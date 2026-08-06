import { describe, expect, it } from "vitest";
import { unwrapErrorData } from "./rest.utils";

describe("unwrapErrorData", () => {
    it("should correctly unwrap and spread JSON object response data", () => {
        const error = {
            message: "Request failed with status code 409",
            response: {
                status: 409,
                data: {
                    message: "Artifact already exists",
                    errorCode: 409
                }
            }
        };
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "Artifact already exists",
            errorCode: 409,
            status: 409
        });
    });

    it("should correctly handle and wrap plain-text/HTML string response data", () => {
        const error = {
            message: "Request failed with status code 504",
            response: {
                status: 504,
                data: "504 Gateway Time-out"
            }
        };
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "504 Gateway Time-out",
            status: 504
        });
    });

    it("should handle response with null/undefined data by falling back to error message", () => {
        const error = {
            message: "Request failed with status code 500",
            response: {
                status: 500,
                data: null
            }
        };
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "Request failed with status code 500",
            status: 500
        });
    });

    it("should handle errors without response object by returning status 500", () => {
        const error = new Error("Connection failed");
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "Connection failed",
            status: 500
        });
    });

    it("should handle unknown/falsy errors gracefully", () => {
        const result = unwrapErrorData(undefined);
        expect(result).toEqual({
            message: "Unknown error",
            status: 500
        });
    });

    it("should handle array response data by converting it to string message", () => {
        const error = {
            message: "Request failed with status code 400",
            response: {
                status: 400,
                data: ["Validation error 1", "Validation error 2"]
            }
        };
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "Validation error 1,Validation error 2",
            status: 400
        });
    });

    it("should handle non-string scalar response data by converting it to string message", () => {
        const error = {
            message: "Request failed with status code 500",
            response: {
                status: 500,
                data: 99999
            }
        };
        const result = unwrapErrorData(error);
        expect(result).toEqual({
            message: "99999",
            status: 500
        });
    });
});
