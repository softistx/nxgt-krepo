package com.strange.graphix.schema

import com.strange.graphix.execute.RegisteredLoader

internal fun TypeFieldMeta.toRegisteredLoader(): RegisteredLoader =
    RegisteredLoader(
        name = loaderName,
        instance = instance,
        function = function,
        keysParameter = parentParameter,
    )
