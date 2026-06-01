package com.baysoft.gallerywall.provider

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * A beautiful synthetic procedural tile generator that simulates an ML generator.
 * Uses a seeded random generator and prompt keywords to render stunning retro tiles.
 */
object NoiseGenerator {

    /**
     * Generates a perfectly tileable square pattern tile based on prompt tags and active colors.
     */
    fun generateSeamlessTile(
        size: Int,
        colors: List<Int>,
        prompt: String = "",
        seed: Int = -1
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Seeded random number generator
        val actualSeed = if (seed == -1) (0..Int.MAX_VALUE).random() else seed
        val random = java.util.Random(actualSeed.toLong())

        // Map colors from list safely, providing fallbacks if the user's palette is small
        val c0 = colors.getOrNull(0) ?: Color.parseColor("#6750A4")
        val c1 = colors.getOrNull(1) ?: colors.getOrNull(0) ?: Color.parseColor("#625B71")
        val c2 = colors.getOrNull(2) ?: colors.getOrNull(0) ?: Color.parseColor("#7D5260")
        
        // Background color
        canvas.drawColor(c0)

        // Detect prompt categories
        val lowerPrompt = prompt.lowercase()
        val isFlower = lowerPrompt.contains("flower") || lowerPrompt.contains("floral") || lowerPrompt.contains("daisy") || lowerPrompt.contains("rose") || lowerPrompt.contains("petal") || lowerPrompt.contains("blossom")
        val isStar = lowerPrompt.contains("star") || lowerPrompt.contains("night") || lowerPrompt.contains("space") || lowerPrompt.contains("sky") || lowerPrompt.contains("spark") || lowerPrompt.contains("magic")
        val isHeart = lowerPrompt.contains("heart") || lowerPrompt.contains("love") || lowerPrompt.contains("romantic")
        val isPaw = lowerPrompt.contains("cat") || lowerPrompt.contains("dog") || lowerPrompt.contains("paw") || lowerPrompt.contains("kitten") || lowerPrompt.contains("animal")
        val isLeaf = lowerPrompt.contains("leaf") || lowerPrompt.contains("leaves") || lowerPrompt.contains("botanical") || lowerPrompt.contains("plant")
        val isStripe = lowerPrompt.contains("stripe") || lowerPrompt.contains("stripes") || lowerPrompt.contains("line") || lowerPrompt.contains("lines")
        val isCheck = lowerPrompt.contains("check") || lowerPrompt.contains("plaid") || lowerPrompt.contains("chess") || lowerPrompt.contains("grid")

        val offsets = listOf(-size, 0, size)

        when {
            isFlower -> {
                // RENDER FLOWERS
                val flowerCount = 3 + random.nextInt(4)
                for (i in 0 until flowerCount) {
                    val cx = random.nextFloat() * size
                    val cy = random.nextFloat() * size
                    val radius = (size / 14f) + random.nextFloat() * (size / 10f)

                    for (ox in offsets) {
                        for (oy in offsets) {
                            val fx = cx + ox
                            val fy = cy + oy
                            
                            // Center circle
                            paint.color = c1
                            paint.style = Paint.Style.FILL
                            canvas.drawCircle(fx, fy, radius * 0.4f, paint)

                            // 5 Petals
                            paint.color = c2
                            val petalStep = (2.0 * Math.PI) / 5
                            for (p in 0 until 5) {
                                val angle = p * petalStep
                                val px = fx + (radius * 0.75f * Math.cos(angle)).toFloat()
                                val py = fy + (radius * 0.75f * Math.sin(angle)).toFloat()
                                canvas.drawCircle(px, py, radius * 0.35f, paint)
                            }
                        }
                    }
                }
            }
            
            isStar -> {
                // RENDER STARS & MAGIC DUST
                paint.style = Paint.Style.FILL
                paint.color = c1
                val dotCount = 15 + random.nextInt(15)
                for (i in 0 until dotCount) {
                    val cx = random.nextFloat() * size
                    val cy = random.nextFloat() * size
                    val radius = size / 128f + random.nextFloat() * (size / 64f)
                    for (ox in offsets) {
                        for (oy in offsets) {
                            canvas.drawCircle(cx + ox, cy + oy, radius, paint)
                        }
                    }
                }

                // Draw elegant four-pointed stars
                paint.color = c2
                val starCount = 4 + random.nextInt(5)
                for (i in 0 until starCount) {
                    val cx = random.nextFloat() * size
                    val cy = random.nextFloat() * size
                    val radius = (size / 12f) + random.nextFloat() * (size / 10f)

                    for (ox in offsets) {
                        for (oy in offsets) {
                            val fx = cx + ox
                            val fy = cy + oy
                            
                            val path = android.graphics.Path().apply {
                                moveTo(fx, fy - radius)
                                quadTo(fx, fy, fx + radius, fy)
                                quadTo(fx, fy, fx, fy + radius)
                                quadTo(fx, fy, fx - radius, fy)
                                quadTo(fx, fy, fx, fy - radius)
                                close()
                            }
                            canvas.drawPath(path, paint)
                        }
                    }
                }
            }

            isHeart -> {
                // RENDER HEARTS
                paint.color = c1
                paint.strokeWidth = size / 96f
                paint.style = Paint.Style.STROKE
                val pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 15f), 0f)
                paint.pathEffect = pathEffect
                canvas.drawLine(0f, 0f, size.toFloat(), size.toFloat(), paint)
                canvas.drawLine(0f, size.toFloat(), size.toFloat(), 0f, paint)
                paint.pathEffect = null

                // Draw procedural tiling hearts
                paint.style = Paint.Style.FILL
                paint.color = c2
                val heartCount = 3 + random.nextInt(4)
                for (i in 0 until heartCount) {
                    val cx = random.nextFloat() * size
                    val cy = random.nextFloat() * size
                    val radius = (size / 14f) + random.nextFloat() * (size / 10f)

                    for (ox in offsets) {
                        for (oy in offsets) {
                            val fx = cx + ox
                            val fy = cy + oy
                            
                            val path = android.graphics.Path().apply {
                                moveTo(fx, fy - radius / 3)
                                cubicTo(fx - radius, fy - radius, fx - radius, fy + radius / 3, fx, fy + radius)
                                cubicTo(fx + radius, fy + radius / 3, fx + radius, fy - radius, fx, fy - radius / 3)
                                close()
                            }
                            canvas.drawPath(path, paint)
                        }
                    }
                }
            }

