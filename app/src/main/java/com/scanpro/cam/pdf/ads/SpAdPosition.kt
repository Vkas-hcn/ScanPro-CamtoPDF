package com.scanpro.cam.pdf.ads

/**
 * 广告位置常量。**逐个照抄 ApolloPDF `ad/AdPosition.java` 的 17 个 key**，不许增删改名。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/AdPosition.java`
 */
object SpAdPosition {
    const val AC_CHECK_INT = "ac_check_int"
    const val AC_FILE_INT = "ac_file_int"
    const val AC_GUIDE_INT = "ac_guide_int"
    const val AC_GUIDE_NAT = "ac_guide_nat"
    const val AC_LANGUAGE_INT = "ac_language_int"
    const val AC_LANGUAGE_NAT = "ac_language_nat"
    const val AC_LAUNCH = "ac_launch"
    const val AC_MAIN_BACK_INT = "ac_mainback_int"
    const val AC_MAIN_NAT = "ac_main_nat"
    const val AC_RECENT_BANNER = "ac_recent_banner"
    const val AC_RECENT_NAT = "ac_recent_nat"
    const val AC_SETTING_NAT = "ac_setting_nat"
    const val AC_TOP_INT = "ac_top_int"
    const val AC_UNINSTALL1_INT = "ac_uninstall1_int"
    const val AC_UNINSTALL1_NAT = "ac_uninstall1_nat"
    const val AC_UNINSTALL2_INT = "ac_uninstall2_int"
    const val AC_UNINSTALL2_NAT = "ac_uninstall2_nat"

    /** 同 Apollo `AdConfig` 的 17 个字段，顺序一致。 */
    val ALL: List<String> = listOf(
        AC_LAUNCH,
        AC_LANGUAGE_INT,
        AC_GUIDE_INT,
        AC_FILE_INT,
        AC_TOP_INT,
        AC_CHECK_INT,
        AC_MAIN_BACK_INT,
        AC_UNINSTALL1_INT,
        AC_UNINSTALL2_INT,
        AC_MAIN_NAT,
        AC_RECENT_NAT,
        AC_RECENT_BANNER,
        AC_SETTING_NAT,
        AC_UNINSTALL1_NAT,
        AC_UNINSTALL2_NAT,
        AC_LANGUAGE_NAT,
        AC_GUIDE_NAT,
    )

    fun isKnown(position: String): Boolean = ALL.contains(position)
}
