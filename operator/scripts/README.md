# Catalog release automation

`catalog-channels.sh` is used by `make catalog-build` on a temporary template
before variable substitution. It derives the active development minor channel
from `CHANNEL` (the Maven version), retaining released history in older channels.
Changing `main` from `3.3.x` to `3.4.0-SNAPSHOT` therefore needs no catalog edit.
The development bundle belongs to `3.x` and `3.4.x`; `3.3.x` retains only releases.

`make release-catalog-template-update` also uses the script to record a release.
`PREVIOUS_PACKAGE_VERSION` identifies the release, while `VERSION` identifies the
next development version. These can belong to different minors. Repeating the
update does not duplicate a release or create a self-replacing channel entry.

For OpenShift, `published-bundle.sh VERSION` waits for the exact version tag in
`quay.io/community-operator-pipeline-prod/apicurio-registry-3` and returns its
immutable digest reference. The release workflow refreshes upstream catalog
history after waiting and renders from that pipeline-built image. It never
substitutes the upstream Apicurio bundle if publication is delayed. The wait is
bounded to 30 attempts, 30 seconds apart, with a 20-second timeout per request;
on exhaustion the catalog step reports failure through the existing workflow
warning, while post-release processing continues. A pipeline failure or delayed
publication beyond this window still requires a retry once the bundle exists.

Run the offline regression tests with Python 3, jq, and Mike Farah yq:

```sh
python3 -m unittest discover -s operator/scripts -p 'test_catalog_automation.py' -v
```

These tests also run in Verify's required Lint and Validate job.
