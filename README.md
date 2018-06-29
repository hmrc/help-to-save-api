# help-to-save-api

### Testing the RAML documentation locally
To test the RAML documentation, run this command to use service manager to start the prerequisite microservices:
```
sm --start API_DOCUMENTATION API_DOCUMENTATION_FRONTEND SERVICE_LOCATOR THIRD_PARTY_DEVELOPER_FRONTEND API_DEFINITION ASSETS_FRONTEND -f
```

then start up `help-to-save-api` on port `7004`. Go to `http://127.0.0.1:9680/api-documentation/docs/api`
and go to `PreviewRAML` on the left. In the box to enter the URL put in `http://127.0.0.1:7004/api/conf/1.0/application.raml`.
If everything is OK you should see the api documentation. Otherwise, it should tell you what's wrong with the documentation


### Endpoints

All endpoints in this API are user-restricted. All local calls to the API start with localhost:7004/individuals/help-to-save/ 

We can use help-to-save-test-admin-frontend to build requests to hit these endpoints directly
to the microservice locally or through the API platform in other environments used for testing. 

## POST /account

Sends a create account request to NS&I if the user is eligible. This endpoint requires a JSON body.

If the call is successful, expect a `201` response. If call is not successful expect a `400` 
if there was a validation error or expect a `500` if there was a backend error. If the account
already exists expect a `409` response.

## GET /eligibility

Checks whether or not a person is eligible for help to save. The nino is obtained via auth.

If the call is successful, expect a `200` response with JSON containing the eligibility result.
If the nino is not found in Auth, expect a `403` or if the request validation fails expect a 
`400` or if for another reason the call is not successful expect a `500` response.
 
## GET /eligibility/:nino

This endpoint also checks whether or not a person is eligible for help to save with the 
specified NINO (National Insurance Number).  
 
If the call is successful, expect a `200` response with JSON containing the eligibility result.
If the given nino doesn't match that found in Auth expect a `403` or if the request validation 
fails expect a `400` or if for another reason the call is not successful expect a `500` response.

## GET /account

Sends a request to get account info from NS&I if the user has an account.

If the call is successful, expect a `200` response with JSON containing the eligibility result.
If the given nino doesn't match that found in Auth expect a `403` or if the request validation 
fails expect a `400` or if for another reason the call is not successful expect a `500` response.

### Version

To view all versions of this API, you will need to sign up for an HMRC developer hub account at 
{baseUrl}/developer/registration and ask our team to whitelist your application.

### License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")