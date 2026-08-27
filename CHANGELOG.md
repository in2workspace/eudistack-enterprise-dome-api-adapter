## [Unreleased]

### Added

- **EUD-38 — inventario CycloneDX y gate de licencias**: el repositorio genera su inventario CycloneDX 1.6 en cada construcción, lo publica como activo de cada release (`sbom-v<version>.cdx.json`, comprobando que la versión del inventario coincide con la del artefacto) y evalúa cada pull request contra la lista de licencias admitidas (`.github/license-policy.json`, transcripción de `conv-quality-security-gates.md` §16.1). El evaluador y su suite de tests viven en `.github/scripts/`, sin dependencias de terceros y sin depender de ningún otro repositorio. Guía operativa: `docs/_shared/guides/license-gate-and-sbom.md` en `eudistack-platform-dev`.

### Changed

- **`net.jcip:jcip-annotations` excluida de `bitcoinj-core`**: no declara ninguna licencia, así que no hay derecho de distribución que invocar, y sus anotaciones son de retención `CLASS` — la JVM no las busca en ejecución.

### Removed

- `io.github.novacrypto:Base58:2022.01.17` (GPL-3.0), **declarada pero nunca importada**: el código usa `org.bitcoinj.base.Base58` (Apache-2.0). Era el mismo componente copyleft fuerte que EUD-219 retiró de emisión y verificación, y sobrevivía aquí porque este repositorio quedó fuera de aquel alcance.

## Changed (2026-06-16)
- Improved GDPR compliance by reducing PII logging.

## [0.0.6] - 2026-06-10
### Changed
- Configured the application base path through Spring WebFlux using `spring.webflux.base-path`.
- Removed `vci/` from the legacy issuance endpoint paths so the prefix is provided by the application context path.

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