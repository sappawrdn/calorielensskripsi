package com.example.skripsishinta

import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.skripsishinta.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOOD_LIST = "extra_food_list"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val foodList = intent.getParcelableArrayListExtra<FoodItem>(EXTRA_FOOD_LIST) ?: arrayListOf()

        val adapter = FoodAdapter(foodList)
        binding.recyclerFood.layoutManager = LinearLayoutManager(this)
        binding.recyclerFood.adapter = adapter

        val totalCalories = foodList.sumOf { it.calories * it.quantity }
        binding.textTotalCalories.text = "Total Kalori: $totalCalories kcal"

        val imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        imageUriString?.let {
            val imageUri = Uri.parse(it)
            binding.imageResult.setImageURI(imageUri)
        }
    }
}