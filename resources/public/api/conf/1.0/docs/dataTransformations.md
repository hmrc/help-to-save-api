#### Data Transformations by HMRC:

For the following fields; forename, surname, address1, address2, address3, address4, address5 and phoneNumber,
control characters TAB \t, NEWLINE \n, CR \r are replaced with single space, consecutive spaces greater than 1
are replaced with a single space and leading & trailing spaces are removed.

#### Data Transformations by NSI:

For the following fields; forename, surname, address1, address2, address3, address4, address5 and phoneNumber,
accented characters are replaced with unaccented equivalent characters. For forename and surname, characters that
are not one of a-z, A-Z, &, space, fullstop, comma, hyphen are removed, the first character if it is one of &,
fullstop, comma, hyphen is removed, consecutive permissible special characters (&, fullstop, comma, hyphen) are
removed. For surname the last character if it is one of &, fullstop, comma, hyphen is removed.