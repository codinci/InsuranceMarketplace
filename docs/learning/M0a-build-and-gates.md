# M0a: Build, quality gates and a walking skeleton

## What was new

- **Maven and the POM.** `pom.xml` describes the project: its modules, its dependencies, and the plugins that run at each build phase. It plays the role of `composer.json`. Maven has no lock file in the way Composer does, so each build resolves versions from the POM.
- **Parent POM and the Spring Boot starter parent.** The root `pom.xml` inherits from `spring-boot-starter-parent`, which pins compatible versions of Spring, Jackson, JUnit and hundreds of other libraries. Modules then declare dependencies without versions.
- **Multi-module builds (the reactor).** `common-events`, `common-starter` and `hello-service` are built in dependency order from one command. `-pl hello-service -am` builds one module and the modules it needs.
- **The Maven wrapper (`mvnw`).** A small script that downloads the exact Maven version pinned in `.mvn/wrapper`, so everyone, including CI, uses the same Maven. It plays the role of a project-local Composer binary.
- **Spring Boot starters and `@SpringBootApplication`.** A starter is a dependency bundle: `spring-boot-starter-webmvc` brings Tomcat, Jackson and Spring MVC. `@SpringBootApplication` turns on auto-configuration, so Spring configures what is on the classpath.
- **Constructor injection.** Spring creates objects and passes their dependencies in. The project forbids field injection (`@Autowired` on a field), and an ArchUnit test enforces that in main code.
- **Quality tools, one per kind of problem:**
  - Spotless with google-java-format fixes and checks formatting (like Laravel Pint).
  - Checkstyle checks style rules that are not formatting (like PHP_CodeSniffer).
  - SpotBugs finds likely bugs in compiled code (like PHPStan, but working on bytecode).
  - ArchUnit checks architecture rules as ordinary tests (like Pest's architecture tests).
  - JaCoCo measures line coverage and fails the build below a floor (like PHPUnit's coverage minimum).
  - Enforcer checks the build itself: Java version, dependency conflicts and SNAPSHOT versions.
- **Docker multi-stage builds.** The first stage (a Maven image) compiles the jar. The second stage (a JRE image) only runs it, so the final image has no compiler or Maven. The container user is non-root with a fixed UID.
- **Hadolint** lints Dockerfiles for mistakes. Here it flagged a non-numeric user.
- **Git hooks** (`core.hooksPath=.githooks`) run checks before each commit.
- **Claude Code hooks** (`.claude/settings.json`) run formatting after the agent edits a Java file.

## Why it was done this way

The plan asks for quality checks that cannot be skipped by forgetting to run them. Putting them in the Maven build means `verify` fails for everyone, and CI runs the same command. The gates were demonstrated by breaking the code on purpose and checking that the build failed (see ADR-004). A gate that has never failed proves nothing.

## How it maps to Laravel

| Here | Laravel equivalent |
|---|---|
| `pom.xml` | `composer.json` (without a lock file) |
| `mvnw` | a project-local Composer binary |
| `spring-boot-starter-webmvc` | `laravel/framework` and its service providers |
| `@SpringBootApplication` | `bootstrap/app.php` and auto-discovered providers |
| Spotless | Laravel Pint |
| Checkstyle | PHP_CodeSniffer |
| SpotBugs | PHPStan |
| JaCoCo | PHPUnit coverage with a minimum |
| ArchUnit | Pest architecture tests or Deptrac |

## Self-check questions (answer before starting M1)

1. The Spring Boot starter parent pins versions. Why does `hello-service` declare `spring-boot-starter-webmvc` without a version, and what would happen if you added a `<version>` to it?
2. Why does `verify.sh` run `clean verify` and not just `verify`? What went wrong in the first coverage demo, and why does the real gate not have that problem?
3. The coverage check excludes `*Application` classes. Name one reason that is acceptable, and one thing you would need to check before adding any other exclusion.

## Official documentation

- Introduction to the POM: https://maven.apache.org/guides/introduction/introduction-to-the-pom.html
- Spring Boot reference: https://docs.spring.io/spring-boot/reference/
- ArchUnit: https://www.archunit.org/
- JaCoCo: https://www.jacoco.org/jacoco/
