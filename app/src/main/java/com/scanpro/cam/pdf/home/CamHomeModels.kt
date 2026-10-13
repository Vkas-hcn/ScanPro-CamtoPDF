package com.scanpro.cam.pdf.home

import androidx.annotation.DrawableRes
import com.scanpro.cam.pdf.R

enum class FileType(
    val labelRes: Int,
    @DrawableRes val topIconRes: Int,
    @DrawableRes val topIconSelectedRes: Int,
    @DrawableRes val listIconRes: Int,
    val extensions: Set<String>,
) {
    PDF(
        R.string.home_type_pdf,
        R.drawable.ic_home_top_pdf,
        R.drawable.ic_home_top_pdf_sel,
        R.drawable.ic_home_list_pdf,
        setOf("pdf"),
    ),
    TXT(
        R.string.home_type_txt,
        R.drawable.ic_home_top_txt,
        R.drawable.ic_home_top_txt_sel,
        R.drawable.ic_home_list_txt,
        setOf("txt"),
    ),
    PPT(
        R.string.home_type_ppt,
        R.drawable.ic_home_top_ppt,
        R.drawable.ic_home_top_ppt_sel,
        R.drawable.ic_home_list_ppt,
        setOf("ppt", "pptx"),
    ),
    XLS(
        R.string.home_type_xls,
        R.drawable.ic_home_top_xls,
        R.drawable.ic_home_top_xls_sel,
        R.drawable.ic_home_list_xls,
        setOf("xls", "xlsx"),
    ),
    DOC(
        R.string.home_type_doc,
        R.drawable.ic_home_top_doc,
        R.drawable.ic_home_top_doc_sel,
        R.drawable.ic_home_list_doc,
        setOf("doc", "docx"),
    );

    companion object {
        fun fromFileName(fileName: String): FileType? {
            val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
            if (ext.isEmpty()) return null
            return entries.firstOrNull { ext in it.extensions }
        }
    }
}

data class CamFileItem(
    val id: String,
    val name: String,
    val fileName: String,
    val sizeLabel: String,
    val dateLabel: String,
    val type: FileType,
    val uri: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

sealed class CamListState {
    data object NeedPermission : CamListState()
    data object Empty : CamListState()
    data class ListFiles(val files: List<CamFileItem>) : CamListState()
}
