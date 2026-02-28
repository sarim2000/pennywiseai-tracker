package com.pennywiseai.tracker.ui.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize

@Composable
fun BlurredAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + scaleIn(),
    exit: ExitTransition = fadeOut() + scaleOut(),
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = updateTransition(
        targetState = visible,
        label = "blurTransition"
    )

    val blurRadius by transition.animateFloat(
        label = "blurRadius",
        transitionSpec = { tween(durationMillis = 300) }
    ) { state ->
        if (state && transition.currentState == transition.targetState) 0f else 5f
    }

    // For Android 12+, use the native RenderEffect
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AnimatedVisibility(
            visible = visible,
            enter = enter,
            exit = exit,
            modifier = modifier
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    renderEffect = BlurEffect(
                        radiusX = blurRadius,
                        radiusY = blurRadius,
                        edgeTreatment = TileMode.Decal
                    )
                    alpha = 0.95f + (0.05f * (1f - blurRadius / 5f))
                }
            ) {
                content()
            }
        }
    } else {
        // For Android 10-11, use a custom RenderScript blur implementation
        AnimatedVisibility(
            visible = visible,
            enter = enter,
            exit = exit,
            modifier = modifier
        ) {
            CustomBlurEffect(blurRadius = blurRadius) {
                content()
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun CustomBlurEffect(
    blurRadius: Float,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val renderScript = remember { RenderScript.create(context) }
    val scriptBlur = remember { ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript)) }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                if (size != coordinates.size) {
                    size = coordinates.size
                    if (size.width > 0 && size.height > 0) {
                        bitmap?.recycle()
                        bitmap = Bitmap.createBitmap(
                            (size.width * 0.25f).toInt().coerceAtLeast(1),
                            (size.height * 0.25f).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                    }
                }
            }
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    if (blurRadius > 0f && bitmap != null && size.width > 0 && size.height > 0) {
                        try {
                            val scaleFactor = 0.25f
                            val calculatedRadius = (blurRadius * 5f).coerceIn(0.1f, 25f)

                            val canvas = Canvas(bitmap!!)
                            canvas.scale(scaleFactor, scaleFactor)

                            drawContext.canvas.nativeCanvas.save()
                            drawContext.canvas.nativeCanvas.drawBitmap(bitmap!!, 0f, 0f, null)
                            drawContext.canvas.nativeCanvas.restore()

                            val input = Allocation.createFromBitmap(
                                renderScript,
                                bitmap!!,
                                Allocation.MipmapControl.MIPMAP_NONE,
                                Allocation.USAGE_SCRIPT
                            )
                            val output = Allocation.createTyped(renderScript, input.type)

                            scriptBlur.setRadius(calculatedRadius)
                            scriptBlur.setInput(input)
                            scriptBlur.forEach(output)
                            output.copyTo(bitmap!!)

                            drawImage(
                                bitmap!!.asImageBitmap(),
                                alpha = 0.95f + (0.05f * (1f - blurRadius / 5f))
                            )

                            input.destroy()
                            output.destroy()
                        } catch (e: Exception) {
                            Log.e("CustomBlurEffect", "Error applying blur", e)
                        }
                    }
                }
            }
    ) {
        content()
    }
}
