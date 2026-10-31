# Spring Boot Generator (IntelliJ IDEA plugin)

A Java-based IntelliJ Platform plugin starter project.

## What is included

- Java plugin source code (not Kotlin).
- IntelliJ IDEA 2025.3.6.1 as the development target.
- Java plugin dependency for PSI support.
- A context-menu action named **Generate Spring Boot Structure**.
- Initial action detects a Java class and displays its name.

## Requirements

- JDK 21
- IntelliJ IDEA with Gradle support
- Internet access for the first Gradle sync/build (Gradle downloads IntelliJ Platform and dependencies)

## Open and run

1. Extract this ZIP.
2. Open the extracted `SpringBootGenerator` folder in IntelliJ IDEA.
3. Set the Gradle JVM to JDK 21: Settings → Build, Execution, Deployment → Build Tools → Gradle.
4. Reload Gradle.
5. Run the Gradle task `runIde`.

The first sync can take time because Gradle must download the IntelliJ Platform SDK and dependencies.

## Try the action

In the sandbox IDE launched by `runIde`, open a Java class. Right-click in the editor or on the Java file in Project view and choose **Generate Spring Boot Structure**.

This starter currently detects the selected class only. The next development step is implementing safe package and Java-file generation for Controller, Service, Repository, and DTO.

## Important

This ZIP contains the plugin project source, not a prebuilt installable plugin ZIP. Building an installable plugin requires a successful Gradle build (`buildPlugin`), which downloads the required platform SDK and dependencies.
