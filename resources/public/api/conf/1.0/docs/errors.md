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

Here is a list of possible HTTP response error codes:

400 - Bad Request,
401 - Unauthorized,
403 - Forbidden,
404 - Not Found,
405 - Method Not Allowed,
409 - Conflict,
500 - Internal Server Error,
502 - Bad Gateway,
503 - Service Unavailable