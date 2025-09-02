# Makefile for SonarQube ColdFusion Plugin Development
# Provides convenient commands for common development tasks

.PHONY: help build test package clean install check lint docs docker-build docker-test release

# Default target
help:
	@echo "SonarQube ColdFusion Plugin - Development Commands"
	@echo ""
	@echo "Building:"
	@echo "  build        - Compile the plugin"
	@echo "  test         - Run unit tests"
	@echo "  package      - Build the plugin JAR"
	@echo "  clean        - Clean build artifacts"
	@echo ""
	@echo "Quality:"
	@echo "  check        - Run all quality checks"
	@echo "  lint         - Run code style checks"
	@echo "  coverage     - Generate test coverage reports"
	@echo ""
	@echo "Development:"
	@echo "  install      - Install plugin in local SonarQube"
	@echo "  docs         - Generate documentation"
	@echo "  deps         - Check for dependency updates"
	@echo ""
	@echo "Docker:"
	@echo "  docker-build - Build plugin with Docker"
	@echo "  docker-test  - Run tests in Docker container"
	@echo ""
	@echo "Release:"
	@echo "  release      - Prepare release (update versions, create tag)"

# Building targets
build:
	@echo "🔨 Compiling source code..."
	mvn clean compile

test:
	@echo "🧪 Running unit tests..."
	mvn test

package:
	@echo "📦 Building plugin JAR..."
	mvn clean package

clean:
	@echo "🧹 Cleaning build artifacts..."
	mvn clean

# Quality targets  
check:
	@echo "✅ Running all quality checks..."
	mvn clean verify

lint:
	@echo "🔍 Running code style checks..."
	mvn checkstyle:check

coverage:
	@echo "📊 Generating test coverage reports..."
	mvn clean verify jacoco:report
	@echo "Coverage report: target/site/jacoco/index.html"

# Development targets
install: package
	@echo "🚀 Installing plugin in local SonarQube..."
	@if [ -z "$(SONARQUBE_HOME)" ]; then \
		echo "❌ Error: SONARQUBE_HOME environment variable not set"; \
		echo "   Set it to your SonarQube installation directory"; \
		exit 1; \
	fi
	@if [ ! -d "$(SONARQUBE_HOME)" ]; then \
		echo "❌ Error: SonarQube directory not found: $(SONARQUBE_HOME)"; \
		exit 1; \
	fi
	cp target/sonar-coldfusion-plugin-*.jar $(SONARQUBE_HOME)/extensions/plugins/
	@echo "✅ Plugin installed. Restart SonarQube to load the plugin."

docs:
	@echo "📚 Generating documentation..."
	mvn site
	@echo "Documentation: target/site/index.html"

deps:
	@echo "🔍 Checking for dependency updates..."
	mvn versions:display-dependency-updates
	mvn versions:display-plugin-updates

# Docker targets
docker-build:
	@echo "🐳 Building plugin with Docker..."
	docker run --rm -v "$(PWD)":/usr/src/app -w /usr/src/app maven:3.9-eclipse-temurin-17 mvn clean package

docker-test:
	@echo "🐳 Running tests in Docker container..."
	docker run --rm -v "$(PWD)":/usr/src/app -w /usr/src/app maven:3.9-eclipse-temurin-17 mvn test

# Release targets
release:
	@echo "🚀 Preparing release..."
	@echo "Current version: $$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)"
	@echo ""
	@echo "Steps to create a release:"
	@echo "1. Update version: mvn versions:set -DnewVersion=X.Y.Z"
	@echo "2. Update CHANGELOG.md with release notes"
	@echo "3. Commit changes: git commit -am 'chore: release vX.Y.Z'"
	@echo "4. Create tag: git tag vX.Y.Z"
	@echo "5. Push: git push && git push --tags"
	@echo "6. Create GitHub release from tag"

# Utility targets
version:
	@mvn help:evaluate -Dexpression=project.version -q -DforceStdout

java-version:
	@java -version

maven-version:
	@mvn -version

# Development server (requires Docker)
dev-server:
	@echo "🚀 Starting SonarQube development server..."
	@if [ ! -f docker-compose.yml ]; then \
		echo "Creating docker-compose.yml for development..."; \
		cat > docker-compose.yml << 'EOF'; \
version: '3.8'; \
services:; \
  sonarqube:; \
    image: sonarqube:10.3-community; \
    ports:; \
      - "9000:9000"; \
    environment:; \
      - SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true; \
    volumes:; \
      - ./target/sonar-coldfusion-plugin-*.jar:/opt/sonarqube/extensions/plugins/sonar-coldfusion-plugin.jar; \
EOF; \
	fi
	docker-compose up -d
	@echo "SonarQube starting at http://localhost:9000"
	@echo "Default credentials: admin/admin"

dev-server-stop:
	@echo "🛑 Stopping SonarQube development server..."
	docker-compose down

# Check environment
env-check:
	@echo "🔍 Checking development environment..."
	@echo "Java version:"
	@java -version 2>&1 | head -1
	@echo ""
	@echo "Maven version:"
	@mvn -version 2>/dev/null | head -1 || echo "❌ Maven not found"
	@echo ""
	@echo "Docker version:"
	@docker --version 2>/dev/null || echo "❌ Docker not found"
	@echo ""
	@if [ -n "$(SONARQUBE_HOME)" ]; then \
		echo "SonarQube home: $(SONARQUBE_HOME)"; \
	else \
		echo "SonarQube home: Not set (use SONARQUBE_HOME env var)"; \
	fi
