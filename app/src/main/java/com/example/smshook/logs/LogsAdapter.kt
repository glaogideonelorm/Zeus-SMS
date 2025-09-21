package com.example.smshook.logs

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smshook.R

class LogsAdapter(val logs: MutableList<LogEntry>) : RecyclerView.Adapter<LogsAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timestamp: TextView = itemView.findViewById(R.id.log_timestamp)
        val level: TextView = itemView.findViewById(R.id.log_level)
        val tag: TextView = itemView.findViewById(R.id.log_tag)
        val message: TextView = itemView.findViewById(R.id.log_message)
        val details: TextView = itemView.findViewById(R.id.log_details)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        holder.timestamp.text = log.formattedTime
        holder.level.text = log.level.displayName
        holder.level.setTextColor(Color.parseColor(log.level.color))
        holder.tag.text = log.tag
        holder.message.text = log.message
        if (log.details != null) {
            holder.details.text = log.details
            holder.details.visibility = View.VISIBLE
        } else {
            holder.details.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = logs.size

    fun addLog(logEntry: LogEntry) {
        logs.add(logEntry)
        // Keep only the last 1000 logs
        if (logs.size > 1000) {
            logs.removeAt(0)
            notifyItemRemoved(0)
        }
        notifyItemInserted(logs.size - 1)
    }

    fun clearLogs() {
        logs.clear()
        notifyDataSetChanged()
    }
}
