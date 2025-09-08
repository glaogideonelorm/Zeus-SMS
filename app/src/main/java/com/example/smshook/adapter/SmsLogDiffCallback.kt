package com.example.smshook.adapter

import androidx.recyclerview.widget.DiffUtil
import com.example.smshook.data.SmsLogEntry

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
        
        return oldItem.id == newItem.id &&
                oldItem.sender == newItem.sender &&
                oldItem.message == newItem.message &&
                oldItem.status == newItem.status &&
                oldItem.errorMessage == newItem.errorMessage &&
                oldItem.retryCount == newItem.retryCount &&
                oldItem.lastAttemptTime == newItem.lastAttemptTime
    }

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        
        // Return a payload indicating what changed for partial updates
        val changes = mutableListOf<String>()
        
        if (oldItem.status != newItem.status) {
            changes.add("STATUS")
        }
        
        if (oldItem.errorMessage != newItem.errorMessage) {
            changes.add("ERROR")
        }
        
        if (oldItem.retryCount != newItem.retryCount) {
            changes.add("RETRY_COUNT")
        }
        
        if (oldItem.lastAttemptTime != newItem.lastAttemptTime) {
            changes.add("LAST_ATTEMPT")
        }
        
        return if (changes.isNotEmpty()) changes else null
    }
}
