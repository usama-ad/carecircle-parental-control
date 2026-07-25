package com.example.carecircleparentapp.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.carecircleparentapp.R
import com.example.carecircleparentapp.databinding.AlertItemRvDesignBinding
import com.example.carecircleparentapp.modals.AlertModel

class AlertsAdapter(val context: Context, val alertsList : ArrayList<AlertModel>):
    RecyclerView.Adapter<AlertsAdapter.AlertsHolder>() {
    inner class AlertsHolder(val binding: AlertItemRvDesignBinding): RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertsHolder {
        val binding = AlertItemRvDesignBinding.inflate(LayoutInflater.from(context), parent, false)
        return AlertsHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertsHolder, position: Int) {
        holder.binding.alertContent.text = alertsList[position].content
        holder.binding.alertTime.text = alertsList[position].date
        holder.binding.alertTitle.text = alertsList[position].type
        val icon = when(alertsList[position].type){
            "Screen Time" -> R.drawable.time
            "Close Screen" -> R.drawable.eye_strain
            "Eye Blink" -> R.drawable.eye_strain
            else -> {
                R.drawable.time
            }
        }
        holder.binding.alertIcon.setImageResource(icon)
    }

    override fun getItemCount(): Int {
        return alertsList.size
    }


}