package com.personal.apptruyen.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.personal.apptruyen.R

// Google Fonts — Noto Serif for reading, Inter for UI
val NotoSerif =
    FontFamily(
        Font(R.font.noto_serif_regular, FontWeight.Normal),
        Font(R.font.noto_serif_medium, FontWeight.Medium),
        Font(R.font.noto_serif_semibold, FontWeight.SemiBold),
        Font(R.font.noto_serif_bold, FontWeight.Bold),
    )

val BeVietnamPro =
    FontFamily(
        Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
        Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
        Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
        Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
    )

val Typography =
    Typography(
        // Display — for splash, hero sections
        displayLarge =
            TextStyle(
                fontFamily = NotoSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = NotoSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                letterSpacing = 0.sp,
            ),
        // Headline — screen titles, section headers
        headlineLarge =
            TextStyle(
                fontFamily = NotoSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = NotoSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = NotoSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        // Title — card titles, list item headers
        titleLarge =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        // Body — paragraph text, descriptions
        bodyLarge =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 28.sp, // Extra line height for reading comfort
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.4.sp,
            ),
        // Label — buttons, chips, badges
        labelLarge =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )
