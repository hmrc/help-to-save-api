The default HMRC / MDTP API platform timeout is 20 seconds, as set by Play.  The timeout for this interface has been
modified to 48 seconds.  The caller must set a request timeout of > 48 seconds, eg: >= 50 seconds.
