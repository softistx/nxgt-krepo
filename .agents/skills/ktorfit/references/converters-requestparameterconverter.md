<!-- Generated from https://foso.github.io/Ktorfit/converters/requestparameterconverter/ (v) on 2026-08-26. Do not edit; re-run fetch_docs.py. -->

# RequestParameterConverter

```kotlin
@GET("posts/{postId}/comments")
suspend fun getCommentsById(@RequestType(Int::class) @Path("postId") postId: String): List<Comment>
```

You can set RequestType at a parameter with a type to which the parameter should be converted.

Then you need to implement a Converter factory with a RequestParameterConverter.

```kotlin
class StringToIntRequestConverterFactory : Converter.Factory {
    override fun requestParameterConverter(
        parameterType: KClass<*>,
        requestType: KClass<*>
    ): Converter.RequestParameterConverter? {
        return object : Converter.RequestParameterConverter {
            override fun convert(data: Any): Any {
                //convert the data
            }
        }
    }
}
```

```
ktorfit.converterFactories(StringToIntRequestConverterFactory())
```