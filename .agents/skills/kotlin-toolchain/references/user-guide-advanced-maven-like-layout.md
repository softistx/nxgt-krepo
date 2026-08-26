<!-- Generated from https://kotlin-toolchain.org/0.12/user-guide/advanced/maven-like-layout/ (v0.12) on 2026-08-26. Do not edit; re-run fetch_docs.py. -->

# Maven-like module layout

The Kotlin Toolchain is opinionated about where to put your sources, resources, and tests (see the [standard project layout](../../basics/#project-layout)).

When transitioning from other tools, it would be tedious to move all (re)source files around in addition to converting the build configuration files. To smoothen the transition, the Kotlin Toolchain provides an alternative layout that is compatible with Maven and Gradle. The layout conforms to the [Maven Standard Directory Layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html).

Example:

```
module/
├── module.yaml
└── src/
    ├── main/
    │   ├── java/
    │   │   └── Main.java
    │   ├── kotlin/
    │   │   └── func.kt
    │   └── resources/
    │       └── input.txt
    └── test/
        ├── java/
        │   └── JavaTest.java
        ├── kotlin/
        │   └── KotlinTest.kt
        └── resources/
            └── test-input.txt
```

> **Note**

There is no difference between `java/` and `kotlin/` folders, the Kotlin Toolchain will look for java and kotlin sources in both folders. It is only necessary for the sake of transitioning simplicity.

Choosing the file layout is possible per module.

To enable the maven-like module layout, add the following to the `module.yaml` file:

```
layout: maven-like
```