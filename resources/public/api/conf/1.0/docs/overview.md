Help to Save (‘HtS’) is a new government scheme to encourage people on low incomes to build up a “rainy day” fund.

Help to Save will target working families on the lowest incomes to help them build up their savings. The scheme will be
open to 3.5 million adults in receipt of Universal Credit with minimum weekly household earnings equivalent to 16 hours
at the National Living Wage, or those in receipt of Working Tax Credit.

A customer can deposit up to a maximum of £50 per month in the account. It will work by providing a 50% government bonus
on the highest amount saved into a HtS account. The bonus is paid after two years with an option to save for a further
two years, meaning that people can save up to £2,400 and benefit from government bonuses worth up to £1,200. Savers will
be able to use the funds in any way they wish. The published implementation date for this is Q2/2018, but the project
will have a controlled go-live with a pilot population in Q1/2018.

### What is this API for?

This is a private API on the HMRC API Platform termed an 'Application Restricted Endpoint' for the Help to Save (HtS)
product that behaves as a proxy for the Create HtS Account end point at NS&I.

Before invoking this synchronous API, the caller is assumed to have already invoked the HtS DES API #2A to check that the
applicant is Eligible for a account creation, and that this returned SUCCESS.

On a successful account creation at NS&I, the API internals will additionally, and asynchronously in the background, set
the HtS ‘Update Account’ flag in ITMP, aka endpoint HtS DES API #4A.

The API will be called by a Vendor to make a request to create a Help to Save account on behalf of an Applicant
who is eligible for a Help to Save account.

### For Digitally Excluded users

In the JSON 'Body' object, the email must not be specified, communicationPreference must be set to "00" and
registrationChannel must be set to "callCentre".