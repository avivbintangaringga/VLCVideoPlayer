package com.r7fx.vlctestapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.r7fx.vlctestapp.databinding.ActivityMainBinding
import com.r7fx.vlcvideoplayer.VLCVideoPlayerActivity

class MainActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnHello.setOnClickListener {
            val url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            VLCVideoPlayerActivity.open(this, url)
        }
    }
}