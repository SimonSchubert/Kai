package com.inspiredandroid.kai.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.inspiredandroid.kai.ui.DarkColorScheme
import com.inspiredandroid.kai.ui.LightColorScheme
import com.inspiredandroid.kai.ui.Theme
import com.inspiredandroid.kai.ui.markdown.MarkdownContent
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Screenshot tests for the LaTeX math rendering feature (issue #150). Each test packs multiple
 * related formulas into one card so the kai9000.com landing-page PNGs use their full vertical
 * space. Snapshots are copied to site/img/math-*.png by the updateScreenshots Gradle task.
 */
@OptIn(ExperimentalResourceApi::class)
class MathScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_9A.copy(softButtons = false),
        showSystemUi = false,
        maxPercentDifference = 0.1,
        // Shrink to content so the generated site/img/math-*.png fits the card;
        // the outer Box uses fillMaxWidth to still span the device width.
        renderingMode = SessionParams.RenderingMode.SHRINK,
    )

    @Before
    fun setup() {
        setResourceReaderAndroidContext(paparazzi.context)
    }

    private fun Paparazzi.snapMessage(
        colorScheme: ColorScheme,
        markdown: String,
    ) {
        val theme = if (colorScheme == DarkColorScheme) {
            "android:Theme.Material.NoActionBar"
        } else {
            "android:Theme.Material.Light.NoActionBar"
        }
        unsafeUpdateConfig(theme = theme)

        snapshot {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Theme(colorScheme = colorScheme) {
                    MessageCanvas {
                        MarkdownContent(
                            content = markdown,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun math_algebra_light() {
        paparazzi.snapMessage(
            LightColorScheme,
            """
            The **quadratic formula** gives the roots of ${D}ax^2 + bx + c = 0$D:

            ${D}$D
            x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
            ${D}$D

            The sum of the first ${D}n$D natural numbers and their squares:

            ${D}$D
            \sum_{k=1}^{n} k = \frac{n(n+1)}{2}
            ${D}$D

            ${D}$D
            \sum_{k=1}^{n} k^2 = \frac{n(n+1)(2n+1)}{6}
            ${D}$D

            And the **binomial theorem** combines them both:

            ${D}$D
            (x+y)^n = \sum_{k=0}^{n} \binom{n}{k} x^{n-k} y^k
            ${D}$D
            """.trimIndent(),
        )
    }

    @Test
    fun math_calculus_dark() {
        // The exact reproducer from issue #150: integration by parts on x²·arctan(x).
        paparazzi.snapMessage(
            DarkColorScheme,
            """
            To evaluate $D\int_a^b x^2 \arctan(x)\,dx$D, use integration by parts with
            ${D}u = \arctan(x)$D and ${D}dv = x^2\,dx$D:

            ${D}$D
            \int x^2 \arctan(x)\,dx = \frac{x^3}{3}\arctan(x) - \frac{1}{3}\int \frac{x^3}{1+x^2}\,dx
            ${D}$D

            The remaining integral simplifies to $D\frac{x^2}{2} - \frac{1}{2}\ln(1+x^2)$D,
            so the full antiderivative is:

            ${D}$D
            F(x) = \frac{x^3}{3}\arctan(x) - \frac{x^2}{6} + \frac{1}{6}\ln(1+x^2)
            ${D}$D

            Evaluate ${D}F(b) - F(a)$D to get the definite integral.
            """.trimIndent(),
        )
    }

    @Test
    fun math_physics_dark() {
        paparazzi.snapMessage(
            DarkColorScheme,
            """
            **Einstein's mass–energy equivalence**:

            ${D}$D
            E = mc^2
            ${D}$D

            **Schrödinger's time-independent equation** in one dimension:

            ${D}$D
            -\frac{\hbar^2}{2m}\frac{\partial^2 \psi}{\partial x^2} + V(x)\psi = E\psi
            ${D}$D

            **Gauss's law** (one of Maxwell's equations):

            ${D}$D
            \nabla \cdot \vec{E} = \frac{\rho}{\varepsilon_0}
            ${D}$D

            where $D\hbar$D is the reduced Planck constant, $D\psi$D is the wave function,
            and $D\vec{E}$D is the electric field.
            """.trimIndent(),
        )
    }

    @Test
    fun math_structures_light() {
        paparazzi.snapMessage(
            LightColorScheme,
            """
            The **2×2 rotation matrix** by angle $D\theta$D:

            ${D}$D
            R(\theta) = \begin{pmatrix} \cos\theta & -\sin\theta \\ \sin\theta & \cos\theta \end{pmatrix}
            ${D}$D

            The **absolute value** as a piecewise function:

            ${D}$D
            |x| = \begin{cases} x & \text{if } x \geq 0 \\ -x & \text{if } x < 0 \end{cases}
            ${D}$D

            **Completing the square** on ${D}x^2 + 6x + 5$D:

            ${D}$D
            \begin{aligned}
              x^2 + 6x + 5 &= (x^2 + 6x + 9) - 9 + 5 \\
                           &= (x + 3)^2 - 4
            \end{aligned}
            ${D}$D
            """.trimIndent(),
        )
    }

    @Test
    fun math_notation_light() {
        paparazzi.snapMessage(
            LightColorScheme,
            """
            **Classic identities** in one line each:

            - Euler's formula: ${D}e^{i\pi} + 1 = 0$D
            - Pythagorean: $D\sin^2\theta + \cos^2\theta = 1$D
            - Sine limit: $D\lim_{x \to 0} \frac{\sin x}{x} = 1$D
            - Sets: $D\mathbb{N} \subset \mathbb{Z} \subset \mathbb{Q} \subset \mathbb{R} \subset \mathbb{C}$D

            **Statistics & vectors** use accents for estimators and directions:

            ${D}$D
            \bar{x} = \frac{1}{n}\sum_{i=1}^{n} x_i \qquad \hat{\theta} = \arg\max_{\theta} \mathcal{L}(\theta)
            ${D}$D

            ${D}$D
            \vec{v} \cdot \vec{w} = |\vec{v}||\vec{w}|\cos\theta
            ${D}$D

            **Greek letters** show up everywhere:
            $D\alpha, \beta, \gamma, \delta, \epsilon, \theta, \lambda, \mu, \pi, \sigma, \phi, \omega$D.
            """.trimIndent(),
        )
    }

    private companion object {
        /** Use `${D}` inside raw strings to emit a literal `$` without triggering Kotlin interpolation. */
        const val D = "\$"
    }
}

// PIXEL_9A is 1080 px wide at 420 dpi → 411.43 dp. Under RenderingMode.SHRINK both axes are
// unbounded, so fillMaxWidth collapses to zero; we pin the Box to the real device width to
// keep a consistent card width while SHRINK tightly trims the unused vertical space.
private val DeviceWidth = 411.dp

@Composable
private fun MessageCanvas(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .width(DeviceWidth)
            .background(MaterialTheme.colorScheme.background),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            content = content,
        )
    }
}