            isPaw -> {
                // RENDER ANIMAL PAW PRINTS
                paint.style = Paint.Style.FILL
                val pawCount = 3 + random.nextInt(4)
                for (i in 0 until pawCount) {
                    val cx = random.nextFloat() * size
                    val cy = random.nextFloat() * size
                    val radius = (size / 18f) + random.nextFloat() * (size / 14f)

                    for (ox in offsets) {
                        for (oy in offsets) {
                            val fx = cx + ox
                            val fy = cy + oy
                            
                            // Main large pad
                            paint.color = c2
                            canvas.drawCircle(fx, fy + radius * 0.25f, radius * 0.55f, paint)
                            
                            // 4 toe pads
                            paint.color = c1
                            canvas.drawCircle(fx - radius * 0.45f, fy - radius * 0.1f, radius * 0.22f, paint)
                            canvas.drawCircle(fx - radius * 0.18f, fy - radius * 0.4f, radius * 0.22f, paint)
                            canvas.drawCircle(fx + radius * 0.18f, fy - radius * 0.4f, radius * 0.22f, paint)
                            canvas.drawCircle(fx + radius * 0.45f, fy - radius * 0.1f, radius * 0.22f, paint)
                        }
                    }
                }
            }

            isLeaf -> {
                // RENDER BOTANICAL LEAVES
                paint.style = Paint.Style.FILL
                val leafCount = 4 + random.nextInt(4)
                for (i in 0 until leafCount) {
                    val cx = random.nextFloat() * size
                    val cy = random.nextFloat() * size
                    val radius = (size / 12f) + random.nextFloat() * (size / 9f)
                    val rotation = random.nextFloat() * 360f

                    for (ox in offsets) {
                        for (oy in offsets) {
                            val fx = cx + ox
                            val fy = cy + oy
                            
                            canvas.save()
                            canvas.rotate(rotation, fx, fy)
                            
                            // Draw leaf shape
                            val path = android.graphics.Path().apply {
                                moveTo(fx - radius, fy)
                                quadTo(fx, fy - radius * 0.4f, fx + radius, fy)
                                quadTo(fx, fy + radius * 0.4f, fx - radius, fy)
                                close()
                            }
                            paint.color = c2
                            canvas.drawPath(path, paint)
                            
                            // Leaf vein
                            paint.color = c1
                            paint.strokeWidth = size / 128f
                            paint.style = Paint.Style.STROKE
                            canvas.drawLine(fx - radius, fy, fx + radius, fy, paint)
                            
                            canvas.restore()
                            paint.style = Paint.Style.FILL
                        }
                    }
                }
            }

