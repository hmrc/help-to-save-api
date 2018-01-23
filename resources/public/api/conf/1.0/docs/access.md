The caller is expected to invoke a private HMRC API Platform endpoint using OAuth 2.0 & OpenID Connect, as documented
here: https://developer.service.hmrc.gov.uk/api-documentation/docs/authorisation/application-restricted-endpoints

The vendor MUST create a new Developer hub account and MUST request a new ApplicationID from HMRC for using this API as
the API permissioning is made against this ID. Perform the following steps:

#### Initial Testing
Step 1: setup a new Sandbox Test account at https://test-developer.service.hmrc.gov.uk/api-documentation old accounts must not be reused.
Step 2: register the application at https://test-developer.service.hmrc.gov.uk/developer/login as new in order to acquire an ApplicationID.
Step 3. Send the ApplicationID + “test” environment name is sent to the Help To Save Dev Team whereby they raise an
internal ticket onto the API Platform backlog to setup access, and will additionally specify ‘grant suppress’ as part
of this ticket. Once this ticket is done, the Help To Save Dev Team will notify the vendor that the API is ready to
“sandbox test”.

#### Live running
Step 1. setup a Live account at https://developer.service.hmrc.gov.uk/api-documentation old accounts must not be reused.
Step 2. register the application at https://developer.service.hmrc.gov.uk/developer/applications/add as new in order to
acquire an ApplicationID (different from Test).
Step 3. as Step 3 in Initial Testing above, except state the env as “Live”.
