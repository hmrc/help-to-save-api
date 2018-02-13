201 is the expected response and 409 is not really an error as it indicates that an account has already been created with
the given details. Other 4xx and 5xx ranges indicates failure.

The error response will include a JSON body which follows this schema:

````
{
  "$schema": "http://json-schema.org/draft-04/schema",
  "description": "JSON schema for error response",
  "type":"object",
  "properties":{
    "error": {
      "type":"object",
      "properties":{
        "errorMessageId": {
          "type":"string"
        },
        "errorMessage": {
          "type":"string"
        },
        "errorDetail": {
          "type":"string"
        }

      }
    }
  }
}
````

