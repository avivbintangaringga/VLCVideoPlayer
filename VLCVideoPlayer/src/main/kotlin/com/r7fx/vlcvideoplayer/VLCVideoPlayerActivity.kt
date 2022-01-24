package com.r7fx.vlcvideoplayer

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.r7fx.vlcvideoplayer.bindingadapter.loadUrl
import com.r7fx.vlcvideoplayer.databinding.ActivityPlayerBinding
import com.r7fx.vlcvideoplayer.util.StringUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import kotlin.math.abs

class VLCVideoPlayerActivity : AppCompatActivity() {
    companion object {
        const val ARG_URL = "url"
        const val ARG_TITLE = "title"
        const val ARG_THUMBNAIL_URL = "thumbnail_url"

        fun open(context: Context, videoUrl: String, title: String? = null, thumbnailUrl: String? = null) {
            context.startActivity(
                Intent(context, VLCVideoPlayerActivity::class.java).apply {
                    putExtra(ARG_URL, videoUrl)
                    putExtra(ARG_TITLE, title)
                    putExtra(ARG_THUMBNAIL_URL, thumbnailUrl)
                }
            )
        }
    }

    private val libVLC: LibVLC by lazy { LibVLC(this) }
    private val mediaPlayer: MediaPlayer by lazy { MediaPlayer(libVLC) }
    private val binding by lazy { ActivityPlayerBinding.inflate(layoutInflater) }
    private val url by lazy { intent.getStringExtra(ARG_URL) }
    private val title by lazy { intent.getStringExtra(ARG_TITLE) }
    private val thumbnailUrl by lazy { intent.getStringExtra(ARG_THUMBNAIL_URL) }
    private val maxStep = 10000
    private val jumpDurationMs = 5000L
    private val uiAnimationDurationMs = 300L
    private val uiHideDelayMs = 4000L
    private val centerMessageHideDelayMs = 1000L
    private val doubleTapDeltaThresholdMs = 300L
    private var chapters: List<MediaPlayer.Chapter>? = null
    private var uiHideSchedulerJob: Job? = null
    private var centerMessageJob: Job? = null
    private var isControlVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.txtTitle.text = title ?: "Loading..."
        binding.imgThumbnail.loadUrl(thumbnailUrl)
        binding.txtTitle.isSelected = true
        binding.slider.valueTo = maxStep.toFloat()
        binding.slider.isEnabled = false

//        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
//        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
//        window.statusBarColor = Color.TRANSPARENT
//        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

//        viewModel.url.observe(this) {
            mediaPlayer.play(Uri.parse(url))
//        }

