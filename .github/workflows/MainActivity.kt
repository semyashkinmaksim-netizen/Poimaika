package com.example.wolfeggs

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

// ---------- Игровые объекты ----------
class Egg(var x: Float, var y: Float) {
    val radius = 28f
    var speed = 0f
}

class Wolf(var x: Float, var y: Float) {
    val w = 140f
    val h = 110f
    val basketW = 130f
    val basketH = 45f

    // прямоугольник корзины (зона ловли)
    fun basketRect(): RectF = RectF(
        x - basketW / 2,
        y + h / 2,
        x + basketW / 2,
        y + h / 2 + basketH
    )

    fun boundsRect(): RectF = RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null
    @Volatile private var running = false

    private var wolf = Wolf(0f, 0f)
    private val eggs = mutableListOf<Egg>()
    private var score = 0
    private var lives = 3
    private var gameOver = false
    private var spawnTimer = 0f
    private var screenW = 0f
    private var screenH = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // кисти
    private val wolfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 120, 130) }
    private val wolfBellyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 200, 210) }
    private val basketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 90, 40) }
    private val eggPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 245, 200) }
    private val eggStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(180, 160, 90); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 55f; textAlign = Paint.Align.LEFT
    }
    private val bigTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 90f; textAlign = Paint.Align.CENTER
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenW = width.toFloat()
        screenH = height.toFloat()
        wolf.x = screenW / 2
        wolf.y = screenH - 200f
        running = true
        thread = Thread(this).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        screenW = w.toFloat(); screenH = h.toFloat()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join()
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            update(dt)
            draw()
            Thread.sleep(16)
        }
    }

    private fun update(dt: Float) {
        if (gameOver) return

        // спавн яиц
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            val diff = score / 10f
            eggs.add(Egg(Random.nextFloat() * (screenW - 80) + 40, -40f).apply {
                speed = 260f + Random.nextFloat() * 150f + diff * 40f
            })
            spawnTimer = (1.0f - (score / 100f)).coerceAtLeast(0.35f)
        }

        val iter = eggs.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.y += e.speed * dt
            val basket = wolf.basketRect()
            if (e.y + e.radius >= basket.top && e.y <= basket.bottom &&
                e.x >= basket.left && e.x <= basket.right) {
                score++
                iter.remove()
            } else if (e.y - e.radius > screenH) {
                iter.remove()
                lives--
                if (lives <= 0) gameOver = true
            }
        }
    }

    private fun draw() {
        val holder = holder
        if (holder.surface == null || !holder.surface.isValid) return
        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.rgb(40, 120, 60)) // трава

            // волк
            val wr = wolf.boundsRect()
            canvas.drawRoundRect(wr, 30f, 30f, wolfPaint)
            // живот
            canvas.drawRoundRect(
                RectF(wr.left + 20, wr.top + 30, wr.right - 20, wr.bottom), 20f, 20f, wolfBellyPaint
            )
            // глаза
            val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
            canvas.drawCircle(wolf.x - 25, wolf.y - 20, 12f, eyePaint)
            canvas.drawCircle(wolf.x + 25, wolf.y - 20, 12f, eyePaint)
            canvas.drawCircle(wolf.x - 25, wolf.y - 20, 6f, pupilPaint)
            canvas.drawCircle(wolf.x + 25, wolf.y - 20, 6f, pupilPaint)

            // корзина
            val basket = wolf.basketRect()
            canvas.drawRoundRect(basket, 12f, 12f, basketPaint)

            // яйца
            for (e in eggs) {
                val oval = RectF(e.x - e.radius * 0.8f, e.y - e.radius, e.x + e.radius * 0.8f, e.y + e.radius)
                canvas.drawOval(oval, eggPaint)
                canvas.drawOval(oval, eggStroke)
            }

            // счёт / жизни
            canvas.drawText("Счёт: $score", 30f, 70f, textPaint)
            canvas.drawText("Жизни: ${"❤".repeat(lives.coerceAtLeast(0))}", 30f, 140f, textPaint)

            // game over
            if (gameOver) {
                canvas.drawColor(Color.argb(180, 0, 0, 0))
                canvas.drawText("GAME OVER", screenW / 2, screenH / 2 - 60, bigTextPaint)
                canvas.drawText("Счёт: $score", screenW / 2, screenH / 2 + 60, bigTextPaint)
                canvas.drawText("Коснись экрана", screenW / 2, screenH / 2 + 160,
                    Paint(textPaint).apply { textAlign = Paint.Align.CENTER; textSize = 50f })
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (gameOver) {
                    if (event.action == MotionEvent.ACTION_DOWN) restart()
                } else {
                    wolf.x = event.x.coerceIn(wolf.w / 2, screenW - wolf.w / 2)
                    wolf.y = (event.y - 50f).coerceIn(screenH - 400f, screenH - 80f)
                }
            }
        }
        return true
    }

    private fun restart() {
        eggs.clear()
        score = 0
        lives = 3
        gameOver = false
        spawnTimer = 0f
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(GameView(this))
    }
}
