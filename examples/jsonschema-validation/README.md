# jsonschema-validation

This is an Apicurio Registry example. For more information about Apicurio Registry see https://www.apicur.io/registry/

## Instructions


This example demonstrates how to use Apicurio Registry Schema Validation library for JSON and JSON Schema.

The following aspects are demonstrated:

<ol>
<li>Register the JSON Schema in the registry</li>
<li>Configuring a JsonValidator that will use Apicurio Registry to fetch and cache the schema to use for validation</li>
<li>Successfully validate Java objects using static configuration to always use the same schema for validation</li>
<li>Successfully validate Java objects using dynamic configuration to dynamically choose the schema to use for validation</li>
</ol>

Pre-requisites:

<ul>
<li>Apicurio Registry must be running on localhost:8080</li>
</ul>

@author eric.wittmann@gmail.com