            isStripe -> {
                // RENDER STRIPES
                paint.color = c1
                paint.style = Paint.Style.STROKE
                val lineCount = 6 + random.nextInt(6)
                val stripeStep = (size * 2f) / lineCount
                paint.strokeWidth = stripeStep * 0.35f
                for (i in 0..lineCount) {
                    val offset = i * stripeStep
                    canvas.drawLine(offset - size, 0f, offset, size.toFloat(), paint)
                }
                
                // Draw high contrast accent stripes
                paint.color = c2
                paint.strokeWidth = stripeStep * 0.08f
                for (i in 0..lineCount) {
                    val offset = i * stripeStep + (stripeStep * 0.25f)
                    canvas.drawLine(offset - size, 0f, offset, size.toFloat(), paint)
                }
            }

            isCheck -> {
                // RENDER PLAID / CHESS CHECKBOARD
                val divisions = 4 + random.nextInt(3) * 2
                val checkStep = size.toFloat() / divisions
                for (row in 0 until divisions) {
                    for (col in 0 until divisions) {
                        paint.color = if ((row + col) % 2 == 0) c1 else c2
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(col * checkStep, row * checkStep, (col + 1) * checkStep, (row + 1) * checkStep, paint)
                    }
                }
            }

            else -> {
                // DEFAULT CHIC RETRO ABSTRACT SHAPES
                paint.color = c1
                paint.strokeWidth = size / 48f
                paint.style = Paint.Style.STROKE
                
                val divisions = 2 + random.nextInt(2) * 2
                val checkStep = size.toFloat() / divisions
                for (i in 1 until divisions) {
                    canvas.drawLine(i * checkStep, 0f, i * checkStep, size.toFloat(), paint)
                    canvas.drawLine(0f, i * checkStep, size.toFloat(), i * checkStep, paint)
                }

                paint.style = Paint.Style.FILL
                val shapeCount = 3 + random.nextInt(4)
                for (i in 0 until shapeCount) {
                    val cx = random.nextFloat() * size
                    val cy = random.nextFloat() * size
                    val radius = (size / 14f) + random.nextFloat() * (size / 10f)

                    for (ox in offsets) {
                        for (oy in offsets) {
                            val fx = cx + ox
                            val fy = cy + oy
                            
                            paint.color = c2
                            canvas.drawCircle(fx, fy, radius, paint)
                            
                            paint.color = c1
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = size / 64f
                            canvas.drawCircle(fx, fy, radius * 1.3f, paint)
                            paint.style = Paint.Style.FILL
                        }
                    }
                }
            }
        }

        // Draw a second layer of smaller details with high contrast
        paint.color = c1
        val detailCount = 5 + random.nextInt(10)
        for (i in 0 until detailCount) {
            val radius = size / 48f + random.nextFloat() * (size / 32f)
            val cx = random.nextFloat() * size
            val cy = random.nextFloat() * size
            
            val offsets = listOf(-size, 0, size)
            for (ox in offsets) {
                for (oy in offsets) {
                    canvas.drawCircle(cx + ox, cy + oy, radius, paint)
                }
            }
        }

        return bitmap
    }
}
