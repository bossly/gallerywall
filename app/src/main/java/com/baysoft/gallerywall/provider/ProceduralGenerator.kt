package com.baysoft.gallerywall.provider

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * A beautiful premium procedural tile generator.
 * Uses a seeded random generator and prompt keywords to render stunning retro geometric and organic tiles.
 */
object ProceduralGenerator {

    /**
     * Generates periodic vertex noise for organic flagstones that wraps perfectly at boundaries.
     */
    private fun getVertexNoise(gx: Int, gy: Int, divisions: Int, maxOffset: Float, seed: Long): Pair<Float, Float> {
        val wx = (gx % divisions + divisions) % divisions
        val wy = (gy % divisions + divisions) % divisions
        val r = java.util.Random(wx * 73L + wy * 199L + seed)
        val dx = (r.nextFloat() - 0.5f) * maxOffset
        val dy = (r.nextFloat() - 0.5f) * maxOffset
        return Pair(dx, dy)
    }

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
        val c0 = colors.getOrNull(0) ?: Color.parseColor("#4A2840") // Dark background color
        val c1 = colors.getOrNull(1) ?: colors.getOrNull(0) ?: Color.parseColor("#B05C85") // Secondary contrast
        val c2 = colors.getOrNull(2) ?: colors.getOrNull(0) ?: Color.parseColor("#E09CB5") // Main highlight

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
        
        // Premium Memphis & Abstract Tiled Styles
        val isArch = lowerPrompt.contains("arch") || lowerPrompt.contains("column") || lowerPrompt.contains("tunnel") || lowerPrompt.contains("monument")
        val isStone = lowerPrompt.contains("stone") || lowerPrompt.contains("tile") || lowerPrompt.contains("clay") || lowerPrompt.contains("puzzle") || lowerPrompt.contains("flagstone") || lowerPrompt.contains("ground")
        val isCheck = lowerPrompt.contains("check") || lowerPrompt.contains("plaid") || lowerPrompt.contains("chess") || lowerPrompt.contains("grid")

        val offsets = listOf(-size, 0, size)

