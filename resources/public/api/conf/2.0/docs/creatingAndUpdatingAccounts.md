A `POST` to the create/update account endpoint can be used to both create and update an account. The
distinction between the two is made by checking MDTP checking whether or not an account already exists.
If an account already exists, the request is treated as an update account request. Otherwise, the request
is treated as a create account request. Each account is identified using the NINO as the primary key.

#### Parameters

For the `POST` account end point, the JSON payload consists of a `header` and `body`. The primary key used to
identify accounts is the NINO, which resides in the `body`. MDTP uses the `header` and NS&I are passed the `body`. 

The NINO is an optional parameter in the request body. If no NINO is supplied then a NINO will be derived
in the MDTP backend systems. If a NINO does not exist then a `403 (Forbidden)` will be returned by the API.

In a create account request, if some mandatory fields are not supplied, an attempt to retrieve the information
on MDTP will be made.  If such retrieval is not possible a `500 (Internal Server Error)` will be returned by
the API. For create account the mandatory fields are:
- `forename`
- `surname`
- `dateOfBirth`
- `nino`
- `address1`, `address2` and `postcode`
- `registrationChannel`

The exception to the above is `registrationChannel` - for all create account requests this has to be supplied
in the request body.

In an update account request, only requests will be accepted where updates to the fields given are supported.
Currently, there are no update requests supported.
If update requests are received where other fields are populated a `400 (Bad Request)` will be given and no attempt
to update any details will be made.


**For create and update account requests using privileged access, the NINO is a mandatory field.**


### Bank Details
When bank details are supplied, they will be checked against an internal HMRC service to check their validity. The
checks done by Help to Save are a modulus check on the bank account and sort code and also a check to see if the sort
code actually exists. If these checks fail a `400 (Bad Request)`  response will be returned.



#### Errors

When creating accounts, `201 (Created)` is the expected response and `409 (Conflict)` is used to indicate that
an account has already been created with the given details. Other `4xx` and `5xx` HTTP status codes indicates
failure.

For updating accounts, `200 (OK)` is the expected repsonse. All other HTTP responses should be treated as errors.


#### Data Transformations

##### Data Transformations by HMRC:

For the following fields;

- `forename`
- `surname`
- `address1`
- `address2`
- `address3`
- `address4`
- `address5`
- `phoneNumber`

control characters `TAB \t`, `NEWLINE \n`, `CR \r` are replaced with single space, consecutive spaces greater than 1
are replaced with a single space and leading & trailing spaces are removed.

##### Data Transformations by NSI:

For the following fields;

- `forename`
- `surname`
- `address1`
- `address2`
- `address3`
- `address4`
- `address5`
- `phoneNumber`

accented characters are replaced with unaccented equivalent characters. For forename and surname, characters that
are not one of `a-z`, `A-Z`, `&`, `space`, `fullstop`, `comma`, `hyphen` are removed, the first character if it is
one of `&`, `fullstop`, `comma`, `hyphen` is removed, consecutive permissible special characters (`&`, `fullstop`,
`comma`, `hyphen`) are removed. For surname the last character if it is one of `&`, `fullstop`, `comma`, `hyphen`
is removed.

#### Diacritics and Legal Considerations

This section highlights API internal handling of diacritic (accented) chars when the POST create account end point
is triggered. The following Diacritic handling rules only target the forename & surname.  Address & Telephone fields
are accepted as-is.

In this API diacritic characters will be converted to the undecorated underlying chracter. For example, a manual
conversion of ‘Höben’ normally converts to ‘Hoeben’, but the conversion within the API will be to ‘o’ not ‘oe’,
i.e ‘Hoben’.  As such, it could be argued that the legal name or address of that person has been changed.  The
view by HMRC/MDTP & NS&I is that this name change is not legally significant. You may want to consult your legal
representative to ensure they share the same view.

Only diacritic chars up to and including Unicode 3.0 at http://www.unicode.org/Public/3.0-Update/UnicodeData-3.0.0.txt
will have a 1:1 char conversion attempt.  If say a Scandinavian char ø is presented, the char is removed. E.g.
surname "O'Connor-Jørgensen" converts to "O'Connor-Jrgensen".

#### Digitally Excluded Callers
When creating accounts for digitally excluded people, the communicationPreference field must be set to "00" and
registrationChannel must be set to "callCentre".
