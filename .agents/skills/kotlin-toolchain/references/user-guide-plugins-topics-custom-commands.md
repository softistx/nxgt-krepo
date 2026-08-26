<!-- Generated from https://kotlin-toolchain.org/0.12/user-guide/plugins/topics/custom-commands/ (docs 0.12) on 2026-08-26. Do not edit by hand; run sync_docs.py to refresh. -->

# Custom commands

Custom commands are tasks that perform side effects extending the build capabilities. E.g., such tasks can print a report to the console, update the linter baseline, upload artifacts to a custom repository, etc.

## Running custom commands

To run a command, one can use the `do` command followed by the command name.

```bash
$ ./kotlin do updateDetektBaseline
```

> **Note**

To get the name of the commands available in the project run `./kotlin show commands`.

It is also possible to run commands only for some modules by passing the module name in the `--module` option (can be repeated):

```bash
$ ./kotlin do updateDetektBaseline --module api --module core  # Will update baseline only for the 'api' and 'core' modules
# or
$ ./kotlin do updateDetektBaseline -m api -m core
```

## Create custom commands

To create a custom command, you need to [write a task](../tasks/) and register it as a command.

To register a task as a custom command, add it to the `commands` section of the `plugin.yaml`:

plugin.yaml

```
tasks:
  updateBaseline:
    action: !runDetektForBaseline
      sources: ${module.kotlinJavaSources}
      outputFile: ${module.rootDir}/detekt/baseline.xml

commands:
  - updateBaseline  # Can be called via `./kotlin do updateBaseline`

  # You can customize the name of the command using the full form
  - name: updateDetektBaseline  # Can be called via `./kotlin do updateDetektBaseline`
    performedBy: updateBaseline
```