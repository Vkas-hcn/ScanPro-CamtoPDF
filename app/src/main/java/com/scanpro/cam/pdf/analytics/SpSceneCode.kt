package com.scanpro.cam.pdf.analytics

/**
 * 广告场景码。**逐个照抄 ApolloPDF `report/SceneCode.java` 的 45 个常量**，不许增删改名。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/report/SceneCode.java`
 *
 * 和 [com.scanpro.cam.pdf.ads.SpAdPosition] 的区别：位置（`ac_launch`）说的是「这个位在哪」，
 * 场景码（`ac_launch_notification`）说的是「这次展示是被什么带进来的」。
 * Apollo `FullScreenAdManager.showAd(..., sceneCode, ...)` 上报的是场景码，
 * `AdPosition` 只用来找配置。
 */
object SpSceneCode {
    const val AC_BOTTOM_INT = "ac_bottom_int"
    const val AC_CHECK_INT = "ac_check_int"
    const val AC_COMPRESS_PDF_INT = "ac_compress_pdf_int"
    const val AC_DRAW_EXCEL_INT = "ac_draw_excel_int"
    const val AC_DRAW_PDF_INT = "ac_draw_pdf_int"
    const val AC_DRAW_PPT_INT = "ac_draw_ppt_int"
    const val AC_DRAW_WORD_INT = "ac_draw_word_int"
    const val AC_LAUNCH_BAR = "ac_launch_bar"
    const val AC_LAUNCH_MEDIA = "ac_launch_media"
    const val AC_LAUNCH_NOTIFICATION = "ac_launch_notification"
    const val AC_LAUNCH_OPEN = "ac_launch_open"
    const val AC_LAUNCH_POPUP = "ac_launch_popup"
    const val AC_LAUNCH_UNINSTALL = "ac_launch_uninstall"
    const val AC_LOCK_OFFICE_INT = "ac_lock_office_int"
    const val AC_LOCK_PDF_INT = "ac_lock_pdf_int"
    const val AC_MAINBACK_INT = "ac_mainback_int"
    const val AC_MAIN_BANNER = "ac_main_banner"
    const val AC_MAIN_NAT = "ac_main_nat"
    const val AC_MERGE_PDF_INT = "ac_merge_pdf_int"
    const val AC_NOTE_PDF_INT = "ac_note_pdf_int"
    const val AC_PDF_PHOTO_INT = "ac_pdf_photo_int"
    const val AC_PDF_PPT_INT = "ac_pdf_ppt_int"
    const val AC_PHOTO_PDF_INT = "ac_photo_pdf_int"
    const val AC_PPT_PDF_INT = "ac_ppt_pdf_int"
    const val AC_PPT_PHOTO_INT = "ac_ppt_photo_int"
    const val AC_PRINT_PDF_INT = "ac_print_pdf_int"
    const val AC_RESULT_NAT = "ac_result_nat"
    const val AC_SCAN_INT = "ac_scan_int"
    const val AC_SCAN_PDF_INT = "ac_scan_pdf_int"
    const val AC_SIGN_PDF_INT = "ac_sign_pdf_int"
    const val AC_SPLIT_PDF_INT = "ac_split_pdf_int"
    const val AC_TOP_INT = "ac_top_int"
    const val AC_UNINSTALL1_INT = "ac_uninstall1_int"
    const val AC_UNINSTALL1_NAT = "ac_uninstall1_nat"
    const val AC_UNINSTALL2_INT = "ac_uninstall2_int"
    const val AC_UNINSTALL2_NAT = "ac_uninstall2_nat"
    const val AC_UNLOCK_OFFICE_INT = "ac_unlock_office_int"
    const val AC_UNLOCK_PDF_INT = "ac_unlock_pdf_int"
    const val AC_WORD_PDF_INT = "ac_word_pdf_int"
    const val AC_WORD_PHOTO_INT = "ac_word_photo_int"

    /** Apollo `Constants.FROM_TYPE` 里 `FloatingWindow` 的取值，场景码落 [AC_LAUNCH_POPUP]。 */
    const val FROM_FLOATING_WINDOW = "FloatingWindow"

    /** Apollo `Constants.FROM_TYPE` 里 `delete_app` 的取值，场景码落 [AC_LAUNCH_UNINSTALL]。 */
    const val FROM_DELETE_APP = "delete_app"
}
