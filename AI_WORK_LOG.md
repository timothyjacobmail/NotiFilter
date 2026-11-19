# AI work log

## 2026-11-19

- GitHub `dev` and the local branch matched at `4417056`. Use the account fork as the publish destination.
- Phone package `co.adityarajput.notifilter.debug` was installed with version code 47.
- Found that importing a malformed filter backup could clear saved filters before JSON validation failed. Decode first, replace the database contents in one transaction, read the input stream off the main thread, and report invalid or unreadable backups in Settings.
- The debug build and existing JVM unit tests passed with the import fix.
- Review the settings screen's periodic battery-optimization check and demo data's synchronous database path in a later pass.
