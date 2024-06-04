# simple-validation

This is an Apicurio Registry example. For more information about Apicurio Registry see https://www.apicur.io/registry/

## Instructions


This example demonstrates how to integrate with Apicurio Registry when performing client-side validation of
JSON messages. This example imagines a generic scenario where JSON messages are sent/published to a custom
messaging system for later consumption. It assumes that the JSON Schema used for validation must already be
registered. The following aspects are demonstrated:
<ol>
<li>Fetch the JSON Schema from the registry</li>
<li>Generate and Validate JSON messages</li>
<li>Send validated messages to a messaging system</li>
</ol>
Pre-requisites:
<ul>
<li>Apicurio Registry must be running on localhost:8080</li>
<li>JSON schema must be registered at coordinates default/SimpleValidationExample</li>
</ul>

@author eric.wittmann@gmail.com

