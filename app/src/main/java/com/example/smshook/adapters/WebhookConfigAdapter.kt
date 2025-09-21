package com.example.smshook.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smshook.R
import com.example.smshook.fragments.WebhookConfig
import com.google.android.material.switchmaterial.SwitchMaterial

class WebhookConfigAdapter(
    private var webhooks: MutableList<WebhookConfig>,
    private val onTestClick: (WebhookConfig) -> Unit,
    private val onEditClick: (WebhookConfig) -> Unit,
    private val onDeleteClick: (WebhookConfig) -> Unit,
    private val onToggleEnabled: (WebhookConfig, Boolean) -> Unit
) : RecyclerView.Adapter<WebhookConfigAdapter.WebhookViewHolder>() {

    class WebhookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textName: TextView = itemView.findViewById(R.id.textWebhookName)
        val textUrl: TextView = itemView.findViewById(R.id.textWebhookUrl)
        val textPriority: TextView = itemView.findViewById(R.id.textWebhookPriority)
        val textSecret: TextView = itemView.findViewById(R.id.textWebhookSecret)
        val switchEnabled: SwitchMaterial = itemView.findViewById(R.id.switchWebhookEnabled)
        val buttonTest: Button = itemView.findViewById(R.id.buttonTestWebhook)
        val buttonEdit: Button = itemView.findViewById(R.id.buttonEditWebhook)
        val buttonDelete: Button = itemView.findViewById(R.id.buttonDeleteWebhook)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebhookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_webhook_config, parent, false)
        return WebhookViewHolder(view)
    }

    override fun onBindViewHolder(holder: WebhookViewHolder, position: Int) {
        val webhook = webhooks[position]
        
        holder.textName.text = webhook.name
        holder.textUrl.text = webhook.url
        holder.textPriority.text = webhook.priority.toString()
        holder.textSecret.text = if (webhook.secret.isNotEmpty()) "Set" else "Not set"
        holder.switchEnabled.isChecked = webhook.enabled
        
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggleEnabled(webhook, isChecked)
        }
        
        holder.buttonTest.setOnClickListener {
            onTestClick(webhook)
        }
        
        holder.buttonEdit.setOnClickListener {
            onEditClick(webhook)
        }
        
        holder.buttonDelete.setOnClickListener {
            onDeleteClick(webhook)
        }
    }

    override fun getItemCount(): Int = webhooks.size

    fun updateWebhooks(newWebhooks: List<WebhookConfig>) {
        webhooks.clear()
        webhooks.addAll(newWebhooks)
        notifyDataSetChanged()
    }

    fun addWebhook(webhook: WebhookConfig) {
        webhooks.add(webhook)
        notifyItemInserted(webhooks.size - 1)
    }

    fun removeWebhook(webhook: WebhookConfig) {
        val index = webhooks.indexOf(webhook)
        if (index != -1) {
            webhooks.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun updateWebhook(oldWebhook: WebhookConfig, newWebhook: WebhookConfig) {
        val index = webhooks.indexOf(oldWebhook)
        if (index != -1) {
            webhooks[index] = newWebhook
            notifyItemChanged(index)
        }
    }
}




