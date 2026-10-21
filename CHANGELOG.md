# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Violations that keep client arguments, which reach messages and responses, apart from internal arguments, which never leave the application.
- `BusinessRuleViolationException`, thrown once with every violation; its message lists codes only.
- Rule results: a rule either passes or fails with one or more violations.
