help-to-save-api
================

Allows requests to Help to Save from third parties outside of MDTP through the API platform. Access to this API is private - if access
is required please contact the Help to Save team. 


Table of Contents
=================

* [About Help to Save](#about-help-to-save)
* [Running and testing](#running-and-testing)
   * [Running](#running)
   * [Unit tests](#unit-tests)
   * [Testing the RAML documentation locally](#testing-the-raml-documentation-locally)
* [Endpoints](#endpoints)
* [License](#license)

About Help to Save
==================
Please click [here](https://github.com/hmrc/help-to-save#about-help-to-save) for more information.

Running and testing
===================

Running
-------

Run `sbt run` on the terminal to start the service. The service runs on port 7004 by default.

Unit tests
----------
Run `sbt test` on the terminal to run the unit tests.


Testing the YAML documentation locally   
--------------------------------------
To test the RAML documentation, run this command to use service manager to start the prerequisite microservices:

- Clone the api-documentation-frontend GitHub repository.
- Run the new cloned api-documentation-frontend microservice using its run_local_with_dependencies script.

```
sm --start API_DEFINITION THIRD_PARTY_APPLICATION API_EXAMPLE_MICROSERVICE API_PLATFORM_MICROSERVICE THIRD_PARTY_DEVELOPER_FRONTEND CONTACT_FRONTEND API_PLATFORM_XML_SERVICES
sbt "run -Dhttp.port=9680 $*"
```
- Enter the URL of the OpenAPI specification file that is being developed. For example if running open-api-example-microservice locally then enter URL `http://localhost:servicePort/api/conf/1.0/application.yaml`
- Run the API containing the new OpenAPI specification locally: start up `help-to-save-api` on port `7004` using

```
sbt run

```
in the `help-to-save-api` repo. Navigate to the preview page at ` 'http://localhost:9680/api-documentation/docs/openapi/preview` on a browser
and go to `PreviewYAML` on the left. In the box to enter the URL put in `http://localhost:7004/api/conf/2.0/application.yaml`.
If everything is OK you should see the api documentation. Otherwise, it should tell you what's wrong with the documentation


Endpoints
=========

All local calls to the API start with `localhost:7004/individuals/help-to-save/`

You can use [help-to-save-test-admin-frontend](https://github.com/hmrc/help-to-save-test-admin-frontend) to build 
requests to hit these endpoints directly to the microservice locally or through the API platform in other test environments. 

| Path                | Method | Description |
|---------------------|--------|-------------|
| /eligibility        | GET    | Checks whether or not a person is eligible for HTS. The  NINO is retrieved from backend systems |
| /eligibility/{nino} | GET    | This endpoint also checks whether or not a person is eligible for HTS with the specified NINO |
| /account            | POST   | Sends a create account request to NS&I if the user is eligible. This endpoint requires a JSON body. <br> |
| /account            | GET    | Sends a request to get account info from NS&I if the user has an account |

License
=======
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
