#### Parameters

For the POST create account end point the JSON payload consists of a “Header” and “Body”.
The PK is the NINO, which resides in the Body.
MDTP uses the Header and NS&I are passed the Body.

#### Errors

For the POST create account end point 201 is the expected response and 409 is not really an error as it indicates that
an account has already been created with the given details. Other 4xx and 5xx ranges indicates failure.

In case of auth related errors which are triggered by API Platform (401 or 403), the error json schema contains two fields and looks like as below.

<pre>
{
  "$schema": "http://json-schema.org/draft-04/schema",
  "description": "JSON schema for Help to Save Create Account error response",
  "type": "object",
  "properties": {
    "code": {
      "type": "string"
    },
    "message": {
      "type": "string"
    }
  }
}
</pre>

Example

<pre>
{
  "code": "MISSING_BEARER_TOKEN",
  "message": "Bad or expired token"
}
</pre>


In case of errors triggered by Help to Save service (400, 404, 5xx), the error json schema contains three fields and looks like as below.

<pre>
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
</pre>

Example

<pre>
{ "error":
  {
    "errorMessageId": "ZYRA0712",
    "errorMessage": "The first character cannot be a special character",
    "errorDetail": "Field: surname"
  }
}
</pre>

<pre>
{
  "error":
  {
    "errorMessageId": "ZYRA0713",
    "errorMessage": "Server received an invalid response from the upstream server while trying to fulfil the request",
    "errorDetail": "Bad gateway"
  }
}
</pre>


#### Data Transformations

##### Data Transformations by HMRC:

For the following fields;

1. forename
2. surname
3. address1
4. address2
5. address3
6. address4
7. address5
8. phoneNumber

control characters TAB \t, NEWLINE \n, CR \r are replaced with single space, consecutive spaces greater than 1
are replaced with a single space and leading & trailing spaces are removed.

##### Data Transformations by NSI:

For the following fields;

1. forename
2. surname
3. address1
4. address2
5. address3
6. address4
7. address5
8. phoneNumber

accented characters are replaced with unaccented equivalent characters. For forename and surname, characters that
are not one of a-z, A-Z, &, space, fullstop, comma, hyphen are removed, the first character if it is one of &,
fullstop, comma, hyphen is removed, consecutive permissible special characters (&, fullstop, comma, hyphen) are
removed. For surname the last character if it is one of &, fullstop, comma, hyphen is removed.

#### Diacritics and Legal Considerations

This section highlights API internal handling of diacritic (accented) chars when the POST create account end point is triggered.
The following Diacritic handling rules only target the forename & surname.  Address & Telephone fields are accepted as-is.

In this API diacritic characters will be converted to the undecorated underlying chracter. For example, a manual conversion of
‘Höben’ normally converts to ‘Hoeben’, but the conversion within the API will be to ‘o’ not ‘oe’, i.e ‘Hoben’.  As such, it could
 be argued that the legal name or address of that person has been changed.  The view by HMRC/MDTP & NS&I is that this name change is not legally
 significant. You may want to consult your legal representative to ensure they share the same view.

Only diacritic chars up to and including Unicode 3.0 at http://www.unicode.org/Public/3.0-Update/UnicodeData-3.0.0.txt
will have a 1:1 char conversion attempt.  If say a Scandinavian char ø is presented, the char is removed. E.g. surname
"O'Connor-Jørgensen" converts to "O'Connor-Jrgensen".

#### Digitally Excluded Callers

In the json payloads sent from digitally excluded callers to the POST create account end point, the communicationPreference
field must be set to "00" and registrationChannel must be set to "callCentre".