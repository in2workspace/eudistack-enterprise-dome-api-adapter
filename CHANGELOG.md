## [Unreleased]

## [0.0.5] - 2026-06-02
### Changed
- Set Flyway username and password using a common env var with R2DBC config to avoid duplication.


## [0.0.4] - 2026-06-02
### Changed
- Set the identity JWT credential env var via config file instead of env vars to avoid issues with env var length limits.

## [0.0.3] - 2026-06-02
### Changed
- This upgrade was performed to update ci/cd pipelines and activate them, it doesn't include significant code changes.

## [0.0.2] - 2026-05-22

### Changed
- Simplified SMTP-related env vars to make them similar to the Issuer Core config.

### [0.0.1] - 2026-05-19

### Added
- Added new `last_error` and `issued_by` columns for procedure retry table.

### Changed
- Changed the name of env var `ISSUER_DOME_ADAPTER_ENABLED` to `DOME_ADAPTER_ENABLED`.

## [0.0.0] - 2026-05-04

### Added
- Initial release of the project. This version includes the basic structure and setup for the application, along with initial features and functionalities, including the issuance endpoint for label credentials issuance.
- Added issuance for Employee and Machine credentials.