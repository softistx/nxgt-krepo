<!-- Generated from https://foso.github.io/Ktorfit/converters/converters/ (v) on 2026-08-26. Do not edit; re-run fetch_docs.py. -->

# Converters

Converters are used to convert the HTTPResponse or parameters.

They are added inside of a Converter.Factory which will then be added to the Ktorfit builder with the **converterfactories()** function.

### Converter Types

- [ResponseConverters](../responseconverter/)
- [SuspendResponseConverter](../suspendresponseconverter/)
- [RequestParameterConverter](../requestparameterconverter/)

### Existing converter factories

- CallConverterFactory

Add this dependency: 

```
implementation("de.jensklingenberg.ktorfit:ktorfit-converters-call:$CONVERTER_VERSION")
```

You can find all available versions [here](https://repo.maven.apache.org/maven2/de/jensklingenberg/ktorfit/ktorfit-converters-call/)

- FlowConverterFactory

Add this dependency: 

```
implementation("de.jensklingenberg.ktorfit:ktorfit-converters-flow:$CONVERTER_VERSION")
```

You can find all available versions [here](https://repo.maven.apache.org/maven2/de/jensklingenberg/ktorfit/ktorfit-converters-flow/)

- ResponseConverterFactory

Add this dependency: 

```
implementation("de.jensklingenberg.ktorfit:ktorfit-converters-response:$CONVERTER_VERSION")
```

You can find all available versions [here](https://repo.maven.apache.org/maven2/de/jensklingenberg/ktorfit/ktorfit-converters-response/)