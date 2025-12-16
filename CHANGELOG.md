# Changelog

All notable changes to the SonarQube ColdFusion Plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.2.0] - 2025-12-16

### 🚀 Performance Improvements
- **Thread Pool Optimization**: Implemented shared ExecutorService for timeout enforcement, replacing per-file thread pool creation
- **91% Faster Analysis**: Reduced analysis time from 709s to 60s on 493-file repositories (repo-08 benchmark)
- **Large Codebase Support**: AIP repository (3,380 files) now completes in 21 minutes vs 90-120 minutes projected with old approach
- **Per-File Overhead Reduction**: Decreased from ~1.3s to ~0.05s per file (96% reduction)

### 🔧 Fixed
- **Resource Leak**: Fixed ExecutorService cleanup to ensure proper shutdown in all exit paths
- **Timeout Protection**: Maintained full timeout functionality while improving performance
- **Circuit Breaker Integration**: Added proper executor cleanup when circuit breaker triggers

### 📊 Performance Metrics
| Repository | Files | Before (v3.1.0-rc2) | After (v3.2.0) | Improvement |
|------------|-------|---------------------|----------------|-------------|
| repo-08    | 493   | 709s (11.8 min)     | 60s (1 min)    | 91.5% faster |
| AIP        | 3,380 | ~5,400-7,200s est.  | 1,269s (21 min)| ~80% faster |

### ✅ Testing
- Tested on repositories ranging from 60 to 3,380 files
- Zero timeouts observed across all test cases
- 100% file analysis success rate maintained
- Circuit breaker functionality verified

### 📝 Technical Details
- Single-threaded executor created once per analysis session
- Reused across all file analyses within session
- Proper lifecycle management with finally blocks
- Thread safety maintained with sequential processing

---

## [3.1.0-rc2] - 2025-12-15

### Added
- **Timeout Protection**: Per-file timeout enforcement to prevent analysis hangs
- **Circuit Breaker**: Automatic stopping after consecutive timeout threshold
- **Progress Reporting**: Enhanced logging with file-by-file progress updates

### Changed
- ExecutorService-based timeout mechanism (later optimized in v3.2.0)

---

## [3.0.0] - 2025-12-01

### 🎉 Major Update
- **SonarQube 2025.4+ Compatibility**: Updated for modern SonarQube versions
- **Plugin API 12.0**: Migrated to latest Plugin API
- **Java 17**: Updated minimum Java version requirement

### Changed
- Modernized codebase for current SonarQube ecosystem
- Updated dependencies to latest versions
- Improved code quality and maintainability

### Requirements
- SonarQube 2025.4 or higher
- Java 17 or higher
- CFLint 1.5.9

---

## [2.x.x] - Legacy Versions

Previous versions (2.x.x and earlier) supported older SonarQube versions.
See [releases](https://github.com/mwinfie/sonar-coldfusion/releases) for details.

---

[3.2.0]: https://github.com/mwinfie/sonar-coldfusion/releases/tag/v3.2.0
[3.1.0-rc2]: https://github.com/mwinfie/sonar-coldfusion/releases/tag/v3.1.0-rc2
[3.0.0]: https://github.com/mwinfie/sonar-coldfusion/releases/tag/v3.0.0
