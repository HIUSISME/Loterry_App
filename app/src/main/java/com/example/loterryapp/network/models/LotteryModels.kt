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
    /**
     * Khác nhau theo loại:
     * - Mega 6/45, Power 6/55: danh sách số ["07", "23", ...]
     * - Max 3D, Max 3D Pro: theo giải { "DB": ["897", "099"], "G1": [...], "G2": [...], "G3": [...] }
     * - Keno: { "JACKPOT": [20 số] }
     * Dùng numbers() hoặc resultsByPrize() thay vì đọc trực tiếp.
     */
    @SerializedName("results") val results: JsonElement? = null,
    /** Tất cả các số theo thứ tự, luôn là danh sách với mọi loại */
    @SerializedName("results_flat") val resultsFlat: List<String>? = null,
    @SerializedName("province") val province: ProvinceInfo? = null,
    /** { "MEGA645_JACKPOT": { amount, count }, "G1": {...}, "G2": {...}, "G3": {...} } */
    @SerializedName("prizes") val prizes: JsonElement? = null
) {
    /** Tất cả các số trúng theo thứ tự (với Max 3D: ĐB, G1, G2, G3 nối liền) */
    fun numbers(): List<String> {
        resultsFlat?.takeIf { it.isNotEmpty() }?.let { return it }
        return resultsByPrize().flatMap { it.second }
    }

    /**
     * Các số theo từng giải, giữ thứ tự của API.
     * Với Mega / Power (results là danh sách) trả về một nhóm duy nhất có mã "".
     */
    fun resultsByPrize(): List<Pair<String, List<String>>> {
        val element = results ?: return emptyList()
        fun valuesOf(e: JsonElement) = if (e.isJsonArray) e.asJsonArray.filter { it.isJsonPrimitive }.map { it.asString } else emptyList()
        return when {
            element.isJsonArray -> listOf("" to valuesOf(element))
            element.isJsonObject -> element.asJsonObject.entrySet().map { (code, value) -> code to valuesOf(value) }
            else -> emptyList()
        }
    }

    /** Giá trị (đồng) và số lượng giải của một mã giải, VD "G1" hoặc "MEGA645_JACKPOT" */
    fun getPrize(code: String): Pair<Long?, Int?> {
        val prize = prizes?.takeIf { it.isJsonObject }?.asJsonObject?.get(code)?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return null to null
        val amount = prize.get("amount")?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
        val count = prize.get("count")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
        return amount to count
    }

    /** Giá trị Jackpot; null nếu API chưa cập nhật (trả về null hoặc 0) */
    fun jackpotAmount(): Long? {
        val amount = jackpot ?: getPrize("${type ?: province?.code}_JACKPOT").first
        return amount?.takeIf { it > 0 }
    }
}
