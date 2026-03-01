package com.pennywiseai.tracker.presentation.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.skydoves.colorpicker.compose.*
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.ui.components.CategoryDot
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditDialog(
    category: CategoryEntity? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String, isIncome: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var isIncome by remember { mutableStateOf(category?.isIncome ?: false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    
    // Color picker controller
    val controller = rememberColorPickerController()
    
    // Initialize with existing color or default
    LaunchedEffect(category) {
        val initialColor = category?.color?.let { parseColor(it) } ?: Color(0xFF4CAF50)
        controller.selectByColor(initialColor, fromUser = false)
    }
    
    Dialog(onDismissRequest = onDismiss) {
        PennyWiseCardV2(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            shape = RoundedCornerShape(28.dp),
            contentPadding = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimensions.Padding.card),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Title
                Text(
                    text = if (category == null) "Add Category" else "Edit Category",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Category Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = if (it.isBlank()) "Category name is required" else null
                    },
                    label = { Text("Category Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Category Type Selection
                Column {
                    Text(
                        text = "Category Type",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        FilterChip(
                            selected = !isIncome,
                            onClick = { isIncome = false },
                            label = { Text("Expense") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isIncome,
                            onClick = { isIncome = true },
                            label = { Text("Income") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Color Selection
                Column {
                    Text(
                        text = "Color",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    
                    // Color Wheel Picker
                    HsvColorPicker(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .padding(horizontal = Spacing.md),
                        controller = controller,
                        onColorChanged = { colorEnvelope: ColorEnvelope ->
                            // Color is automatically updated in controller
                        }
                    )
                    
                    // Alpha Slider (optional - for transparency)
                    AlphaSlider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md)
                            .height(35.dp),
                        controller = controller,
                        tileOddColor = Color.White,
                        tileEvenColor = Color.LightGray
                    )
                    
                    // Brightness Slider
                    BrightnessSlider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md)
                            .height(35.dp),
                        controller = controller
                    )
                }
                
                // Preview
                PennyWiseCardV2(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    contentPadding = Dimensions.Padding.content
                ) {
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        // Show selected color
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(controller.selectedColor.value)
                        )
                        Text(
                            text = name.ifBlank { "Category Name" },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Show hex value
                        Text(
                            text = colorToHex(controller.selectedColor.value),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val selectedColor = colorToHex(controller.selectedColor.value)
                                onSave(name.trim(), selectedColor, isIncome)
                            } else {
                                nameError = "Category name is required"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank()
                    ) {
                        Text(if (category == null) "Add" else "Save")
                    }
                }
            }
        }
    }
}

private fun parseColor(colorString: String): Color {
    return try {
        val cleanColor = if (colorString.startsWith("#")) colorString else "#$colorString"
        Color(android.graphics.Color.parseColor(cleanColor))
    } catch (e: Exception) {
        Color(0xFF4CAF50) // Default green
    }
}

private fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    // Convert to RGB hex (without alpha) for storage
    return String.format("#%06X", argb and 0xFFFFFF)
}