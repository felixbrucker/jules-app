package com.felixbrucker.jules.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("ObjectPropertyName")
private var _differenceIcon: ImageVector? = null
val DifferenceIcon: ImageVector
    get() {
        if (_differenceIcon != null) return _differenceIcon!!
        _differenceIcon = ImageVector.Builder(
            name = "difference",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.5f, 11f)
                horizontalLineToRelative(2f)
                verticalLineTo(9f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                horizontalLineToRelative(-2f)
                verticalLineTo(5f)
                horizontalLineToRelative(-2f)
                verticalLineTo(7f)
                horizontalLineToRelative(-2f)
                verticalLineTo(9f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveToRelative(-2f, 4f)
                horizontalLineToRelative(6f)
                verticalLineTo(13f)
                horizontalLineToRelative(-6f)
                verticalLineToRelative(2f)
                close()
                moveTo(8f, 19f)
                quadTo(7.18f, 19f, 6.59f, 18.41f)
                reflectiveQuadTo(6f, 17f)
                verticalLineTo(3f)
                quadTo(6f, 2.17f, 6.59f, 1.59f)
                reflectiveQuadTo(8f, 1f)
                horizontalLineToRelative(7f)
                lineToRelative(6f, 6f)
                verticalLineTo(17f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(19f, 19f)
                horizontalLineTo(8f)
                close()
                moveTo(8f, 17f)
                horizontalLineTo(19f)
                verticalLineTo(8f)
                lineTo(14f, 3f)
                horizontalLineTo(8f)
                verticalLineTo(17f)
                close()
                moveTo(4f, 23f)
                quadTo(3.18f, 23f, 2.59f, 22.41f)
                reflectiveQuadTo(2f, 21f)
                verticalLineTo(7f)
                horizontalLineTo(4f)
                verticalLineTo(21f)
                horizontalLineTo(15f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                close()
                moveTo(8f, 17f)
                verticalLineTo(3f)
                verticalLineTo(8f)
                verticalLineToRelative(9f)
                close()
            }
        }.build()
        return _differenceIcon!!
    }
