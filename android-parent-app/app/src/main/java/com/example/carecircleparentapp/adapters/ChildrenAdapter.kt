package com.example.carecircleparentapp.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.carecircleparentapp.Activities.ChildDetailsActivity
import com.example.carecircleparentapp.databinding.ChildItemRvDesignBinding
import com.example.carecircleparentapp.modals.ChildMostUsedApp
import com.example.carecircleparentapp.utils.TimeFormat.formatMillis

class ChildrenAdapter(val context: Context , val childList : ArrayList<ChildMostUsedApp>):
    RecyclerView.Adapter<ChildrenAdapter.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ChildItemRvDesignBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val child = childList[position]
        holder.binding.childName.text = child.childName
        holder.binding.mostUsedApp.text = child.mostUsedAppName
        holder.binding.mostUsedAppTime.text = formatMillis(child.mostUsedMillis)
        holder.binding.screenTime.text = formatMillis(child.totalScreenTimeMillis)

        holder.binding.root.setOnClickListener {
            val intent = Intent(context, ChildDetailsActivity::class.java)
            intent.putExtra("childId", child.childId)
            intent.putExtra("childName", child.childName)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return childList.size
    }

    inner  class ViewHolder(val binding: ChildItemRvDesignBinding) : RecyclerView.ViewHolder(binding.root)
}