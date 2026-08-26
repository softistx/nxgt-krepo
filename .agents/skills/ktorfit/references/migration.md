<!-- Generated from https://foso.github.io/Ktorfit/migration/ (v) on 2026-08-26. Do not edit; re-run fetch_docs.py. -->

# Migration

Here is how to migrate from deprecated code:

## From <2 to 2.0.0

- You used Response?

Add this dependency:

You can find all available versions [here](https://repo.maven.apache.org/maven2/de/jensklingenberg/ktorfit/ktorfit-converters-response/)

```
implementation("de.jensklingenberg.ktorfit:ktorfit-converters-response:$CONVERTER_VERSION")
```

and add this converter to your Ktorfit instance: 

```
.addConverterFactory(ResponseConverterFactory())
```

- You used Call?

Add this dependency:

You can find all available versions [here](https://repo.maven.apache.org/maven2/de/jensklingenberg/ktorfit/ktorfit-converters-call/)

```
implementation("de.jensklingenberg.ktorfit:ktorfit-converters-call:$CONVERTER_VERSION")
```

and add this converter to your Ktorfit instance: 

```
.addConverterFactory(CallConverterFactory())
```

- You used Flow?

Add this dependency:

You can find all available versions [here](https://repo.maven.apache.org/maven2/de/jensklingenberg/ktorfit/ktorfit-converters-flow/)

```
implementation("de.jensklingenberg.ktorfit:ktorfit-converters-flow:$CONVERTER_VERSION")
```

and add this converter to your Ktorfit instance: 

```
.addConverterFactory(FlowConverterFactory())
```

## From 1.7.0 to 1.8.1

### SuspendResponseConverter

Implement **override suspend fun convert(result: KtorfitResult)**

```kotlin
public suspend fun convert(result: KtorfitResult): T {
    return when (result) {
        is KtorfitResult.Failure -> {
            throw result.throwable // Or do something with the throwable
        }

        is KtorfitResult.Success -> {
            val response = result.response
            //Put the code that was in your other convert function here
        }
    }
}
```

Redirect the deprecated function to the new function:

```
override suspend fun convert(response: HttpResponse): Any {
    return convert(KtorfitResult.Success(response))
}
```