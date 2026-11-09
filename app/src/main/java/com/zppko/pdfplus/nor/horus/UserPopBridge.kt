package com.zppko.pdfplus.nor.horus

import org.json.JSONObject

/** Adapts B's raw dispatch payload. The caller supplies A's Korean Samsung classification. */
object UserPopBridge {
    private fun asObject(value: Any?): JSONObject? = when (value) {
        is JSONObject -> value
        is String -> runCatching { JSONObject(value) }.getOrNull()
        else -> null
    }

    fun fromDispatch(payload: JSONObject?, isKoreanSamsung: Boolean): UserPop {
        // 韩国三星优先读 samsung_acpop_config（与 A 的 sams_pop_data 行为对齐），缺块回退 acpop_config。
        val pop = if (isKoreanSamsung) {
            asObject(payload?.opt("samsung_acpop_config")) ?: asObject(payload?.opt("acpop_config"))
        } else {
            asObject(payload?.opt("acpop_config"))
        }
        fun number(key: String): Int? {
            val value = pop?.opt(key) ?: return null
            return when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }
        val gap = 0
        val count = 0
        return UserPop(
            coIsSwitch = (number("ac_on") ?: 0) == 1,
            cotTime = number("ac_t") ?: gap,
            cotCount = number("ac_t_limit") ?: count,
            couTime = number("ac_u") ?: 0,
            couCount = number("ac_u_limit") ?: number("ac_u_Limit") ?: count,
            coaTime = number("ac_a") ?: gap,
            coaCount = number("ac_a_limit") ?: count,
            freeTimeOne = number("nopop_start") ?: 0,
            freeTimeTwo = number("nopop_end") ?: 0,
            isVoice = 0,
            rpCount = 0,
            rpVoiceCt = 1,
            delayTm = 0,
        )
    }
}
