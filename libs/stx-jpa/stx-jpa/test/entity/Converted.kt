package com.softistx.jpa.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.serialization.json.Json
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/*
 * The other way to put a document in a column: an `AttributeConverter` doing the serialization
 * itself, instead of Hibernate's `FormatMapper` doing it.
 *
 * The question this answers is whether the converter route is a *replacement* for the format mapper
 * — it is the obvious alternative, and the two differ in what the column becomes and in what a dirty
 * check costs. Measured rather than argued, in `ConvertedJsonTest`.
 */

private val converterJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

@Converter
class AddressConverter : AttributeConverter<Address, String> {
    override fun convertToDatabaseColumn(attribute: Address?): String? =
        attribute?.let { converterJson.encodeToString(Address.serializer(), it) }

    override fun convertToEntityAttribute(dbData: String?): Address? =
        dbData?.let { converterJson.decodeFromString(Address.serializer(), it) }
}

/** A converter alone: whatever column a `String` gets, with no JSON type behind it. */
@Entity
@Table(name = "converted_plain")
class ConvertedPlain(
    @Id var id: Long = 0,
    @Convert(converter = AddressConverter::class) var address: Address = Address(),
)

/** The same converter, asked for a JSON column as well. */
@Entity
@Table(name = "converted_json")
class ConvertedJson(
    @Id var id: Long = 0,
    @Convert(converter = AddressConverter::class)
    @JdbcTypeCode(SqlTypes.JSON)
    var address: Address = Address(),
)
