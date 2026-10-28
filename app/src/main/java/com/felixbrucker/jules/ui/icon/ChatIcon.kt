package com.felixbrucker.jules.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("ObjectPropertyName")
private var _chatIcon: ImageVector? = null
val ChatIcon: ImageVector
    get() {
        if (_chatIcon != null) return _chatIcon!!
        _chatIcon = ImageVector.Builder(
            name = "chat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 14f)
                horizontalLineToRelative(8f)
                verticalLineTo(12f)
                horizontalLineTo(6f)
                verticalLineToRelative(2f)
                close()
                moveTo(6f, 11f)
                horizontalLineTo(18f)
                verticalLineTo(9f)
                horizontalLineTo(6f)
                verticalLineToRelative(2f)
                close()
                moveTo(6f, 8f)
                horizontalLineTo(18f)
                verticalLineTo(6f)
                horizontalLineTo(6f)
                verticalLineTo(8f)
                close()
                moveTo(2f, 22f)
                verticalLineTo(4f)
                quadTo(2f, 3.17f, 2.59f, 2.59f)
                reflectiveQuadTo(4f, 2f)
                horizontalLineTo(20f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                reflectiveQuadTo(22f, 4f)
                verticalLineTo(16f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(20f, 18f)
                horizontalLineTo(6f)
                lineTo(2f, 22f)
                close()
                moveTo(5.15f, 16f)
                horizontalLineTo(20f)
                verticalLineTo(4f)
                horizontalLineTo(4f)
                verticalLineTo(17.13f)
                lineTo(5.15f, 16f)
                close()
                moveTo(4f, 16f)
                verticalLineTo(4f)
                verticalLineTo(16f)
                close()
            }
        }.build()
        return _chatIcon!!
    }
