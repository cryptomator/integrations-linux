# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

The changelog starts with version 1.6.1.
Changes to prior versions can be found on the [Github release page](https://github.com/cryptomator/integrations-linux/releases).

## [Unreleased](https://github.com/cryptomator/integrations-linux/compare/1.6.1...HEAD)

### Added
* Flatpak Update Mechanism (#117)
* New KeychainAccess service implementation based on freedesktop secret-service DBus API ([#125](https://github.com/cryptomator/integrations-linux/pull/125))
* Use Maven wrapper for building

### Changed
* Require JDK 25
* Pin GitHub action versions used in CI ([#132](https://github.com/cryptomator/integrations-linux/pull/132))
* Updated dependency `com.fasterxml.jackson.core:jackson-databind` from 2.20.0 to 2.20.1


## [1.6.1](https://github.com/cryptomator/integrations-linux/releases/tag/1.6.1) - 2025-09-17

### Changed
* Updated `org.cryptomator:integrations-api` from 1.6.0 to 1.7.0
* Refactor Dolphin quick access integration for robustness (#114)

### Fixed
* Remove stale bookmarks in Dolphin quick access (#114)


