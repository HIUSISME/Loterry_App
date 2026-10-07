package com.example.loterryapp.network.models

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: T? = null,
    @SerializedName("message") val message: String? = null
)

// Dữ liệu xổ số 3 miền theo ngày
data class VietnamDrawDay(
    @SerializedName("date") val date: String? = null,
    @SerializedName("region") val region: JsonElement? = null,
    @SerializedName("provinces") val provinces: List<ProvinceDraw>? = null
)

data class ProvinceDraw(
    @SerializedName("id") val id: JsonElement? = null,
    @SerializedName("date") val date: String? = null,
    @SerializedName("province") val province: ProvinceInfo? = null,
    @SerializedName("prizes") val prizes: JsonElement? = null,
    @SerializedName("results") val results: JsonElement? = null
) {
    fun getProvinceName(): String {
        return province?.name ?: "Tỉnh"
    }

    fun getProvinceCode(): String {
        return province?.code ?: ""
    }

    /**
     * Lấy danh sách số trúng thưởng theo mã giải ("G8", "DB", "G1"...)
     * An toàn tuyệt đối dù API trả về prizes dạng Object hay results dạng Array
     */
    fun getPrizeNumbers(prizeCode: String): List<String> {
        val resultList = mutableListOf<String>()

        // 1. Thử lấy từ prizes (JsonObject: { "g8": ["08"], "db": ["248087"] })
        if (prizes != null && prizes.isJsonObject) {
            val prizesObj = prizes.asJsonObject
            val key = prizeCode.lowercase()
            val upperKey = prizeCode.uppercase()

            val arrayElement = when {
                prizesObj.has(key) -> prizesObj.get(key)
                prizesObj.has(upperKey) -> prizesObj.get(upperKey)
                else -> null
            }

            if (arrayElement != null) {
                if (arrayElement.isJsonArray) {
                    for (item in arrayElement.asJsonArray) {
                        if (item.isJsonPrimitive) {
                            resultList.add(item.asString)
                        }
                    }
                } else if (arrayElement.isJsonPrimitive) {
                    resultList.add(arrayElement.asString)
                }
            }
        }

        if (resultList.isNotEmpty()) return resultList

        // 2. Thử lấy từ results (JsonArray: [ { "prizeCode": "G8", "values": ["08"] } ])
        if (results != null && results.isJsonArray) {
            for (resItem in results.asJsonArray) {
                if (resItem.isJsonObject) {
                    val obj = resItem.asJsonObject
                    val pCode = obj.get("prizeCode")?.asString ?: obj.get("code")?.asString
                    if (pCode.equals(prizeCode, ignoreCase = true)) {
                        val valElement = obj.get("values") ?: obj.get("numbers")
                        if (valElement != null && valElement.isJsonArray) {
                            for (v in valElement.asJsonArray) {
                                if (v.isJsonPrimitive) {
                                    resultList.add(v.asString)
                                }
                            }
                        } else if (valElement != null && valElement.isJsonPrimitive) {
                            resultList.add(valElement.asString)
                        }
                    }
                }
            }
        }

        return resultList
    }
}

data class ProvinceInfo(
    @SerializedName("id") val id: JsonElement? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("region") val region: JsonElement? = null
) {
    val regionCode: String?
        get() = when {
            region == null || region.isJsonNull -> null
            region.isJsonPrimitive -> region.asString
            region.isJsonObject -> {
                val obj = region.asJsonObject
                obj.get("code")?.asString ?: obj.get("id")?.asString ?: obj.get("name")?.asString
            }
            else -> null
        }
}

data class PrizeResult(
    @SerializedName("prizeCode") val prizeCode: String? = null,
    @SerializedName("values") val values: List<String>? = null
)

// Dữ liệu Vietlott
data class VietlottDraw(
    @SerializedName("id") val id: JsonElement? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("draw_number") val drawNumber: Long? = null,
    @SerializedName("date") val date: String? = null,
    @SerializedName("jackpot") val jackpot: Long? = null,
    @SerializedName("results") val results: List<String>? = null,
    @SerializedName("province") val province: ProvinceInfo? = null
)
