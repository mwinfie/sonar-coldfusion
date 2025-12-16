# SonarQube ColdFusion Plugin

[![CI/CD Pipeline](https://github.com/mwinfie/sonar-coldfusion/actions/workflows/ci.yml/badge.svg)](https://github.com/mwinfie/sonar-coldfusion/actions/workflows/ci.yml) 
[![Nightly Build](https://github.com/mwinfie/sonar-coldfusion/actions/workflows/nightly.yml/badge.svg)](https://github.com/mwinfie/sonar-coldfusion/actions/workflows/nightly.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mwinfie_sonar-coldfusion&metric=alert_status)](https://sonarcloud.io/dashboard?id=mwinfie_sonar-coldfusion) 
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=mwinfie_sonar-coldfusion&metric=coverage)](https://sonarcloud.io/dashboard?id=mwinfie_sonar-coldfusion)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=mwinfie_sonar-coldfusion&metric=security_rating)](https://sonarcloud.io/dashboard?id=mwinfie_sonar-coldfusion)

A [SonarQube plugin](http://www.sonarqube.org/) for analyzing ColdFusion code, based on the [CFLint library](https://github.com/cfmleditor/CFLint).

**Current Version: 3.2.0** - Major performance optimization with thread pool reuse for timeout enforcement, reducing analysis time by up to 91% on large codebases.

## Installation

### For SonarQube 2025.4+
1. Download `sonar-coldfusion-plugin-3.2.0.jar` from the [releases section](https://github.com/mwinfie/sonar-coldfusion/releases) or build it yourself by cloning the code and running `mvn clean package`.
1. Copy `sonar-coldfusion-plugin-3.2.0.jar` to `<sonarqube dir>/extensions/plugins`.
1. Restart SonarQube.

### Using Docker
For Docker-based SonarQube installations:
```bash
# Copy plugin to container
docker cp sonar-coldfusion-plugin-3.0.0.jar sonarqube:/opt/sonarqube/extensions/plugins/

# Restart container
docker restart sonarqube
```

### Legacy Versions
For older SonarQube installations, use the appropriate legacy plugin version (see Compatibility section below).

## Compatibility

SonarQube Version | Plugin Version | Status
------------------|----------------|--------
2025.4+          | 3.2.0          | ✅ **Current** (Plugin API 12.0)
2025.4+          | 3.0.0          | ✅ Supported
9.0 - 9.1        | 2.2.0          | ⚠️ Legacy (Plugin API 9.x)
7.6 - 8.9        | 2.1.1          | ⚠️ Legacy  
5.6 - 7.5        | 1.5.0          | ⚠️ Legacy

### Requirements for v3.2.0
- **SonarQube**: 2025.4 Community/Developer/Enterprise editions
- **Java**: 17+ (minimum 11 for runtime)
- **CFLint**: 1.5.9 (cfmleditor fork - bundled)
- **Maven**: 3.6+ (for building from source)

## Quick Start

1. **Install the plugin** in your SonarQube 2025.4+ instance
2. **Create a `sonar-project.properties`** file in your ColdFusion project:
   ```properties
   sonar.projectKey=my-coldfusion-project
   sonar.projectName=My ColdFusion Project
   sonar.projectVersion=1.0
   sonar.sources=.
   sonar.sourceEncoding=UTF-8
   # Optional: specify file extensions (defaults to .cfc,.cfm)
   sonar.cf.file.suffixes=.cfc,.cfm,.cfml
   ```
3. **Run the SonarQube Scanner**:
   ```bash
   sonar-scanner
   ```

## What's New in v3.2.0

### 🚀 Performance Optimization (v3.2.0)
- **Thread Pool Reuse**: Replaced per-file ExecutorService creation with shared thread pool for timeout enforcement
- **91% Faster Analysis**: Reduced analysis time from 709s to 60s on 493-file repositories
- **Scalability Improvements**: AIP repository (3,380 files) completes in 21 minutes vs projected 90-120 minutes
- **Zero Timeouts**: Maintains all timeout protection features while dramatically improving performance
- **Production Ready**: Tested on repositories ranging from 60 to 3,380 files with 100% success rates

### Key Metrics
- **Per-file overhead**: Reduced from ~1.3s to ~0.05s (96% reduction)
- **Large repository performance**: ~80% faster on 3,000+ file codebases
- **Memory efficiency**: Fixed resource leak with proper ExecutorService lifecycle management

## Previous Releases

### What's New in v3.0.0

### 🚀 **Enhanced Performance**
- **38% faster analysis** compared to v2.2.0
- Optimized Plugin API 12.0 integration
- Improved memory utilization

### 🔧 **Modernized Architecture**
- **Plugin API 12.0**: Updated to latest SonarQube plugin architecture
- **Java 17 Support**: Built with modern Java for better performance
- **Programmatic Configuration**: Enhanced property definition system
- **CFLint Integration**: Uses cfmleditor/CFLint 1.5.9 fork with enhanced features

### 🛡️ **Improved Reliability**
- Comprehensive unit test coverage with Plugin API 12.0 compatibility
- Enhanced error handling and validation
- Better integration with SonarQube 2025.4 security features

### 📊 **Maintained Compatibility**
- **Same CFLint Rules**: All existing rule definitions preserved (cfmleditor/CFLint 1.5.9)
- **Quality Profiles**: Existing configurations remain compatible
- **Analysis Results**: Consistent issue detection and metrics

## Running

Follow the instructions for [analyzing code with SonarQube Scanner](http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner). The ColdFusion plugin will automatically discover and analyze `.cfc` and `.cfm` files.

## Parameters tuning

If you encounter log output indicating, that the Compute Engine of SonarQube has insufficient memory, similar to:

```text
2016.06.22 16:17:43 INFO  ce[o.s.s.c.t.CeWorkerCallableImpl] Execute task | project=ApplyNowModule | type=REPORT | id=AVV4eUIgcn4uboqEX1C3
java.lang.OutOfMemoryError: GC overhead limit exceeded
Dumping heap to java_pid8400.hprof ...
Heap dump file created [565019912 bytes in 6.373 secs]
```

you'll need to increase heap memory on the server, in `<sonarqube dir>/conf/sonar.properties`:

```text
sonar.ce.javaOpts=-Xmx2g -Xms128m -XX:+HeapDumpOnOutOfMemoryError
```

2GB might be enough, or perhaps your code base warrants more.

## Building from Source

### Prerequisites
- **Java**: 17+ (OpenJDK or Oracle JDK recommended)
- **Maven**: 3.6+ (3.9+ recommended)
- **Git**: For cloning the repository
- **SonarQube Plugin API**: 12.0.0.2960 (managed by Maven)

### Development Setup
```bash
# Clone the repository
git clone https://github.com/mwinfie/sonar-coldfusion.git
cd sonar-coldfusion

# Build and test
mvn clean package

# Quick build without tests
mvn clean package -DskipTests

# Development build with verbose output
mvn clean compile -X
```

### CI/CD Information
This project uses GitHub Actions for continuous integration:

- **Main CI Pipeline**: Runs on every push and pull request
- **Nightly Builds**: Extended compatibility testing across OS/Java versions
- **Dependency Updates**: Automated weekly dependency scanning
- **Security Scanning**: Automated vulnerability detection
- **Pull Request Validation**: Comprehensive PR checks and validation

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed development guidelines.

### Build Artifacts
- **Main Plugin**: `target/sonar-coldfusion-plugin-3.2.0.jar`
- **Test Reports**: `target/surefire-reports/`
- **Coverage Reports**: `target/site/jacoco/`

## Contributing

We welcome contributions! Please read our [Contributing Guide](CONTRIBUTING.md) for details on:

- Development setup and workflow
- Code style and standards  
- Testing requirements
- Pull request process
- Issue reporting guidelines

### Quick Contributing Steps
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make your changes and add tests
4. Run the test suite: `mvn clean verify`
5. Submit a pull request

See [CHANGELOG.md](CHANGELOG.md) for recent changes and version history.

## Support and Documentation

- **Issues**: [GitHub Issues](https://github.com/mwinfie/sonar-coldfusion/issues)
- **Discussions**: [GitHub Discussions](https://github.com/mwinfie/sonar-coldfusion/discussions)
- **Documentation**: [Wiki](https://github.com/mwinfie/sonar-coldfusion/wiki)
- **SonarQube Docs**: [Plugin Development](https://docs.sonarqube.org/latest/extend/developing-plugin/)

## Releasing

### Automated Releases
Releases are automated through GitHub Actions:

1. **Create a release** on GitHub with a version tag (e.g., `v3.2.1`)
2. **CI/CD pipeline** automatically builds and attaches artifacts
3. **Release notes** are generated from changelog and commits

### Manual Release Process
For maintainers with appropriate permissions:

```bash
# Update version in pom.xml
mvn versions:set -DnewVersion=3.2.1

# Build and test
mvn clean verify

# Create GitHub release
# Artifacts will be automatically built and attached by CI
```

## Contributors

Many thanks for the people, who created or improved this project:

- Tomek Stec
- Michał Paluchowski
- Nicolas Bihan
- Gareth Edwards

### v3.0.0 Migration Contributors
- SonarQube 2025.4 compatibility upgrade
- Plugin API 12.0 migration and performance optimization
- Enhanced testing and documentation

## License

Copyright 2016-2025 StepStone GmbH
          and contributors

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
