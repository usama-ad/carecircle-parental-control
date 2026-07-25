package com.example.carecircleparentapp.adapters

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.carecircleparentapp.databinding.AppsItemDesignBinding
import com.example.carecircleparentapp.modals.AppItem
import com.example.carecircleparentapp.utils.FirebaseUtils
import androidx.core.graphics.drawable.toDrawable

class AppsAdapter(val context: Context , val appsList : ArrayList<AppItem> , val childId : String): RecyclerView.Adapter<AppsAdapter.AppsHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AppsHolder {
        val binding = AppsItemDesignBinding.inflate(LayoutInflater.from(context),parent,false)
        return AppsHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AppsHolder,
        position: Int
    ) {
        val app = appsList[position]
        holder.binding.appName.text = app.name


        holder.binding.btnToggleRestricted.text = if (app.isRestricted) "Remove" else "Add"
        holder.binding.btnToggleRestricted.setOnClickListener {
            val restrictedRef = FirebaseUtils.db.collection("children_data")
                .document(childId)
                .collection("restricted_apps")
                .document(app.packageName)

            if (app.isRestricted) {
                // Remove from restricted
                restrictedRef.delete()
                    .addOnSuccessListener {
                        app.isRestricted = false
                        notifyItemChanged(position)
                    }
            } else {
                // Add to restricted
                val data = mapOf(
                    "name" to app.name,
                    "package" to app.packageName
                )
                restrictedRef.set(data)
                    .addOnSuccessListener {
                        app.isRestricted = true
                        notifyItemChanged(position)

                    }
            }

        }
    }

    override fun getItemCount(): Int {
        return appsList.size
    }


    inner class AppsHolder(val binding: AppsItemDesignBinding) : RecyclerView.ViewHolder(binding.root)


}