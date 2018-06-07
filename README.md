# help-to-save-api

### Testing the RAML documentation locally
o test locally run these with service manager:
```
API_DOCUMENTATION
API_DOCUMENTATION_FRONTEND
SERVICE_LOCATOR
THIRD_PARTY_DEVELOPER_FRONTEND
API_DEFINITION
```

then start up `help-to-save-api` on port `7004`. Go to `http://0.0.0.0:9680/api-documentation/docs/api`
and go to `PreviewRAML` on the left. In the box to enter the URL put in `http://0.0.0.0:7004/api/conf/1.0/application.raml`.
If everything is OK you should see the api documentation. Otherwise, it should tell you what's wrong with the documentation


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
