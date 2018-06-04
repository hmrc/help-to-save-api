#### Invalid responses
There are some combinations of parameters that are allowed by the JSON schema of the response format but will never occur. These are:

* The combination of “hasWTC” and “hasUC” both being false with an eligible response is not possible    
<pre> 
{
&nbsp&nbsp“eligibility”: { 
&nbsp&nbsp&nbsp&nbsp“isEligible”  : true,
&nbsp&nbsp&nbsp&nbsp“hasWTC”      : false,
&nbsp&nbsp&nbsp&nbsp“hasUC”       : false
&nbsp&nbsp},
&nbsp&nbsp  “accountExists” : false
}
</pre> 

* If an account does not already exists there will always be an accompanying object describing the eligibility result
<pre> 
{ “accountExists” : false }
</pre> 

* If an account already exists there will never be an accompanying “eligibility” object
<pre> 
{
&nbsp&nbsp“eligibility”   : { … },
&nbsp&nbsp“accountExists” : true 
}
</pre>

#### Errors

The error json schema in case of an error is as follows

<pre>
{
  "$schema": "http://json-schema.org/draft-04/schema",
  "description": "JSON schema for Help to Save eligibility check error response",
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

Examples:

In case of auth related error(401):

<pre>
{
 "code": "INVALID_AUTH",
 "message": "Missing Bearer Token"
}
</pre>

In case of OAuth related error(403):

<pre>
{
 "code": "TOKEN_EXPIRED",
 "message": "Token expired"
}
</pre>

In case of server error(5xx):

<pre>
{
 "code": "SERVER_ERROR",
 "message": "Server Error"
}
</pre>

In case of Bad Request(400):

<pre>
{
 "code": "INVALID_NINO",
 "message": "The NINO does not exist"
}
</pre>



