fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## iOS

### ios create_app

```sh
[bundle exec] fastlane ios create_app
```

Create the App Store Connect app record (one-time)

### ios beta

```sh
[bundle exec] fastlane ios beta
```

Archive a Release build and upload to TestFlight

### ios metadata

```sh
[bundle exec] fastlane ios metadata
```

Upload App Store listing (metadata + screenshots), no submit

### ios asc_status

```sh
[bundle exec] fastlane ios asc_status
```

Read-only: print live description + list review (draft) submissions

### ios builds

```sh
[bundle exec] fastlane ios builds
```

Read-only: list uploaded builds and their processing state

### ios release

```sh
[bundle exec] fastlane ios release
```

Create/update the App Store version with metadata, attach a processed build, and submit for review. Usage: fastlane release version:1.1 build:2

### ios cancel_rejected

```sh
[bundle exec] fastlane ios cancel_rejected
```

Cancel the rejected/in-progress review submission holding the version (frees it to resubmit)

### ios submit_version

```sh
[bundle exec] fastlane ios submit_version
```

Attach version 1.0 to a review submission and submit for review (reuses an empty draft)

### ios delete_empty_drafts

```sh
[bundle exec] fastlane ios delete_empty_drafts
```

Hard-DELETE empty draft review submissions (READY_FOR_REVIEW, 0 items) via raw ASC REST

### ios cancel_drafts

```sh
[bundle exec] fastlane ios cancel_drafts
```

Cancel empty draft review submissions (READY_FOR_REVIEW, 0 items)

### ios resubmit_metadata

```sh
[bundle exec] fastlane ios resubmit_metadata
```

Push corrected text metadata (description etc.) and resubmit the rejected version for review — metadata-only, no new binary

### ios submit

```sh
[bundle exec] fastlane ios submit
```

Submit the current version for App Store review (metadata/screenshots already uploaded)

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
