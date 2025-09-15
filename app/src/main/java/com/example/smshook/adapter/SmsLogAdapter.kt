package com.example.smshook.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.smshook.R
import com.example.smshook.data.ForwardingStatus
import com.example.smshook.data.SmsLogEntry
import android.util.Log
import java.lang.ref.WeakReference

class SmsLogAdapter(
    private val smsLogList: MutableList<SmsLogEntry>,
    private val onRetryClick: (SmsLogEntry) -> Unit,
    private val onDetailsClick: (SmsLogEntry) -> Unit
) : RecyclerView.Adapter<SmsLogAdapter.SmsLogViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsLogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sms_log, parent, false)
        return SmsLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmsLogViewHolder, position: Int) {
        val smsLogEntry = smsLogList[position]
        holder.bind(smsLogEntry)
    }

    override fun getItemCount(): Int = smsLogList.size

    fun updateData(newList: List<SmsLogEntry>) {
        try {
            val oldList = smsLogList.toList()
            val diffCallback = SmsLogDiffCallback(oldList, newList)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            
            smsLogList.clear()
            smsLogList.addAll(newList)
            diffResult.dispatchUpdatesTo(this)
        } catch (e: Exception) {
            Log.e("SmsLogAdapter", "Error updating data", e)
            // Fallback to simple update
            smsLogList.clear()
            smsLogList.addAll(newList)
            notifyDataSetChanged()
        }
    }

    fun updateEntry(updatedEntry: SmsLogEntry) {
        try {
            val index = smsLogList.indexOfFirst { it.id == updatedEntry.id }
            if (index != -1) {
                smsLogList[index] = updatedEntry
                notifyItemChanged(index)
            }
        } catch (e: Exception) {
            Log.e("SmsLogAdapter", "Error updating entry", e)
        }
    }

    inner class SmsLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textSender: TextView = itemView.findViewById(R.id.textSender)
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val textStatus: TextView = itemView.findViewById(R.id.textStatus)
        private val textTimestamp: TextView = itemView.findViewById(R.id.textTimestamp)
        private val textSimInfo: TextView = itemView.findViewById(R.id.textSimInfo)
        private val textErrorMessage: TextView = itemView.findViewById(R.id.textErrorMessage)
        private val layoutActions: LinearLayout = itemView.findViewById(R.id.layoutActions)
        private val buttonRetry: Button = itemView.findViewById(R.id.buttonRetry)
        private val buttonViewDetails: Button = itemView.findViewById(R.id.buttonViewDetails)

        fun bind(smsLogEntry: SmsLogEntry) {
            // Basic info with null safety
            textSender.text = if (smsLogEntry.isTest) "TEST" else (smsLogEntry.sender.takeIf { it.isNotBlank() } ?: "Unknown")
            textMessage.text = smsLogEntry.getShortMessage()
            textTimestamp.text = smsLogEntry.getFormattedTimestamp()
            textSimInfo.text = smsLogEntry.getSimInfo()

            // Status with color coding
            textStatus.text = smsLogEntry.status.displayName
            updateStatusBackground(textStatus, smsLogEntry.status)

            // Show last attempt summary if present
            val lastCode = smsLogEntry.lastHttpStatus
            val lastDur = smsLogEntry.lastDurationMs
            val extra = when {
                lastCode != null && lastDur != null -> " (HTTP $lastCode, ${lastDur}ms)"
                lastCode != null -> " (HTTP $lastCode)"
                lastDur != null -> " (${lastDur}ms)"
                else -> ""
            }
            if (extra.isNotEmpty()) {
                textStatus.text = smsLogEntry.status.displayName + extra
            }

            // Error message handling with length limit
            if (smsLogEntry.status == ForwardingStatus.FAILED && !smsLogEntry.errorMessage.isNullOrEmpty()) {
                val errorText = smsLogEntry.errorMessage!!
                val displayError = if (errorText.length > 100) {
                    errorText.take(97) + "..."
                } else errorText
                textErrorMessage.text = "Error: $displayError"
                textErrorMessage.visibility = View.VISIBLE
            } else {
                textErrorMessage.visibility = View.GONE
            }

            // Action buttons - show for all messages (successful, failed, or with retries)
            val showActions = smsLogEntry.status == ForwardingStatus.FAILED || 
                            smsLogEntry.status == ForwardingStatus.SUCCESS ||
                            smsLogEntry.retryCount > 0 || 
                            !smsLogEntry.errorMessage.isNullOrEmpty()
            
            if (showActions) {
                layoutActions.visibility = View.VISIBLE
                
                // Retry button - now enabled for both failed and successful messages
                val canRetry = smsLogEntry.status == ForwardingStatus.FAILED || 
                              smsLogEntry.status == ForwardingStatus.SUCCESS
                buttonRetry.isEnabled = canRetry
                buttonRetry.alpha = if (canRetry) 1.0f else 0.6f
                
                // Update retry button text based on status
                buttonRetry.text = when (smsLogEntry.status) {
                    ForwardingStatus.SUCCESS -> "Resend"
                    ForwardingStatus.FAILED -> "Retry"
                    else -> "Retry"
                }
                
                // Use weak reference to prevent memory leaks
                val weakEntryRetry = WeakReference(smsLogEntry)
                buttonRetry.setOnClickListener { 
                    val entry = weakEntryRetry.get()
                    if (canRetry && entry != null) {
                        onRetryClick(entry) 
                    }
                }
                
                // Details button with weak reference
                val weakEntryDetails = WeakReference(smsLogEntry)
                buttonViewDetails.setOnClickListener { 
                    val entry = weakEntryDetails.get()
                    if (entry != null) {
                        onDetailsClick(entry)
                    }
                }
            } else {
                layoutActions.visibility = View.GONE
            }

            // Special styling for test messages
            if (smsLogEntry.isTest) {
                textSender.text = "🧪 TEST"
                itemView.alpha = 0.8f
            } else {
                itemView.alpha = 1.0f
            }
        }

        private fun updateStatusBackground(textView: TextView, status: ForwardingStatus) {
            val context = textView.context
            val drawable = ContextCompat.getDrawable(context, R.drawable.status_background) as GradientDrawable
            
            val color = when (status) {
                ForwardingStatus.SUCCESS -> ContextCompat.getColor(context, android.R.color.holo_green_dark)
                ForwardingStatus.FAILED -> ContextCompat.getColor(context, android.R.color.holo_red_dark)
                ForwardingStatus.PENDING -> ContextCompat.getColor(context, android.R.color.holo_orange_dark)
                ForwardingStatus.RETRYING -> ContextCompat.getColor(context, android.R.color.holo_blue_dark)
            }
            
            drawable.setColor(color)
            textView.background = drawable
        }
    }
}

class SmsLogDiffCallback(
    private val oldList: List<SmsLogEntry>,
    private val newList: List<SmsLogEntry>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        
        return oldItem.status == newItem.status &&
               oldItem.errorMessage == newItem.errorMessage &&
               oldItem.retryCount == newItem.retryCount &&
               oldItem.lastAttemptTime == newItem.lastAttemptTime
    }

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        
        if (oldItem.status != newItem.status) {
            return "status_change"
        }
        if (oldItem.errorMessage != newItem.errorMessage) {
            return "error_change"
        }
        return null
    }
}
