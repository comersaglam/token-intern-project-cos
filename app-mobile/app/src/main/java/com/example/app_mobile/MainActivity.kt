package com.example.app_mobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.app_mobile.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    // ViewBinding: activity_main.xml için otomatik üretilen tip-güvenli sınıf.
    // binding.titleText → XML'deki @+id/titleText view'ına doğrudan erişim.
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
