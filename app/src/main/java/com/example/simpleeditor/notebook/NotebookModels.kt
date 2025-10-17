package com.example.simpleeditor.notebook

sealed class NBCell(open val id: Long) {
    data class CodeCell(override val id: Long, var code: String = "", var output: String = "") : NBCell(id)
    data class MarkdownCell(override val id: Long, var text: String = "", var preview: String = "") : NBCell(id)
}

