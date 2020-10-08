
package io.apicurio.registry.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Set;


/**
 * Root Type for Error
 * <p>
 * All error responses, whether `4xx` or `5xx` will include one of these as the response
 * body.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "message",
    "error_code"
})
public class Error {

    @JsonProperty("message")
    private String message;
    @JsonProperty("error_code")
    private Integer errorCode;
    @JsonProperty(value = "detail", required = false)
    private String detail;
    @JsonProperty(value = "incompatibleDiffs", required = false)
    private Set incompatibleDiffs;

    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    @JsonProperty("message")
    public void setMessage(String message) {
        this.message = message;
    }

    @JsonProperty("error_code")
    public Integer getErrorCode() {
        return errorCode;
    }

    @JsonProperty("error_code")
    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    @JsonProperty("detail")
    public String getDetail() {
        return detail;
    }

    @JsonProperty("detail")
    public void setDetail(String detail) {
        this.detail = detail;
    }

    @JsonProperty(value = "incompatibleDiffs")
    public Set getIncompatibleDiffs() { return incompatibleDiffs; }

    @JsonProperty(value = "incompatibleDiffs")
    public void setIncompatibleDiffs(Set incompatibleDiffs) { this.incompatibleDiffs = incompatibleDiffs; }
}
