import 'package:flutter/material.dart';

/// Bedrock's visual language: a calm, minimalist dark surface built on a
/// monotone *cool true-grey* palette (a faint steel tint, no colour). The
/// single accent is a light neutral; filled controls put dark text on it.
/// The UI is editorial - big quiet type and hairline-divided lists, not cards.
abstract final class BedrockColors {
  /// Seed for the Material scheme - a mid cool grey.
  static const seed = Color(0xFF6E7378);

  /// The one highlight: a light cool neutral. Dark text sits on it.
  static const accent = Color(0xFFC7CCD1);

  /// Near-black scaffold with a faint cool tint.
  static const background = Color(0xFF0E0F12);

  /// Slightly lifted surface, used sparingly (inputs, pressed states).
  static const surface = Color(0xFF17181B);

  /// A touch more lift for nested / selected elements.
  static const surfaceHigh = Color(0xFF212327);

  static const onSurface = Color(0xFFEDEFF2);
  static const onSurfaceMuted = Color(0xFF8A9099);

  /// Text/icon colour that sits on top of [accent] (its dark counterpart).
  static const onAccent = Color(0xFF1B1D20);

  /// Hairline used to bracket and separate list rows.
  static const hairline = Color(0xFF26282C);

  /// Cool charcoal gradient, kept for the odd immersive surface.
  static const heroGradient = [Color(0xFF2A2D31), Color(0xFF1B1D20)];
}

/// Corner radii used across the app - large and soft, ScreenZen-style.
abstract final class BedrockRadii {
  static const card = 24.0;
  static const hero = 28.0;
  static const chip = 14.0;
  static const pill = 999.0;
}

ThemeData buildBedrockTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: BedrockColors.seed,
    brightness: Brightness.dark,
  ).copyWith(
    surface: BedrockColors.background,
    surfaceContainerHighest: BedrockColors.surfaceHigh,
    onSurface: BedrockColors.onSurface,
    primary: BedrockColors.accent,
  );

  final base = ThemeData(
    colorScheme: scheme,
    useMaterial3: true,
    scaffoldBackgroundColor: BedrockColors.background,
  );

  return base.copyWith(
    textTheme: base.textTheme.apply(
      bodyColor: BedrockColors.onSurface,
      displayColor: BedrockColors.onSurface,
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: BedrockColors.background,
      surfaceTintColor: Colors.transparent,
      centerTitle: false,
      elevation: 0,
      titleTextStyle: TextStyle(
        color: BedrockColors.onSurface,
        fontSize: 26,
        fontWeight: FontWeight.w700,
      ),
    ),
    cardTheme: CardThemeData(
      color: BedrockColors.surface,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(BedrockRadii.card),
      ),
    ),
    dividerTheme: const DividerThemeData(
      color: BedrockColors.hairline,
      space: 1,
      thickness: 1,
    ),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith(
        (s) => s.contains(WidgetState.selected)
            ? BedrockColors.onAccent
            : const Color(0xFF5F646B),
      ),
      trackColor: WidgetStateProperty.resolveWith(
        (s) => s.contains(WidgetState.selected)
            ? BedrockColors.accent
            : const Color(0xFF26282C),
      ),
      trackOutlineColor: WidgetStateProperty.all(Colors.transparent),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: const Color(0xFF131417),
      surfaceTintColor: Colors.transparent,
      indicatorColor: BedrockColors.accent.withValues(alpha: 0.18),
      elevation: 0,
      height: 68,
      labelTextStyle: WidgetStateProperty.resolveWith(
        (s) => TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: s.contains(WidgetState.selected)
              ? BedrockColors.onSurface
              : BedrockColors.onSurfaceMuted,
        ),
      ),
      iconTheme: WidgetStateProperty.resolveWith(
        (s) => IconThemeData(
          color: s.contains(WidgetState.selected)
              ? BedrockColors.accent
              : BedrockColors.onSurfaceMuted,
        ),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: BedrockColors.accent,
        foregroundColor: BedrockColors.onAccent,
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
        shape: const StadiumBorder(),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: BedrockColors.onSurface,
        side: const BorderSide(color: Color(0xFF33363B)),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
        textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
        shape: const StadiumBorder(),
      ),
    ),
    timePickerTheme: TimePickerThemeData(
      backgroundColor: BedrockColors.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(BedrockRadii.card),
      ),
    ),
  );
}
