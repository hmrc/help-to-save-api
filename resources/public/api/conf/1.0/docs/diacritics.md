This section highlights API internal handling of diacritic (accented) chars. The following Diacritic handling rules only
target the forename & surname.  Address & Telephone fields are accepted as-is.

When a diacritic character such as ‘o’<double dot> is converted to a regular single ASCII char, whereas ‘o’<double dot>
normally converts manually to ‘oe’.  Example

- a manual conversion of ‘Ho<double-dot>ben’ normally converts to ‘Hoboen’.
- the conversion within the API will be to ‘o’ not ‘oe’, i.e ‘Hoben’.  As such, it could be argued that the legal name
  or address of that person has been changed.  The view by HMRC/MDTP & NS&I is that this name change is not legally
  significant. You may want to consult your legal representative to ensure they share the same view.

Only diacritic chars up to and including Unicode 3.0 at http://www.unicode.org/Public/3.0-Update/UnicodeData-3.0.0.txt
will have a 1:1 char conversion attempt.  If say Scandic char 0-slash is presented, the char is removed. E.g. surname
"O'Connor-Jørgensen" converts to "O'Connor-Jrgensen".