package com.zppko.pdfplus.nor.horus

import org.json.JSONObject

/** Adapts B's raw dispatch payload. The caller supplies A's Korean Samsung classification. */
object UserPopBridge {
    fun fromDispatch(payload: JSONObject?, isKoreanSamsung: Boolean): UserPop {
        val raw = payload?.opt("acpop_config")
        val pop = when (raw) {
            is JSONObject -> raw
            is String -> runCatching { JSONObject(raw) }.getOrNull()
            else -> null
        }
        // Only original A blocks may supply the four fields missing from B's protocol.
        val originalRaw = payload?.opt(if (isKoreanSamsung) "sams_pop_data" else "comm_pop_data")
        val originalPop = when (originalRaw) {
            is JSONObject -> originalRaw
            is String -> runCatching { JSONObject(originalRaw) }.getOrNull()
            else -> null
        }
        fun number(key: String): Int? {
            val value = pop?.opt(key) ?: return null
            return when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }
        val gap = if (isKoreanSamsung) 20 else 15
        val count = if (isKoreanSamsung) 60 else 80
        return UserPop(
            coIsSwitch = (number("ac_on") ?: 1) == 1,
            cotTime = number("ac_t") ?: gap,
            cotCount = number("ac_t_limit") ?: count,
            couTime = number("ac_u") ?: 0,
            couCount = number("ac_u_limit") ?: number("ac_u_Limit") ?: count,
            coaTime = number("ac_a") ?: gap,
            coaCount = number("ac_a_limit") ?: count,
            freeTimeOne = number("nopop_start") ?: 23,
            freeTimeTwo = number("nopop_end") ?: 6,
            isVoice = originalPop?.optInt("is_voice", 0) ?: 0,
            rpCount = originalPop?.optInt("rp_count", 0) ?: 0,
            rpVoiceCt = originalPop?.optInt("rp_voice_ct", 1) ?: 1,
            delayTm = originalPop?.optInt("delay_tm", 0) ?: 0,
        )
    }
}
