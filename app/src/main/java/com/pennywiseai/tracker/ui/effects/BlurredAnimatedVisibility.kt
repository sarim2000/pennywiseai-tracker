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
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.pennywiseai.tracker.ui.animations.pwFadeIn
import com.pennywiseai.tracker.ui.animations.pwFadeOut

@Composable
fun BlurredAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = pwFadeIn(),
    exit: ExitTransition = pwFadeOut(),
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = updateTransition(
        targetState = visible,
        label = "blurTransition"
    )

    val motion = MaterialTheme.motionScheme
    val blurRadius by transition.animateFloat(
        label = "blurRadius",
        transitionSpec = { motion.defaultEffectsSpec() }
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
        // Below Android 12, use a custom RenderScript blur implementation
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
    // Both hold native resources the GC doesn't free for us. The bitmap is left to the
    // GC: a display list recorded earlier may still reference it.
    DisposableEffect(renderScript, scriptBlur) {
        onDispose {
            scriptBlur.destroy()
            renderScript.destroy()
        }
    }

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