        when {
            isArch -> {
                // PREMIUM STYLE 1: INTERLOCKING COLUMNS & ARCHWAYS (Wallpaper 2 style)
                val divisions = 3
                val step = size.toFloat() / divisions
                val cellSize = step
                
                for (row in 0 until divisions) {
                    for (col in 0 until divisions) {
                        val cellSeed = (row * 13L + col * 37L + actualSeed).toLong()
                        val cellRand = java.util.Random(cellSeed)
                        
                        val cx = col * step + step / 2f
                        val cy = row * step + step / 2f
                        
                        val stoneColor = if ((row + col) % 2 == 0) c1 else c2
                        
                        for (ox in offsets) {
                            for (oy in offsets) {
                                val fx = cx + ox
                                val fy = cy + oy
                                
                                // Base column
                                paint.color = stoneColor
                                paint.style = Paint.Style.FILL
                                canvas.drawRect(fx - cellSize * 0.25f, fy - cellSize * 0.25f, fx + cellSize * 0.25f, fy + cellSize * 0.5f, paint)
                                
                                // Column Arch Top
                                val arcRect = RectF(fx - cellSize * 0.25f, fy - cellSize * 0.5f, fx + cellSize * 0.25f, fy)
                                canvas.drawArc(arcRect, 180f, 180f, true, paint)
                                
                                // Inner Concentric Cutout (Negative space)
                                paint.color = c0
                                val innerRect = RectF(fx - cellSize * 0.12f, fy - cellSize * 0.15f, fx + cellSize * 0.12f, fy + cellSize * 0.5f)
                                canvas.drawRoundRect(innerRect, cellSize * 0.12f, cellSize * 0.12f, paint)
                                
                                val innerArc = RectF(fx - cellSize * 0.12f, fy - cellSize * 0.27f, fx + cellSize * 0.12f, fy - cellSize * 0.03f)
                                canvas.drawArc(innerArc, 180f, 180f, true, paint)
                            }
                        }
                    }
                }
            }

            isStone -> {
                // PREMIUM STYLE 2: ORGANIC FLAGSTONES / CLAY PUZZLE TILES (Wallpaper 6 style)
                val divisions = 4
                val step = size.toFloat() / divisions
                val maxOffset = step * 0.28f

                for (row in 0 until divisions) {
                    for (col in 0 until divisions) {
                        // Periodic noise vertices
                        val v0 = getVertexNoise(col, row, divisions, maxOffset, actualSeed.toLong())
                        val v1 = getVertexNoise(col + 1, row, divisions, maxOffset, actualSeed.toLong())
                        val v2 = getVertexNoise(col + 1, row + 1, divisions, maxOffset, actualSeed.toLong())
                        val v3 = getVertexNoise(col, row + 1, divisions, maxOffset, actualSeed.toLong())
                        
                        val p0x = col * step + v0.first
                        val p0y = row * step + v0.second
                        
                        val p1x = (col + 1) * step + v1.first
                        val p1y = row * step + v1.second
                        
                        val p2x = (col + 1) * step + v2.first
                        val p2y = (row + 1) * step + v2.second
                        
                        val p3x = col * step + v3.first
                        val p3y = (row + 1) * step + v3.second
                        
                        val stoneColor = if ((row + col) % 2 == 0) c1 else c2
                        
                        for (ox in offsets) {
                            for (oy in offsets) {
                                val path = Path().apply {
                                    moveTo(p0x + ox, p0y + oy)
                                    lineTo(p1x + ox, p1y + oy)
                                    lineTo(p2x + ox, p2y + oy)
                                    lineTo(p3x + ox, p3y + oy)
                                    close()
                                }
                                
                                // Draw stone fill
                                paint.color = stoneColor
                                paint.style = Paint.Style.FILL
                                canvas.drawPath(path, paint)
                                
                                // Draw dark stone boundary gap
                                paint.color = c0
                                paint.style = Paint.Style.STROKE
                                paint.strokeWidth = size / 64f
                                canvas.drawPath(path, paint)
                            }
                        }
                    }
                }
            }

            isCheck -> {
                // CHIC RETRO CHECKERS (Wallpaper 3 Style)
                val divisions = 4
                val checkStep = size.toFloat() / divisions
                for (row in 0 until divisions) {
                    for (col in 0 until divisions) {
                        paint.color = if ((row + col) % 2 == 0) c1 else c2
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(col * checkStep, row * checkStep, (col + 1) * checkStep, (row + 1) * checkStep, paint)
                        
                        // Draw alternating decorative circles inside checkers
                        if ((row + col) % 3 == 0) {
                            paint.color = c0
                            for (ox in offsets) {
                                for (oy in offsets) {
                                    canvas.drawCircle(col * checkStep + checkStep/2 + ox, row * checkStep + checkStep/2 + oy, checkStep * 0.25f, paint)
                                }
                            }
                        }
                    }
                }
            }

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
                            
                            val path = Path().apply {
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
                            
                            val path = Path().apply {
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
                            val path = Path().apply {
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

            else -> {
                // PREMIUM STYLE 3: MEMPHIS RETRO CHIC PATTERNS (Wallpaper 1 & 4 Style)
                val divisions = 3
                val step = size.toFloat() / divisions
                val cellSize = step * 0.88f // slight gap for elegant structure

                for (row in 0 until divisions) {
                    for (col in 0 until divisions) {
                        val cellSeed = (row * 31L + col * 17L + actualSeed).toLong()
                        val cellRand = java.util.Random(cellSeed)
                        
                        val cx = col * step + step / 2f
                        val cy = row * step + step / 2f
                        
                        val choice = cellRand.nextInt(5)
                        
                        for (ox in offsets) {
                            for (oy in offsets) {
                                val fx = cx + ox
                                val fy = cy + oy
                                
                                when (choice) {
                                    0 -> {
                                        // Pill capsule
                                        val width = cellSize * 0.44f
                                        val height = cellSize * 0.88f
                                        val rect = RectF(fx - width/2, fy - height/2, fx + width/2, fy + height/2)
                                        paint.color = c1
                                        paint.style = Paint.Style.FILL
                                        canvas.drawRoundRect(rect, width/2, width/2, paint)
                                        
                                        // Inner pill cutout in c2
                                        paint.color = c2
                                        val innerW = width * 0.45f
                                        val innerH = height * 0.65f
                                        val innerRect = RectF(fx - innerW/2, fy - innerH/2, fx + innerW/2, fy + innerH/2)
                                        canvas.drawRoundRect(innerRect, innerW/2, innerW/2, paint)
                                    }
                                    1 -> {
                                        // Diagonal triangle
                                        paint.color = c2
                                        paint.style = Paint.Style.FILL
                                        val path = Path().apply {
                                            moveTo(fx - cellSize/2, fy - cellSize/2)
                                            lineTo(fx + cellSize/2, fy - cellSize/2)
                                            lineTo(fx - cellSize/2, fy + cellSize/2)
                                            close()
                                        }
                                        canvas.drawPath(path, paint)
                                        
                                        // Capsule on contrasting corner
                                        paint.color = c1
                                        canvas.drawCircle(fx + cellSize/4, fy + cellSize/4, cellSize * 0.2f, paint)
                                    }
                                    2 -> {
                                        // Shield blob with circular negative cutout
                                        paint.color = c1
                                        paint.style = Paint.Style.FILL
                                        val rect = RectF(fx - cellSize/2, fy - cellSize/3, fx + cellSize/2, fy + cellSize/3)
                                        canvas.drawRoundRect(rect, cellSize/3, cellSize/3, paint)
                                        
                                        paint.color = c0
                                        canvas.drawCircle(fx, fy, cellSize * 0.18f, paint)
                                    }
                                    3 -> {
                                        // Curved archway
                                        paint.color = c2
                                        paint.style = Paint.Style.FILL
                                        val path = Path().apply {
                                            moveTo(fx - cellSize/3, fy + cellSize/2)
                                            lineTo(fx - cellSize/3, fy - cellSize/6)
                                            val arcRect = RectF(fx - cellSize/3, fy - cellSize/2, fx + cellSize/3, fy + cellSize/6)
                                            arcTo(arcRect, 180f, 180f, false)
                                            lineTo(fx + cellSize/3, fy + cellSize/2)
                                            close()
                                        }
                                        canvas.drawPath(path, paint)
                                        
                                        // Inner details
                                        paint.color = c1
                                        canvas.drawCircle(fx, fy - cellSize/8, cellSize * 0.14f, paint)
                                    }
                                    4 -> {
                                        // Overlapping contrasting capsules
                                        paint.color = c2
                                        paint.style = Paint.Style.FILL
                                        val w1 = cellSize * 0.32f
                                        val h1 = cellSize * 0.82f
                                        val rect1 = RectF(fx - w1/2 - cellSize/6, fy - h1/2, fx + w1/2 - cellSize/6, fy + h1/2)
                                        canvas.drawRoundRect(rect1, w1/2, w1/2, paint)
                                        
                                        paint.color = c1
                                        val w2 = cellSize * 0.32f
                                        val h2 = cellSize * 0.62f
                                        val rect2 = RectF(fx - w2/2 + cellSize/6, fy - h2/2 + cellSize/8, fx + w2/2 + cellSize/6, fy + h2/2 + cellSize/8)
                                        canvas.drawRoundRect(rect2, w2/2, w2/2, paint)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Draw a second layer of smaller details with high contrast
        paint.color = c1
        paint.style = Paint.Style.FILL
        val detailCount = 5 + random.nextInt(10)
        for (i in 0 until detailCount) {
            val radius = size / 48f + random.nextFloat() * (size / 32f)
            val cx = random.nextFloat() * size
            val cy = random.nextFloat() * size
            
            for (ox in offsets) {
                for (oy in offsets) {
                    canvas.drawCircle(cx + ox, cy + oy, radius, paint)
                }
            }
        }

        return bitmap
    }
}
