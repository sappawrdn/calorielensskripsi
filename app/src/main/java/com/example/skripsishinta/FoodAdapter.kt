package com.example.skripsishinta

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.skripsishinta.databinding.ItemFoodBinding

class FoodAdapter(private val items: List<FoodItem>) :
    RecyclerView.Adapter<FoodAdapter.FoodViewHolder>() {

    inner class FoodViewHolder(private val binding: ItemFoodBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FoodItem) {
            binding.textFoodNameAndQuantity.text = "${item.quantity} x ${item.name}"

            binding.textFoodWeight.text = "Berat: ${item.weight}g"

            binding.textCaloriesPerItem.text = "(${item.calories} kcal per porsi)"

            val totalCalories = item.quantity * item.calories
            binding.textTotalItemCalories.text = "Total: $totalCalories kcal"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
        val binding = ItemFoodBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FoodViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
        holder.bind(items[position])
    }
}