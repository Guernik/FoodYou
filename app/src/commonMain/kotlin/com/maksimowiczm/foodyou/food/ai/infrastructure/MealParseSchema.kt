package com.maksimowiczm.foodyou.food.ai.infrastructure

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
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

    /**
     * Single-product variant: instructs the model to merge everything the user described into ONE
     * combined item instead of one item per component. Uses the same [schema]; the `items` array
     * simply contains a single element.
     */
    val SINGLE_PRODUCT_SYSTEM_PROMPT =
        """
        You are a nutrition assistant. The user describes a meal in natural language.
        Treat the ENTIRE description as ONE combined dish and return EXACTLY ONE item in the items
        array. Do NOT split it into separate components. For that single item return:
        - name: one concise combined name for the whole dish (e.g. "Tortilla de carne y queso").
        - isLiquid: true only if the whole dish is a drink/liquid measured by volume.
        - estimatedGrams: the TOTAL weight of the whole combined dish (the sum of all its
          components) in grams (or millilitres for liquids). Must be greater than 0.
        - nutrition: values PER 100 GRAMS of the FINISHED COMBINED DISH (or per 100 ml for liquids),
          NOT for the whole portion and NOT the sum of the components' per-100g values. Compute the
          weight-weighted average of the components across the total weight, so the result stays a
          realistic per-100g figure for the combined dish.
          energy is in kilocalories (kcal); all other fields are in grams.
          Provide: energy, protein, carbohydrates, fat, dietaryFiber, sugars, saturatedFats, sodium.
          Use realistic reference values; leave a field null only if genuinely unknown.
        Only describe foods explicitly mentioned. Never invent items. If nothing edible is described,
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
