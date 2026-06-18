# examples/my-app

The sample application the pipeline scans, mirrored here from the Gitea repo
`praks/my-app` for reference. Its `Jenkinsfile` is the full proven pipeline:
`build → SAST → SCA → deploy-staging → DAST → report`.

- `pom.xml` — a minimal Maven **WAR**. Intentionally pins **`log4j-core:2.14.1`**
  (Log4Shell, CVE-2021-44228) so the SCA stage finds a real critical CVE.
- `HelloServlet.java` — a trivial servlet so there's a running endpoint for DAST to scan.
- `Jenkinsfile` — the pipeline that runs on every push (see `docs/LIVE-SETUP.md` for the
  docker-in-docker details behind the staging/DAST/SCA stages).

This is a **demo target**, not production code — the vulnerable dependency is deliberate.