        binding.btnPlayPause.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                displayCenterMessage("PAUSE")
            } else {
                mediaPlayer.play()
                displayCenterMessage("PLAY")
            }
        }

        binding.btnPrevChapter.setOnClickListener {
            if (mediaPlayer.isSeekable) {
                mediaPlayer.previousChapter()
                displayCenterMessage("PREVIOUS CHAPTER")
                scheduleHideControl()
            }
        }

        binding.btnNextChapter.setOnClickListener {
            if (mediaPlayer.isSeekable) {
                mediaPlayer.nextChapter()
                displayCenterMessage("NEXT CHAPTER")
                scheduleHideControl()
            }
        }

        val jumpClickListener = object : View.OnClickListener {
            private var clickDoneJob: Job? = null
            private var clickCounter = 0
            private var wasPlaying = false
            private var jumpMs = 0L

            private fun displayJump() {
                if (jumpMs > 0) {
                    displayCenterMessage(
                        "+"
                            .plus(StringUtil.longMillisToTimeStamp(jumpMs))
                    )
                } else {
                    displayCenterMessage(
                        "-"
                            .plus(StringUtil.longMillisToTimeStamp(abs(jumpMs)))
                    )
                }
            }

            override fun onClick(v: View?) {
                v?.let {
                    clickCounter++
                    clickDoneJob?.cancel()

                    if (clickCounter == 1) {
                        wasPlaying = mediaPlayer.isPlaying
                        if (wasPlaying) mediaPlayer.pause()
                    }

                    when (v.id) {
                        R.id.btnBackward -> {
                            if (mediaPlayer.time + (jumpMs - jumpDurationMs) > 0) {
                                jumpMs -= jumpDurationMs
                            } else {
                                jumpMs = -mediaPlayer.time
                            }
                        }
                        R.id.btnForward -> {
                            if (mediaPlayer.time + (jumpMs + jumpDurationMs)
                                <= mediaPlayer.length
                            ) {
                                jumpMs += jumpDurationMs
                            } else {
                                jumpMs = mediaPlayer.length - mediaPlayer.time - 1
                            }
                        }
                    }

                    if (mediaPlayer.length > 0) {
                        binding.slider.value =
                            ((mediaPlayer.time + jumpMs).toFloat() / mediaPlayer.length) * maxStep
                    }

                    displayJump()
                    cancelScheduleHideControl()

                    clickDoneJob = lifecycleScope.launch {
                        delay(centerMessageHideDelayMs)
                        clickCounter = 0
                        mediaPlayer.time += jumpMs
                        jumpMs = 0

                        if (wasPlaying) mediaPlayer.play()

                        scheduleHideControl()
                    }
                }
            }
        }

        binding.btnBackward.setOnClickListener(jumpClickListener)
        binding.btnForward.setOnClickListener(jumpClickListener)

        binding.touchPad.setOnTouchListener(object : View.OnTouchListener {
            private val dragThreshold = 50F

            private var lastX = 0F
            private var deltaX = 0F
            private var lastClickTime = 0L
            private var singleTapJob: Job? = null
            private var multiTapDoneJob: Job? = null
            private var dragDoneJob: Job? = null
            private var multiTapCounter = 0
            private var dragCounter = 0
            private var jumpMs = 0L
            private var wasPlaying = false
            private var dragWasPlaying = false
            private var doPlayCheck = true

            private fun displayJump(ms: Long = jumpMs) {
                if (ms > 0) {
                    displayCenterMessage(
                        "+"
                            .plus(StringUtil.longMillisToTimeStamp(ms))
                    )
                } else {
                    displayCenterMessage(
                        "-"
                            .plus(StringUtil.longMillisToTimeStamp(abs(ms)))
                    )
                }
            }

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                v?.let {
                    val endLeft = v.width * 2 / 5
                    val startRight = v.width * 3 / 5

                    when (event?.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = event.x

                            binding.linChapterListBox.isVisible = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            deltaX = event.x - lastX
                            if (abs(deltaX) >= dragThreshold) {
                                dragDoneJob?.cancel()

                                if (dragCounter == 0 && doPlayCheck) {
                                    doPlayCheck = false
                                    dragWasPlaying = mediaPlayer.isPlaying
                                    if (dragWasPlaying) mediaPlayer.pause()
                                }

                                val addTime = (jumpMs + (deltaX / v.width) * 60000).toLong()

                                when {
                                    mediaPlayer.time + addTime <= 0 -> {
                                        displayJump(-mediaPlayer.time)
                                    }
                                    mediaPlayer.time + addTime >= mediaPlayer.length -> {
                                        displayJump(mediaPlayer.length - mediaPlayer.time - 1)
                                    }
                                    else -> {
                                        displayJump(addTime)
                                    }
                                }
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            val deltaX = event.x - lastX
                            if (abs(deltaX) < dragThreshold) {
                                val clickTime = System.currentTimeMillis()

                                if (clickTime - lastClickTime <
                                    if (multiTapCounter == 0) doubleTapDeltaThresholdMs
                                    else centerMessageHideDelayMs
                                ) {
                                    singleTapJob?.cancel()
                                    multiTapDoneJob?.cancel()

                                    if (multiTapCounter == 1) {
                                        wasPlaying = mediaPlayer.isPlaying

                                        if (wasPlaying) mediaPlayer.pause()
                                    }

                                    if (event.x <= endLeft || event.x >= startRight) {
                                        multiTapCounter++

                                        if (event.x <= endLeft) {
                                            if (mediaPlayer.time + (jumpMs - jumpDurationMs) > 0) {
                                                jumpMs -= jumpDurationMs
                                            } else {
                                                jumpMs = -mediaPlayer.time
                                            }
                                        } else if (event.x >= startRight) {
                                            if (mediaPlayer.time + (jumpMs + jumpDurationMs)
                                                <= mediaPlayer.length
                                            ) {
                                                jumpMs += jumpDurationMs
                                            } else {
                                                jumpMs = mediaPlayer.length - mediaPlayer.time - 1
                                            }
                                        }

                                        if (mediaPlayer.length > 0) {
                                            binding.slider.value =
                                                ((mediaPlayer.time + jumpMs).toFloat() /
                                                        mediaPlayer.length) * maxStep
                                        }

                                        displayJump()

                                        multiTapDoneJob = lifecycleScope.launch {
                                            delay(centerMessageHideDelayMs)
                                            multiTapCounter = 0
                                            mediaPlayer.time += jumpMs
                                            jumpMs = 0

                                            if (wasPlaying) mediaPlayer.play()
                                        }
                                    } else {
                                        binding.btnPlayPause.performClick()
                                    }
                                } else {
                                    singleTapJob?.cancel()
                                    singleTapJob = lifecycleScope.launch {
                                        delay(doubleTapDeltaThresholdMs)
                                        toggleControl()
                                    }
                                }

                                lastClickTime = clickTime
                            } else {
                                dragCounter++
                                dragDoneJob?.cancel()

                                jumpMs += (deltaX / v.width * 60000).toLong()

                                if (mediaPlayer.time + jumpMs <= 0) {
                                    jumpMs = -mediaPlayer.time
                                } else if (mediaPlayer.time + jumpMs >= mediaPlayer.length) {
                                    jumpMs = mediaPlayer.length - mediaPlayer.time - 1
                                }

                                dragDoneJob = lifecycleScope.launch {
                                    delay(centerMessageHideDelayMs)
                                    dragCounter = 0
                                    doPlayCheck = true
                                    mediaPlayer.time += jumpMs
                                    jumpMs = 0
                                    if (dragWasPlaying) mediaPlayer.play()
                                }
                            }
                        }
                    }
                }

                return true
            }
        })

        val sliderEventListener =
            object : Slider.OnSliderTouchListener, Slider.OnChangeListener, LabelFormatter {
                var lastSeekTimePosition = 0L
                var lastSeekIsPlaying = false
                var seekingTimePosition = 0L

                override fun onStartTrackingTouch(slider: Slider) {
                    lastSeekTimePosition = mediaPlayer.time
                    lastSeekIsPlaying = mediaPlayer.isPlaying

                    if (mediaPlayer.isPlaying) mediaPlayer.pause()

                    cancelScheduleHideControl()
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    if (mediaPlayer.isSeekable) {
                        mediaPlayer.position = slider.value / maxStep
                    }

                    scheduleHideControl()

                    if (lastSeekIsPlaying) mediaPlayer.play()
                }

                override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
                    if (mediaPlayer.isSeekable && fromUser) {
                        seekingTimePosition = (mediaPlayer.length * value / maxStep).toLong()
                        binding.txtCurrentTime.text =
                            StringUtil.longMillisToTimeStamp(seekingTimePosition)
                    }
                }

                override fun getFormattedValue(value: Float): String {
                    val seekingTimeStr = StringUtil.longMillisToTimeStamp(seekingTimePosition)
                    val timeDelta = seekingTimePosition - lastSeekTimePosition
                    val timeDeltaStr =
                        if (timeDelta >= 0) {
                            " | +".plus(StringUtil.longMillisToTimeStamp(timeDelta))
                        } else {
                            " | -".plus(StringUtil.longMillisToTimeStamp(abs(timeDelta)))
                        }
                    return seekingTimeStr.plus(timeDeltaStr)
                }

            }

        binding.slider.addOnSliderTouchListener(sliderEventListener)
        binding.slider.setLabelFormatter(sliderEventListener)
        binding.slider.addOnChangeListener(sliderEventListener)

        mediaPlayer.setEventListener {
            when (it.type) {
                MediaPlayer.Event.Buffering -> {
                    if (it.buffering < 100) {
                        if (!binding.progressBuffering.isVisible) {
                            binding.progressBuffering.visibility = View.VISIBLE
                        }

                        binding.slider.value = mediaPlayer.position * maxStep
                        binding.txtCurrentTime.text =
                            StringUtil.longMillisToTimeStamp(mediaPlayer.time)
                    } else {
                        binding.progressBuffering.visibility = View.GONE
                    }
                }
                MediaPlayer.Event.Playing -> {
                    binding.btnPlayPause.setImageDrawable(
                        ContextCompat.getDrawable(applicationContext, R.drawable.ic_media_pause)
                    )

                    if (binding.imgThumbnail.isVisible) {
                        binding.imgThumbnail.visibility = View.GONE
                    }

                    mediaPlayer.media?.apply {
                        getMeta(IMedia.Meta.Title)?.let {
                            val titleText = title?.let { "$title | $it" } ?: it
                            if (titleText != binding.txtTitle.text) {
                                binding.txtTitle.text = titleText
                            }
                        }
                    }

                    mediaPlayer.getChapters(mediaPlayer.title)?.let {
                        chapters = it.asList()

                        binding.txtChapter.isVisible = true
                        binding.btnChapterList.isVisible = true
                        binding.btnNextChapter.isVisible = true
                        binding.btnPrevChapter.isVisible = true

                        chapters?.forEachIndexed { index, chapter ->
                            val item = layoutInflater.inflate(R.layout.item_chapter, null)
                            item.findViewById<TextView>(R.id.txtChapterList).text = chapter.name
                            item.setOnClickListener {
                                mediaPlayer.chapter = index
                                binding.linChapterListBox.isVisible = false
                            }

                            binding.linChapterListHost.addView(item)
                        }

                        binding.txtChapter.setOnClickListener {
                            binding.linChapterListBox.isVisible = true
                        }

                        binding.btnChapterList.setOnClickListener {
                            binding.linChapterListBox.isVisible = true
                        }
                    }

                    scheduleHideControl()
                }
                MediaPlayer.Event.Paused -> {
                    binding.btnPlayPause.setImageDrawable(
                        ContextCompat.getDrawable(applicationContext, R.drawable.ic_media_play)
                    )
                }
                MediaPlayer.Event.EndReached -> {
                    finish()
                }
                MediaPlayer.Event.SeekableChanged -> {
                    binding.slider.isEnabled = it.seekable
                }
                MediaPlayer.Event.EncounteredError -> {
                    Snackbar.make(
                        binding.root,
                        "Encountered error while playing media!",
                        Snackbar.LENGTH_INDEFINITE
                    ).show()
                }
                MediaPlayer.Event.TimeChanged -> {
                    binding.txtCurrentTime.text = StringUtil.longMillisToTimeStamp(it.timeChanged)
                }
                MediaPlayer.Event.PositionChanged -> {
                    binding.slider.value = it.positionChanged * maxStep

                    chapters?.let {
                        val currentChapter = it[mediaPlayer.chapter].name
                        with(binding.txtChapter) {
                            text?.let {
                                if (it != currentChapter) text = currentChapter
                            }
                        }
                    }
                }
                MediaPlayer.Event.LengthChanged -> {
                    binding.txtTotalTime.text = StringUtil.longMillisToTimeStamp(it.lengthChanged)
                }
            }
        }
    }

    private fun showControl() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.controlTop, "y", 0F),
                ObjectAnimator.ofFloat(
                    binding.controlBottom, "y",
                    (binding.controlBottom.rootView.height - binding.controlBottom.height).toFloat()
                ),
                ObjectAnimator.ofFloat(binding.controlTop, "alpha", 1F),
                ObjectAnimator.ofFloat(binding.controlBottom, "alpha", 1F)
            )
            duration = uiAnimationDurationMs
            interpolator = DecelerateInterpolator()
            doOnEnd {
                isControlVisible = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(true)
                    window.insetsController?.show(
                        WindowInsets.Type.statusBars() or
                                WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("Deprecation")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility xor
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION xor
                                View.SYSTEM_UI_FLAG_FULLSCREEN
                }
            }
            start()
        }
    }

    private fun hideControl() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(
                    binding.controlTop, "y",
                    -binding.controlTop.height.toFloat()
                ),
                ObjectAnimator.ofFloat(
                    binding.controlBottom, "y",
                    (binding.controlBottom.rootView.height + binding.controlBottom.height).toFloat()
                ),
                ObjectAnimator.ofFloat(binding.controlTop, "alpha", 0F),
                ObjectAnimator.ofFloat(binding.controlBottom, "alpha", 0F)
            )
            duration = uiAnimationDurationMs
            doOnEnd {
                isControlVisible = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false)
                    window.insetsController?.hide(
                        WindowInsets.Type.statusBars() or
                                WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("Deprecation")
                    window.decorView.systemUiVisibility =
                        View.SYSTEM_UI_FLAG_IMMERSIVE xor
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION xor
                                View.SYSTEM_UI_FLAG_FULLSCREEN
                }
            }
            start()
        }
    }

    private fun scheduleHideControl() {
        cancelScheduleHideControl()

        uiHideSchedulerJob = lifecycleScope.launch {
            delay(uiHideDelayMs)
            hideControl()
        }
    }

    private fun cancelScheduleHideControl() {
        uiHideSchedulerJob?.let {
            if (!it.isCompleted) it.cancel()
        }
    }

    private fun toggleControl() {
        Log.d("SDKSDK", "toggleControl: TOGGLE CONTROL: $isControlVisible")
        if (isControlVisible) {
            hideControl()
        } else {
            showControl()
            if (mediaPlayer.isPlaying) scheduleHideControl()
        }
    }

    private fun displayCenterMessage(text: String) {
        centerMessageJob?.cancel()
        binding.txtCenterMessage.text = text

        if (!binding.txtCenterMessage.isVisible) {
            binding.txtCenterMessage.visibility = View.VISIBLE
        }

        centerMessageJob = lifecycleScope.launch {
            delay(centerMessageHideDelayMs)
            binding.txtCenterMessage.visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        mediaPlayer.attachViews(binding.vlcVideoLayout, null, true, false)
        if (!mediaPlayer.isPlaying) mediaPlayer.play()
    }

    override fun onStop() {
        super.onStop()
        mediaPlayer.detachViews()
        mediaPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.release()
        libVLC.release()
    }
}