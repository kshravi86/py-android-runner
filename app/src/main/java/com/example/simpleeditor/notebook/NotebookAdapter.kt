package com.example.simpleeditor.notebook

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.simpleeditor.R
import io.github.rosemoe.sora.widget.CodeEditor

class NotebookAdapter(
    private val onRunCode: (position: Int, code: String) -> Unit,
    private val onPreviewMd: (position: Int, md: String) -> CharSequence,
    private val onDelete: (position: Int) -> Unit,
    private val onMoveUp: (position: Int) -> Unit,
    private val onMoveDown: (position: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val items: MutableList<NBCell> = mutableListOf()

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is NBCell.CodeCell -> 0
        is NBCell.MarkdownCell -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            val v = inf.inflate(R.layout.item_cell_code, parent, false)
            CodeVH(v)
        } else {
            val v = inf.inflate(R.layout.item_cell_markdown, parent, false)
            MdVH(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CodeVH -> bindCode(holder, position)
            is MdVH -> bindMd(holder, position)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].id

    fun onItemMove(from: Int, to: Int) {
        if (from == to) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    private fun bindCode(vh: CodeVH, position: Int) {
        val cell = items[position] as NBCell.CodeCell
        // Apply a better code font (JetBrains Mono via Downloadable Fonts), fallback to monospace
        try {
            val tf = try { ResourcesCompat.getFont(vh.itemView.context, R.font.firacode_regular) } catch (_: Throwable) { null }
                ?: ResourcesCompat.getFont(vh.itemView.context, R.font.jetbrains_mono)
            if (tf != null) {
                vh.editor.setTypefaceText(tf)
                vh.editor.setLigatureEnabled(true)
            }
        } catch (_: Throwable) {
            // Fallback handled by editor default monospace
        }
        vh.editor.setText(cell.code)
        vh.output.text = buildColoredOutput(vh.itemView.context, cell.output)
        vh.btnRun.setOnClickListener {
            cell.code = vh.editor.text.toString()
            onRunCode(position, cell.code)
        }
        vh.btnDelete.setOnClickListener { onDelete(position) }
        vh.btnMoveUp.setOnClickListener { onMoveUp(position) }
        vh.btnMoveDown.setOnClickListener { onMoveDown(position) }
    }

    private fun bindMd(vh: MdVH, position: Int) {
        val cell = items[position] as NBCell.MarkdownCell
        vh.edit.setText(cell.text)
        vh.preview.text = cell.preview
        vh.btnPreview.setOnClickListener {
            cell.text = vh.edit.text.toString()
            val spanned = onPreviewMd(position, cell.text)
            vh.preview.text = spanned
            cell.preview = spanned.toString()
        }
        vh.btnDelete.setOnClickListener { onDelete(position) }
        vh.btnMoveUp.setOnClickListener { onMoveUp(position) }
        vh.btnMoveDown.setOnClickListener { onMoveDown(position) }
    }

    class CodeVH(root: View) : RecyclerView.ViewHolder(root) {
        val editor: CodeEditor = root.findViewById(R.id.codeEditor)
        val output: TextView = root.findViewById(R.id.output)
        val btnRun: View = root.findViewById(R.id.btnRun)
        val btnDelete: View = root.findViewById(R.id.btnDelete)
        val btnMoveUp: View = root.findViewById(R.id.btnMoveUp)
        val btnMoveDown: View = root.findViewById(R.id.btnMoveDown)
    }

    class MdVH(root: View) : RecyclerView.ViewHolder(root) {
        val edit: EditText = root.findViewById(R.id.markdownEdit)
        val preview: TextView = root.findViewById(R.id.preview)
        val btnPreview: View = root.findViewById(R.id.btnPreview)
        val btnDelete: View = root.findViewById(R.id.btnDelete)
        val btnMoveUp: View = root.findViewById(R.id.btnMoveUp)
        val btnMoveDown: View = root.findViewById(R.id.btnMoveDown)
    }
}

private fun buildColoredOutput(ctx: Context, raw: String): CharSequence {
    if (raw.isBlank()) return raw
    val blue = ContextCompat.getColor(ctx, R.color.sky_blue)
    val red = ContextCompat.getColor(ctx, R.color.brand_error)
    val dim = ContextCompat.getColor(ctx, R.color.terminal_dim)
    val sb = SpannableStringBuilder(raw)
    // Colorize 'Out [n]:' label and dim the time/duration line if present
    val lines = raw.split('\n')
    var offset = 0
    for ((idx, line) in lines.withIndex()) {
        val trimmed = line.trimStart()
        if (idx == 0 && trimmed.startsWith("In [")) {
            // First line: leave as default
        } else if (idx == 1 && (trimmed.contains("ms") || trimmed.contains(":"))) {
            // Optional dim timing line
            sb.setSpan(ForegroundColorSpan(dim), offset, offset + line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (trimmed.startsWith("Out [")) {
            sb.setSpan(ForegroundColorSpan(blue), offset, offset + line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (trimmed.startsWith("Traceback (most recent call last):") ||
            Regex("^[A-Za-z_]+Error:.*").matches(trimmed) || trimmed.startsWith("Exception:")) {
            sb.setSpan(ForegroundColorSpan(red), offset, offset + line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        offset += line.length + 1
    }
    return sb
}
