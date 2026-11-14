package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SpaceGroteskFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SplashObsidian = Color(0xFF081017)
private val SplashDeepTeal = Color(0xFF0E222E)
private val SplashEmerald = Color(0xFF34D399)
private val SplashInkPrimary = Color(0xFFF4F8FB)
private val SplashInkMuted = Color(0xFFA3B6C6)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BinCheckSplashOverlay(
    visible: Boolean,
    onFinished: () -> Unit,
    durationMillis: Long = 550L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val emblemScale = remember { Animatable(0.86f) }
    val contentAlpha = remember { Animatable(0f) }
    // Downsample the 1024x1024 splash JPEG to 512x512 RGB_565 to cut cold-start decode time and memory ~8x.
    val sampledLogoBitmap = remember(context) {
        runCatching {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 2
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.bincheck_app_icon_1794588466984,
                opts
            )?.asImageBitmap()
        }.getOrNull()
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val animJob = launch {
            launch {
                emblemScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                contentAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                )
            }
        }
        delay(durationMillis)
        animJob.join()
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
            scaleOut(
                targetScale = 1.03f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ),
        modifier = modifier
            .fillMaxSize()
            .zIndex(50f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SplashDeepTeal,
                            SplashObsidian
                        ),
                        radius = 1050f
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 28.dp)
                .testTag("splash_overlay")
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(contentAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .scale(emblemScale.value)
                            .clip(RoundedCornerShape(36.dp))
                            .background(SplashEmerald.copy(alpha = 0.08f))
                    )
                    Surface(
                        shape = RoundedCornerShape(30.dp),
                        color = SplashObsidian,
                        border = BorderStroke(1.25.dp, SplashEmerald.copy(alpha = 0.5f)),
                        shadowElevation = 20.dp,
                        modifier = Modifier
                            .size(116.dp)
                            .scale(emblemScale.value)
                            .testTag("splash_logo")
                    ) {
                        if (sampledLogoBitmap != null) {
                            Image(
                                bitmap = sampledLogoBitmap,
                                contentDescription = stringResource(R.string.app_name),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(30.dp))
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.bincheck_app_icon_1794588466984),
                                contentDescription = stringResource(R.string.app_name),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(30.dp))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    shape = CircleShape,
                    color = SplashEmerald.copy(alpha = 0.14f),
                    border = BorderStroke(0.75.dp, SplashEmerald.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SplashEmerald)
                        )
                        Text(
                            text = "BATCH & IIN STUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            color = SplashEmerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold,
                    color = SplashInkPrimary,
                    modifier = Modifier.testTag("splash_title")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplashInkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("splash_tagline")
                )

                Spacer(modifier = Modifier.height(26.dp))

                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .width(168.dp)
                        .testTag("splash_progress"),
                    color = SplashEmerald,
                    trackColor = Color(0xFF172834)
                )
            }

            Surface(
                shape = CircleShape,
                color = Color(0xFF10202B),
                border = BorderStroke(0.75.dp, SplashEmerald.copy(alpha = 0.25f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .alpha(contentAlpha.value)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelegramPaperPlaneIcon(
                        color = SplashEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.developer_channel_handle),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        color = SplashInkMuted
                    )
                }
            }
        }
    }
}
