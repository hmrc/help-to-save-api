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




