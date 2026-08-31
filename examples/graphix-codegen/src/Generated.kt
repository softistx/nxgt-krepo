package com.strange.example.graphix.codegen

import com.strange.example.graphix.codegen.apollo.ProductsQuery
import com.strange.example.graphix.codegen.dgs.types.AddProductInput

/** Touches one type from each plugin so a missing generate fails the compile, not a later test. */
fun addProduct(
    name: String,
    price: Int,
): AddProductInput = AddProductInput(name, price)

fun productsQuery(): ProductsQuery = ProductsQuery()
