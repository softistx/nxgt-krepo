<!-- Generated from https://kotlin-toolchain.org/0.12/user-guide/plugins/topics/structure/ (v0.12) on 2026-08-26. Do not edit; re-run fetch_docs.py. -->

# Plugin structure

A plugin is a standard Kotlin module with the `jvm/amper-plugin` product type. It has a regular `module.yaml` build file with an additional [`pluginInfo` section](../../../../reference/module/#plugininfo).

In addition, a plugin has a `plugin.yaml` file, where [tasks](../tasks/) are registered and configured.

The implementation of the tasks (the plugin's logic) is written in Kotlin in the `src` directory.

**Planned: simple cases – simpler structure**

There can be simpler cases for custom build logic for which having a full-blown plugin may be overkill. For example, a single ad-hoc task action that is only needed in one module.

We are planning to improve UX for such cases and provide a more laconic way to express them.

Each plugin has an **ID**, which defaults to its module name. The **plugin ID** is used across the project to refer to the plugin, e.g., when enabling it or in diagnostic messages.

**The plugin ID can be customized**

For example: build-config/module.yaml

```
pluginInfo:
  id: "build-konfig"
```

 This changes the ID from the default `build-config` to `build-konfig`.

Changing the default ID makes little sense if we are not sharing the plugin between projects, which is not supported yet. Currently, the ID string doesn't have a well-defined format, so leaving the ID at its default is a good idea.

## Registering plugins in the project

Kotlin Toolchain plugins are module-level plugins – they are enabled per module. There is no such concept as a project-wide plugin in the Kotlin Toolchain.

To make a plugin available in the project, it must be *registered* by listing the dependency on it in the [`plugins` section](../../../../reference/project/#plugins) of the `project.yaml` file: 

```yaml
modules:
  - ...
  - plugins/my-plugin

plugins:
  - ./plugins/my-plugin
```

Registered plugins are not yet *enabled* anywhere. One must enable them manually in each module where they are needed.

There may be cases where a plugin is developed as part of the project, but not needed *in the project itself*. Then such a plugin is present in the `modules` list, but not included in the `plugins` list.

**Similar to `apply false` in Gradle...**

The Kotlin Toolchain's approach to registering plugins project-wide and enabling them per-module is somewhat similar to the recommended approach in Gradle. There one lists plugins at the project level with the `apply false` clause and then enables them where needed.

## Enabling plugins

Plugins can be enabled and [configured](../configuration/#plugin-settings) like this:

module.yaml (shorthand)

module.yaml (expanded)

Just to enable the plugin and leave the default configuration: 

```
plugins:
  <plugin-id>: enabled
```

To enable the plugin and configure the plugin settings: 

```
plugins:
  <plugin-id>:
    enabled: true
    # other options
```

> **Tip**

If many modules use the same plugin, potentially even using some common settings for it, then it may make sense to use a dedicated [module template](../../../templates/) that enables and configures the plugin.