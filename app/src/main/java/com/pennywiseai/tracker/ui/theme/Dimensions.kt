package com.pennywiseai.tracker.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralized dimensions for consistent UI throughout the app
 */
object Dimensions {
    
    // Padding values (complementing Spacing.kt)
    object Padding {
        val content = 16.dp      // Standard content padding
        val card = 18.dp         // Card internal padding
        val empty = 40.dp        // Empty state padding
        val fab = 16.dp          // FAB padding
    }
    
    // Elevation values
    object Elevation {
        val card = 1.dp
        val fab = 6.dp
        val bottomBar = 3.dp
        val dialog = 8.dp
    }
    
    // Alpha values for transparency
    object Alpha {
        const val high = 0.90f
        const val medium = 0.55f
        const val disabled = 0.38f
        const val divider = 0.12f
        const val surface = 0.65f
        const val subtitle = 0.75f
    }
    
    // Icon sizes
    object Icon {
        val small = 16.dp
        val medium = 24.dp
        val large = 44.dp
        val extraLarge = 120.dp  // For empty states
    }
    
    // Corner radius (additional to Shape.kt)
    object CornerRadius {
        val small = 4.dp
        val medium = 8.dp
        val large = 16.dp
        val full = 50.dp  // For circular elements
    }
    
    // Text sizes
    object TextSize {
        val small = 12.sp
        val body = 14.sp
        val medium = 16.sp
        val large = 20.sp
        val title = 24.sp
        val display = 32.sp
    }
    
    // Component specific dimensions
    object Component {
        val bottomBarHeight = 80.dp
        val buttonHeight = 48.dp
        val minTouchTarget = 48.dp
        val dividerThickness = 1.dp
        val progressIndicatorSize = 24.dp
        val chipHeight = 32.dp
    }
    
    // Animation durations (in milliseconds)
    object Animation {
        const val short = 120
        const val medium = 250
        const val long = 400
    }
}