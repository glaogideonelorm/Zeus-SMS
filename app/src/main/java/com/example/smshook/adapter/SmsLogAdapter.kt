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
        val diffCallback = SmsLogDiffCallback(smsLogList.toList(), newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        smsLogList.clear()
        smsLogList.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateEntry(updatedEntry: SmsLogEntry) {
        val index = smsLogList.indexOfFirst { it.id == updatedEntry.id }
        if (index != -1) {
            val oldEntry = smsLogList[index]
            smsLogList[index] = updatedEntry
            
            // Use DiffUtil for single item update
            val diffCallback = SmsLogDiffCallback(listOf(oldEntry), listOf(updatedEntry))
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            
            if (diffCallback.newListSize > 0) {
                notifyItemChanged(index, diffCallback.getChangePayload(0, 0))
            }
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
            // Basic info
            textSender.text = if (smsLogEntry.isTest) "TEST" else smsLogEntry.sender
            textMessage.text = smsLogEntry.getShortMessage()
            textTimestamp.text = smsLogEntry.getFormattedTimestamp()
            textSimInfo.text = smsLogEntry.getSimInfo()

            // Status with color coding
            textStatus.text = smsLogEntry.status.displayName
            updateStatusBackground(textStatus, smsLogEntry.status)

            // Error message handling
            if (smsLogEntry.status == ForwardingStatus.FAILED && !smsLogEntry.errorMessage.isNullOrEmpty()) {
                textErrorMessage.text = "Error: ${smsLogEntry.errorMessage}"
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
                
                buttonRetry.setOnClickListener { 
                    if (canRetry) {
                        onRetryClick(smsLogEntry) 
                    }
                }
                
                // Details button
                buttonViewDetails.setOnClickListener { onDetailsClick(smsLogEntry) }
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
