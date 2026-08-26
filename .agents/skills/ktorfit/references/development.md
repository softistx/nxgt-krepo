<!-- Generated from https://foso.github.io/Ktorfit/development/ (v) on 2026-08-26. Do not edit; re-run fetch_docs.py. -->

# Development

# Update Ktorfit for new Kotlin version

- Bump **kotlin** in libs.versions
- Change **ktorfitCompiler** in libs.versions to KTORFIT\_VERSION-NEW\_KOTLIN\_VERSION
- Run tests in :ktorfit-compiler-plugin
- Create a PR against master
- Merge PR
- Run GitHub Actions “publish” workflow

## 👷 Project Structure

- compiler plugin - module with source for the compiler plugin
- ktorfit-annotations - module with annotations for the Ktorfit
- ktorfit-ksp - module with source for the KSP plugin
- ktorfit-lib-core - module with source for the Ktorfit lib
- ktorfit-lib - ktorfit-lib-core + dependencies on platform specific clients
- sandbox - experimental test module to try various stuff
- example - contains example projects that use Ktorfit
- docs - contains the source for the GitHub page