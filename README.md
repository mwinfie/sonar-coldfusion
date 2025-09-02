# SonarQube ColdFusion Plugin

[![CI](https://github.com/stepstone-tech/sonar-coldfusion/actions/workflows/ci.yml/badge.svg)](https://github.com/stepstone-tech/sonar-coldfusion/actions/workflows/ci.yml) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=stepstone-tech_sonar-coldfusion&metric=alert_status)](https://sonarcloud.io/dashboard?id=stepstone-tech_sonar-coldfusion) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=stepstone-tech_sonar-coldfusion&metric=coverage)](https://sonarcloud.io/dashboard?id=stepstone-tech_sonar-coldfusion)

A [SonarQube plugin](http://www.sonarqube.org/) for analyzing ColdFusion code, based on the [CFLint library](https://github.com/cflint/CFLint).

**Current Version: 3.0.0** - Updated for SonarQube 2025.4+ compatibility with enhanced performance and modern Plugin API support.

## Installation

### For SonarQube 2025.4+
1. Download `sonar-coldfusion-plugin-3.0.0.jar` from the [releases section](https://github.com/stepstone-tech/sonar-coldfusion/releases) or build it yourself by cloning the code and running `mvn clean package`.
1. Copy `sonar-coldfusion-plugin-3.0.0.jar` to `<sonarqube dir>/extensions/plugins`.
1. Restart SonarQube.

### Legacy Versions
For older SonarQube installations, use the appropriate legacy plugin version (see Compatibility section below).

## Compatibility

SonarQube Version | Plugin Version | Status
------------------|----------------|--------
2025.4+          | 3.0.0          | ✅ **Current** (Plugin API 12.0)
9.0 - 9.1        | 2.2.0          | ⚠️ Legacy (Plugin API 9.x)
7.6 - 8.9        | 2.1.1          | ⚠️ Legacy  
5.6 - 7.5        | 1.5.0          | ⚠️ Legacy

### Requirements for v3.0.0
- **SonarQube**: 2025.4 Community/Developer/Enterprise editions
- **Java**: 17+ (minimum 11 for runtime)
- **CFLint**: 1.5.0 (bundled)

## What's New in v3.0.0

### 🚀 **Enhanced Performance**
- **38% faster analysis** compared to v2.2.0
- Optimized Plugin API 12.0 integration
- Improved memory utilization

### 🔧 **Modernized Architecture**
- **Plugin API 12.0**: Updated to latest SonarQube plugin architecture
- **Java 17 Support**: Built with modern Java for better performance
- **Programmatic Configuration**: Enhanced property definition system

### 🛡️ **Improved Reliability**
- Comprehensive unit test coverage with Plugin API 12.0 compatibility
- Enhanced error handling and validation
- Better integration with SonarQube 2025.4 security features

### 📊 **Maintained Compatibility**
- **Same CFLint Rules**: All existing rule definitions preserved
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

## Building

### Prerequisites
- **Java**: 17+ (OpenJDK or Oracle JDK)
- **Maven**: 3.6+ (3.9+ recommended)
- **SonarQube Plugin API**: 12.0.0.2960 (managed by Maven)

### Build Commands
```bash
# Clean build with tests
mvn clean package

# Build without tests (faster)
mvn clean package -DskipTests

# Development build with verbose output
mvn clean compile -X
```

### Build Artifacts
- **Main**: `target/sonar-coldfusion-plugin-3.0.0.jar`
- **Tests**: Unit tests validate Plugin API 12.0 compatibility
- **Dependencies**: All CFLint and plugin dependencies are bundled

## Releasing

Setup Maven settings.xml with

```xml
  <servers>
    <server>
        <id>github</id>
        <privateKey>yourprivatekey</privateKey>
    </server>
  </servers>
```

Run Maven goal

```bash
mvn clean package de.jutzig:github-release-plugin:1.3.0:release 
```

This will build the plugin jar file, create a release and a tag on github and upload the artifact to
the [repo](https://github.com/stepstone-tech/sonar-coldfusion).

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
