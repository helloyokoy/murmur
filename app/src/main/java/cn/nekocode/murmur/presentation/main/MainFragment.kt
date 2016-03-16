package cn.nekocode.murmur.presentation.main

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.support.v7.graphics.Palette
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.*
import butterknife.bindView
import cn.nekocode.kotgo.component.presentation.BaseFragment

import cn.nekocode.murmur.R
import cn.nekocode.murmur.data.dto.DoubanSong
import cn.nekocode.murmur.data.dto.Murmur
import cn.nekocode.murmur.util.CircleTransform
import cn.nekocode.murmur.util.ImageUtil
import cn.nekocode.murmur.view.ShaderRenderer
import com.pnikosis.materialishprogress.ProgressWheel
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.dip
import org.jetbrains.anko.sp
import org.jetbrains.anko.textColor
import kotlin.properties.Delegates

class MainFragment: BaseFragment(), MainPresenter.ViewInterface, View.OnTouchListener {
    override val layoutId: Int = R.layout.fragment_main
    val presenter = MainPresenter(this)

    val surfaceView: GLSurfaceView by bindView(R.id.surfaceView)
    var renderer: ShaderRenderer by Delegates.notNull<ShaderRenderer>()

    val backgroundView: View by bindView(R.id.relativeLayout)
    val coverImageView: ImageSwitcher by bindView(R.id.coverImageView)
    val progressWheel: ProgressWheel by bindView(R.id.progressWheel)
    val titleTextView: TextView by bindView(R.id.titleTextView)
    val performerTextView: TextView by bindView(R.id.performerTextView)
    val murmursTextView: TextView by bindView(R.id.murmursTextView)
    val timeTextView: TextView by bindView(R.id.timeTextView)

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGLSufaceview()
        setupCoverView()

        oldBackgroundColor = resources.getColor(R.color.color_primary)
        oldTextColor = Color.WHITE

        presenter.init()
    }

    private fun setupGLSufaceview() {
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setOnTouchListener(this)

        val shader = resources.openRawResource(R.raw.shader).reader().readText()

        renderer = ShaderRenderer(activity, shader)
        renderer.setBackColor(resources.getColor(R.color.color_primary_dark))
        surfaceView.setRenderer(renderer)
    }

    private fun setupCoverView() {
        coverImageView.setFactory {
            val imageView = ImageView(activity)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT)
            imageView
        }

        coverImageView.inAnimation = AnimationUtils.loadAnimation(activity, android.R.anim.fade_in)
        coverImageView.outAnimation = AnimationUtils.loadAnimation(activity, android.R.anim.fade_out)
        coverImageView.inAnimation.duration = 500
        coverImageView.outAnimation.duration = 500
        coverImageView.setImageResource(R.drawable.transparent)
    }

    override fun murmursChange(murmurs: List<Murmur>) {
        var text = ""
        val last = murmurs.lastOrNull()
        murmurs.forEach {
            text += it.name
            if(it != last) text += ", "
        }
        murmursTextView.text = text
    }

    override fun songChange(song: DoubanSong) {
        titleTextView.text = song.title
        performerTextView.text = song.artist
        timeTextView.text = song.length.toString()

        Picasso.with(activity).load(song.picture).transform(CircleTransform()).into(object: Target {
            override fun onPrepareLoad(drawable: Drawable?) {
                coverImageView.setImageResource(R.drawable.transparent)
            }

            override fun onBitmapFailed(drawable: Drawable?) {
                coverImageView.setImageResource(R.drawable.transparent)
            }

            override fun onBitmapLoaded(bitmap: Bitmap?, p1: Picasso.LoadedFrom?) {
                if(bitmap != null) {
                    switchPalette(bitmap)
                }
                coverImageView.setImageDrawable(ImageUtil.bitmap2Drawable(bitmap))
            }

        })

        progressWheel.visibility = View.INVISIBLE
    }

    var oldBackgroundColor = 0
    var oldTextColor = 0
    var backgroundColorAnimator: ValueAnimator? = null
    var textColorAnimator: ValueAnimator? = null

    private fun switchPalette(bitmap: Bitmap) {
        Palette.from(bitmap).generate {
            var swatch: Palette.Swatch? = null

            while(swatch == null) {
                swatch = it.darkVibrantSwatch
                if(swatch != null)
                    break

                swatch = it.vibrantSwatch
                if(swatch != null)
                    break

                swatch = it.darkMutedSwatch
                if(swatch != null)
                    break

                swatch = it.lightMutedSwatch
                if(swatch != null)
                    break
            }
            swatch!!

            backgroundColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), oldBackgroundColor, swatch.rgb)
            backgroundColorAnimator?.duration = 500
            backgroundColorAnimator?.interpolator = LinearInterpolator()
            backgroundColorAnimator?.addUpdateListener {
                val color = it.animatedValue as Int

                backgroundView.backgroundColor = color
                renderer.setBackColor(color)
                if (Build.VERSION.SDK_INT >= 21) {
                    activity.window.statusBarColor = color
                }

                oldBackgroundColor = color
            }
            backgroundColorAnimator?.start()

            textColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), oldTextColor, swatch.titleTextColor)
            textColorAnimator?.duration = 500
            textColorAnimator?.interpolator = LinearInterpolator()
            textColorAnimator?.addUpdateListener {
                val color = it.animatedValue as Int

                titleTextView.textColor = color
                performerTextView.textColor = color
                murmursTextView.textColor = color
                timeTextView.textColor = color

                oldTextColor = color
            }
            textColorAnimator?.start()
        }
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    val gestureDetector by lazy {
        GestureDetector(activity, object: GestureDetector.OnGestureListener {
            val FLING_MIN_DISTANCE = dip(100)
            val FLING_MIN_DISTANCE_Y = dip(150)
            val FLING_MIN_VELOCITY = 1

            var lastestTapTime = 0L
            override fun onSingleTapUp(p0: MotionEvent?): Boolean {
                val nowTapTime = System.currentTimeMillis()
                if (nowTapTime - lastestTapTime < 800) {
                    Toast.makeText(activity, "Double tap", Toast.LENGTH_SHORT).show()

                    lastestTapTime = 0
                    return false
                }

                lastestTapTime = nowTapTime
                return true
            }

            override fun onDown(p0: MotionEvent?): Boolean {
                return true
            }

            override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (Math.abs(e1.y - e2.y) > FLING_MIN_DISTANCE_Y)
                    return false

                if (e1.x - e2.x > FLING_MIN_DISTANCE
                        && Math.abs(velocityX) > FLING_MIN_VELOCITY) {
                    //向右滑动
                    progressWheel.visibility = View.VISIBLE
                    presenter.nextSong()
                } else if (e2.x - e1.x > FLING_MIN_DISTANCE
                        && Math.abs(velocityX) > FLING_MIN_VELOCITY) {
                    //向左滑动
                    progressWheel.visibility = View.VISIBLE
                    presenter.nextSong()
                }

                return false
            }

            override fun onScroll(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
                return true
            }

            override fun onShowPress(p0: MotionEvent?) {
            }

            override fun onLongPress(p0: MotionEvent?) {
            }

        })
    }
}
