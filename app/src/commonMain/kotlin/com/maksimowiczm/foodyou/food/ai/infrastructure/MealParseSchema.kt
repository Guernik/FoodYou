package com.maksimowiczm.foodyou.food.ai.infrastructure

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** JSON Schema + system prompt constraining the LLM to the [MealItemsDto] shape. */
internal object MealParseSchema {

    const val SCHEMA_NAME = "meal_items"

    val SYSTEM_PROMPT =
        """
        You are a nutrition assistant. The user describes a meal in natural language.
        Break it into individual food items. For each item return:
        - name: a concise food name.
        - isLiquid: true only for drinks/liquids measured by volume (e.g. milk, juice, coffee).
        - estimatedGrams: your best estimate of the described portion weight in grams (or millilitres
          for liquids). Must be greater than 0.
        - nutrition: values PER 100 GRAMS (or per 100 ml for liquids), NOT for the whole portion.
          energy is in kilocalories (kcal); all other fields are in grams.
          Provide: energy, protein, carbohydrates, fat, dietaryFiber, sugars, saturatedFats, sodium.
          Use realistic reference values; leave a field null only if genuinely unknown.
        Only include foods explicitly described. Never invent items. If nothing edible is described,
        return an empty items array.
        """
            .trimIndent()

    private val NUTRITION_FIELDS =
        listOf(
            "energy",
            "protein",
            "carbohydrates",
            "fat",
            "dietaryFiber",
            "sugars",
            "saturatedFats",
            "sodium",
        )

    private fun numberOrNull() = buildJsonObject {
        putJsonArray("type") {
            add("number")
            add("null")
        }
    }

    val schema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") { add("items") }
        putJsonObject("properties") {
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonArray("required") {
                        add("name")
                        add("isLiquid")
                        add("estimatedGrams")
                        add("nutrition")
                    }
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "string") }
                        putJsonObject("isLiquid") { put("type", "boolean") }
                        putJsonObject("estimatedGrams") { put("type", "number") }
                        putJsonObject("nutrition") {
                            put("type", "object")
                            put("additionalProperties", false)
                            putJsonArray("required") {
                                NUTRITION_FIELDS.forEach { add(it) }
                            }
                            putJsonObject("properties") {
                                NUTRITION_FIELDS.forEach { field -> put(field, numberOrNull()) }
                            }
                        }
                    }
                }
            }
        }
    }
}
