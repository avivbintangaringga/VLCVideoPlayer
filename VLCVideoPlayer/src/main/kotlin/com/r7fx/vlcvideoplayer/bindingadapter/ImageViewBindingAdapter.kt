package com.r7fx.vlcvideoplayer.bindingadapter

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerDrawable
import com.bumptech.glide.request.target.Target

fun ImageView.loadUrl(url: String?) {
    url?.let {
        val shimmer = Shimmer.AlphaHighlightBuilder().apply {
            setBaseAlpha(1f)
            setHighlightAlpha(0.7f)
            setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
            setAutoStart(true)
        }.build()

        val shimmerDrawable = ShimmerDrawable().apply {
            setShimmer(shimmer)
        }

        Glide.with(context).load(url).placeholder(shimmerDrawable).listener(
            object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    shimmerDrawable.stopShimmer()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    shimmerDrawable.stopShimmer()
                    return false
                }

            }
        ).into(this)
    }
}