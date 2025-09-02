# Contributing to SonarQube ColdFusion Plugin

Thank you for your interest in contributing to the SonarQube ColdFusion Plugin! This document provides guidelines and information for contributors.

## Table of Contents
- [Development Setup](#development-setup)
- [Building the Plugin](#building-the-plugin)
- [Running Tests](#running-tests)
- [Submitting Changes](#submitting-changes)
- [Code Style](#code-style)
- [Testing Guidelines](#testing-guidelines)

## Development Setup

### Prerequisites
- **Java 17 or higher** (Java 17 LTS recommended)
- **Maven 3.6+**
- **Git**
- **SonarQube 2025.4+** (for integration testing)
- **Docker** (optional, for local SonarQube testing)

### Getting Started

1. **Clone the repository:**
   ```bash
   git clone https://github.com/mwinfie/sonar-coldfusion.git
   cd sonar-coldfusion
   ```

2. **Build the project:**
   ```bash
   mvn clean compile
   ```

3. **Run tests:**
   ```bash
   mvn test
   ```

4. **Build the plugin JAR:**
   ```bash
   mvn clean package
   ```

## Building the Plugin

The plugin uses Maven for build management. Key commands:

- **Compile only:** `mvn clean compile`
- **Run tests:** `mvn test`
- **Package plugin:** `mvn clean package`
- **Skip tests:** `mvn clean package -DskipTests`
- **Generate reports:** `mvn site`

The built plugin JAR will be available in `target/sonar-coldfusion-plugin-<version>.jar`.

## Running Tests

### Unit Tests
```bash
mvn test
```

### Integration Tests
Integration tests require a running SonarQube instance:

1. **Start SonarQube with Docker:**
   ```bash
   docker run -d --name sonarqube -p 9000:9000 sonarqube:10.3-community
   ```

2. **Install the plugin:**
   ```bash
   cp target/sonar-coldfusion-plugin-*.jar path/to/sonarqube/extensions/plugins/
   docker restart sonarqube
   ```

3. **Run integration tests:**
   ```bash
   mvn verify -Pintegration-tests
   ```

### Test Coverage
Generate test coverage reports:
```bash
mvn clean verify jacoco:report
```

View the report at `target/site/jacoco/index.html`.

## Submitting Changes

### Pull Request Process

1. **Fork the repository** and create a feature branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes** following the code style guidelines.

3. **Add tests** for any new functionality.

4. **Run the full test suite:**
   ```bash
   mvn clean verify
   ```

5. **Commit your changes** using conventional commit format:
   ```bash
   git commit -m "feat(sensor): add support for new CFLint rule"
   ```

6. **Push to your fork** and submit a pull request.

### Commit Message Format
We use [Conventional Commits](https://conventionalcommits.org/) format:

```
type(scope): description

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `test`: Adding or modifying tests
- `chore`: Maintenance tasks
- `ci`: CI/CD changes

**Examples:**
- `feat(sensor): add support for cfscript syntax`
- `fix(analyzer): resolve issue with include file resolution`
- `docs(readme): update installation instructions`

### Pull Request Requirements

Your pull request must:

- [ ] Follow the conventional commit format for the title
- [ ] Include a clear description of changes
- [ ] Add tests for new functionality
- [ ] Update documentation if needed
- [ ] Pass all CI checks
- [ ] Be focused on a single feature/fix

### Pull Request Template

When creating a pull request, include:

```markdown
## Changes
Describe what changes were made and why.

## Testing
Describe how the changes were tested.

## Breaking Changes
List any breaking changes and migration steps.

## Checklist
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] CI checks pass
- [ ] Follows coding standards
```

## Code Style

### Java Code Style
We follow standard Java conventions with these specifics:

- **Indentation:** 4 spaces (no tabs)
- **Line length:** 120 characters maximum
- **Naming:** 
  - Classes: PascalCase
  - Methods/Variables: camelCase
  - Constants: UPPER_SNAKE_CASE
- **Imports:** No wildcard imports
- **Javadoc:** Required for public classes and methods

### Code Formatting
Use your IDE's auto-formatting with these settings:
- 4 spaces for indentation
- 120 character line limit
- Unix line endings (LF)

### Example Code Style
```java
public class ColdFusionSensor implements Sensor {
    
    private static final String LANGUAGE_KEY = "cf";
    
    private final Configuration configuration;
    
    public ColdFusionSensor(Configuration configuration) {
        this.configuration = configuration;
    }
    
    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor.onlyOnLanguage(LANGUAGE_KEY)
            .createIssuesForRuleRepository(ColdFusionPlugin.REPOSITORY_KEY)
            .name("ColdFusion Sensor");
    }
    
    /**
     * Analyzes ColdFusion files in the project.
     *
     * @param context the sensor context
     */
    @Override
    public void execute(SensorContext context) {
        // Implementation
    }
}
```

## Testing Guidelines

### Test Structure
- **Unit tests:** `src/test/java`
- **Test resources:** `src/test/resources`
- **Integration tests:** Use `@IntegrationTest` annotation

### Test Naming
- Test classes: `<ClassName>Test.java`
- Test methods: `should<ExpectedBehavior>When<Condition>()`

Example:
```java
@Test
public void shouldDetectCfcFilesWhenScanningProject() {
    // Test implementation
}
```

### Test Categories
- **Unit tests:** Fast, isolated tests
- **Integration tests:** Test with SonarQube API
- **Performance tests:** Measure analysis performance

### Mocking
Use Mockito for mocking dependencies:
```java
@Mock
private SensorContext sensorContext;

@Mock
private Configuration configuration;
```

## Development Workflow

### Issue Workflow
1. **Check existing issues** before creating new ones
2. **Create an issue** for bugs or feature requests
3. **Reference issues** in commits: `fixes #123`

### Branch Strategy
- `main`: Production-ready code
- `develop`: Integration branch for features
- `feature/*`: Feature development
- `hotfix/*`: Critical bug fixes
- `release/*`: Release preparation

### Release Process
1. Create release branch from `develop`
2. Update version numbers
3. Update CHANGELOG.md
4. Test release candidate
5. Merge to `main` and tag
6. Deploy to GitHub Releases

## Getting Help

### Resources
- [SonarQube Plugin API Documentation](https://docs.sonarqube.org/latest/extend/developing-plugin/)
- [CFLint Documentation](https://github.com/cfmleditor/CFLint)
- [Project Wiki](https://github.com/mwinfie/sonar-coldfusion/wiki)

### Communication
- **Issues:** Use GitHub Issues for bugs and features
- **Questions:** Create a GitHub Discussion
- **Security:** Email security issues privately

### Code of Conduct
This project follows the [Contributor Covenant Code of Conduct](https://www.contributor-covenant.org/version/2/0/code_of_conduct/). Please be respectful and inclusive in all interactions.

## License
By contributing to this project, you agree that your contributions will be licensed under the Apache License 2.0.
