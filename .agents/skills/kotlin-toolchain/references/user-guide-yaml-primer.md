<!-- Generated from https://kotlin-toolchain.org/0.12/user-guide/yaml-primer/ (v0.12) on 2026-08-26. Do not edit; re-run fetch_docs.py. -->

# Brief YAML primer

The Kotlin Toolchain uses (a subset of) the YAML language for configuration files.

YAML describes a tree of mappings and values. Mappings have key-value pairs and can be nested. Values can be scalars (string, numbers, booleans) and sequences (lists, sets). YAML is indent-sensitive.

Here is a [cheat-sheet](https://quickref.me/yaml.html) and [YAML 1.2 specification](https://yaml.org/spec/1.2.2/).

Strings can be quoted or unquoted. These are equivalent:

```
string1: foo bar
string2: "foo bar"
string3: 'foo bar'
```

Mapping:

```
mapping-name:
  field1: foo bar
  field2: 1.2
```

List of values (strings):

```
list-name:
  - foo bar
  - "bar baz"
```

List of mapping:

```
list-name:
  - named-mapping:
      field1: x
      field2: y
  - field1: x
    field2: y
```