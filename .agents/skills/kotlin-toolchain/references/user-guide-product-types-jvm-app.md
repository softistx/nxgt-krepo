<!-- Generated from https://kotlin-toolchain.org/0.12/user-guide/product-types/jvm-app/ (docs 0.12) on 2026-08-26. Do not edit by hand; run sync_docs.py to refresh. -->

# - Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.        JVM application

Use the `jvm/app` product type in a module to build a JVM console or desktop application.

## Module layout

Here is an overview of the module layout for a JVM application:

```
my-module/
├─ resources/ # (1)!
│  ╰─ logback.xml # (2)!
├─ src/
│  ├─ main.kt
│  ╰─ Util.java # (3)!
├─ test/
│  ╰─ MainTest.java # (4)!
│  ╰─ UtilTest.kt
├─ testResources/
│  ╰─ logback-test.xml # (5)!
╰─ module.yaml
```

1. Resources placed here are copied into the resulting jar.
2. This is just an example resource and can be omitted.
3. You can mix Kotlin and Java source files in a single module, all in the `src` folder.
4. You can test Java code with Kotlin tests or Kotlin code with Java tests.
5. This is just an example resource and can be omitted.

> **Maven compatibility layout for JVM-only modules**

If you're migrating from Maven, you can also configure the [Maven-like layout](../../advanced/maven-like-layout/)

## Entry point

By default, the entry point of JVM applications (the `main` function) is expected to be in a `main.kt` file (case-insensitive) in the `src` folder. The `main` function must either have no parameters or one `Array<String>` parameter (representing the command line arguments).

This can be overridden by specifying a main class explicitly in the module settings: 

```yaml
product: jvm/app

settings:
  jvm:
    mainClass: org.example.myapp.MyMainKt
```

> **Note**

In Kotlin, unlike Java, the `main` function doesn't have to be declared in a class, and is usually at the top level of the file. However, the JVM still expects a main class when running any application. Kotlin always compiles top-level declarations to a class, and the name of that class is derived from the name of the file by capitalizing the name and turning the `.kt` extension into a `Kt` suffix.

For example, the top-level declarations of `myMain.kt` will be in a class named `MyMainKt`.

## Packaging

You can use the `build` command to produce a regular JAR of your application's code, or the `package` command to produce an [Executable JAR](https://docs.spring.io/spring-boot/specification/executable-jar/index.html).

The executable JAR format is developed by the [Spring](https://spring.io/) team, hence why you might see the `spring-boot-loader` embedded in it. That being said, this format is a universal packaging solution suitable for any JVM application. It provides a convenient, runnable self-contained deployment unit that includes all necessary dependencies.

> **Why not create an Über JAR (a.k.a Fat JAR)?**

The usual "Über JAR" (a.k.a "Fat JAR") format is quite popular: it just contains the contents of all your dependency JARs, merge with your own classes and resources. This approach has issues that stem from this unpacking/repacking of JARs. For example:

- signature files are no longer valid after the merge, which can render some security libraries invalid
- `META-INF/MANIFEST.MF` files are duplicated in many JARs, and thus need to be discarded or merged
- service loader resources also may have conflicting names and need to be merged
- any other duplicate class/resource file need to be handle in custom ways, which have to be configurable

The executable JAR format doesn't have these problems because it keeps the original JARs intact – it just embeds them and loads them on-demand.