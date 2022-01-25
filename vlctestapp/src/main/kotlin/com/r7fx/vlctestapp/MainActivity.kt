package com.r7fx.vlctestapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.r7fx.vlctestapp.databinding.ActivityMainBinding
import com.r7fx.vlcvideoplayer.VLCVideoPlayerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnHello.setOnClickListener {
            val url =
                "https://sdnegerisapaya-my.sharepoint.com/personal/avivba_myonedrive_site/_layouts/15/download.aspx?UniqueId=e7a85ab7-ffb9-4bec-b0a2-634c77070402&Translate=false&tempauth=eyJ0eXAiOiJKV1QiLCJhbGciOiJub25lIn0.eyJhdWQiOiIwMDAwMDAwMy0wMDAwLTBmZjEtY2UwMC0wMDAwMDAwMDAwMDAvc2RuZWdlcmlzYXBheWEtbXkuc2hhcmVwb2ludC5jb21AYzM1MWQ2ZjEtODMwNy00MzJiLWIyMzktZmRkNzRhMjY3ZTlhIiwiaXNzIjoiMDAwMDAwMDMtMDAwMC0wZmYxLWNlMDAtMDAwMDAwMDAwMDAwIiwibmJmIjoiMTY0MzEyMDk3MiIsImV4cCI6IjE2NDMxMjQ1NzIiLCJlbmRwb2ludHVybCI6IkI3RGczMXorN0xOTFNpZ1FVd1A1QktZTnZwd2dvOVRjeVBydGQ2ektvZkU9IiwiZW5kcG9pbnR1cmxMZW5ndGgiOiIxNjAiLCJpc2xvb3BiYWNrIjoiVHJ1ZSIsImNpZCI6Ik9HTmxNVFEwWkRFdE9URXdOeTAwWlRSakxUZzJOMll0TURBNE1UWXdZVGxoWmpZeiIsInZlciI6Imhhc2hlZHByb29mdG9rZW4iLCJzaXRlaWQiOiJOekZsTTJZd1pqRXRZMll5TWkwMFlUYzBMVGhtWXprdE1qUTNZV0ptTjJSbU9ETXgiLCJhcHBfZGlzcGxheW5hbWUiOiJBbmltZU9uZSIsImFwcGlkIjoiMzBhMmFhNTItNTI1Zi00NDQ3LTliYjItNGRjYjkyMDRkZWMzIiwidGlkIjoiYzM1MWQ2ZjEtODMwNy00MzJiLWIyMzktZmRkNzRhMjY3ZTlhIiwidXBuIjoiYXZpdmJhQG15b25lZHJpdmUuc2l0ZSIsInB1aWQiOiIxMDAzMjAwMUQwMjhCNEM5IiwiY2FjaGVrZXkiOiIwaC5mfG1lbWJlcnNoaXB8MTAwMzIwMDFkMDI4YjRjOUBsaXZlLmNvbSIsInNjcCI6Im15ZmlsZXMucmVhZCBhbGxmaWxlcy5yZWFkIG15ZmlsZXMud3JpdGUgYWxsZmlsZXMud3JpdGUiLCJ0dCI6IjIiLCJ1c2VQZXJzaXN0ZW50Q29va2llIjpudWxsLCJpcGFkZHIiOiI0MC4xMjYuMjMuMTYyIn0.NGJZS1IwekFTTXMrMEdzSks1NTdJbXFkTFE3T2I2bkwzYldGUlZsOFBWOD0&ApiVersion=2.0"

            VLCVideoPlayerActivity.open(baseContext, null, delayedPlay = { _, play ->
                delay(5000)
                play(url)
            })
        }
    }
}