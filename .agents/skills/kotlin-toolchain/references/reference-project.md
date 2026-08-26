<!-- Generated from https://kotlin-toolchain.org/0.12/reference/project/ (docs 0.12) on 2026-08-26. Do not edit by hand; run sync_docs.py to refresh. -->

# Project file reference

## `modules`

The `modules` section defines which modules are part of this Kotlin project.

Each list element must be the path to a module directory1, relative to the project root. If a `module.yaml` is present in the project root, that root module is always included implicitly and doesn’t need to be listed.

Example:

```yaml
# include the `app` and `lib1` modules explicitly:
modules:
  - app
  - libs/lib1
```

> **Sorting alphabetically is recommended**

This reduces the chance of Git conflicts and makes it easier to visually locate a module in the list.

You can also use [glob patterns](https://en.wikipedia.org/wiki/Glob_(programming)) to include multiple module directories at once. Only directories that contain a `module.yaml` file are taken into account:

```yaml
# include all direct subfolders in the `plugins` dir that contain `module.yaml` files:
modules:
  - plugins/*
```

Globs may contain the following special characters:

- `\*` matches zero or more characters of a path name component without crossing directory boundaries
- `?` matches exactly one character of a path name component
- `\[abc\]` matches exactly one character of the given set (here `a`, `b`, or `c`). A dash (`-`) can be used to match a range, such as `\[a-z\]`. A leading `!` negates the set: `\[!abc\]` matches exactly one character that is *not* in the given set.
- `{a,b}` matches one of the comma-separated subpatterns given in the braces (here `a` or `b`)

> **Using `\*\*` to recursively match directories at multiple depth levels is not supported.**

> **No `//` in `modules:`**

Values in the `modules` list are path globs relative to the project root. Prefixing them with `//` is neither supported nor necessary.

## `plugins`

The `plugins` section lists plugin dependencies that should be made available to project modules. Listing a plugin here does not enable it by itself; it only makes it available so that modules can opt in (by enabling the plugin).

A plugin module referenced here must also be included in the project's `modules` list, otherwise an error is reported.

Example: 

```
plugins:
  - //my-plugin
  - //plugins/my-another-plugin
```

> **Info**

Currently, only dependencies on **local** plugin modules are supported here (the Kotlin Toolchain doesn't support publishing plugins yet). Entries use the same notation as [module dependencies](../../user-guide/dependencies/#module-dependencies).

To enable and configure a plugin for a specific module, use the `plugins` block in that module:

app/module.yaml

```
plugins:
  my-plugin:
    enabled: true
    # plugin-specific settings here
```

Learn more about the [plugin structure](../../user-guide/plugins/topics/structure/).

1. That is, a directory that directly contains a `module.yaml` ↩