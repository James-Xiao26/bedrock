import 'package:flutter/material.dart';

/// Bedrock's visual language: a ScreenZen-style calm dark surface built around
/// the app's existing indigo. The seed colour is unchanged from the original
/// theme; only the surrounding surfaces, shapes and controls are restyled to
/// match ScreenZen's rounded, navy-tinted aesthetic.
abstract final class BedrockColors {
  /// The original app accent - kept exactly as it was.
  static const seed = Color(0xFF5C6BC0);

  /// Brighter indigo for accents on dark surfaces.
  static const accent = Color(0xFF8B96E6);

  /// Near-black scaffold with a faint indigo tint.
  static const background = Color(0xFF111019);

  /// Default rounded-card surface.
  static const surface = Color(0xFF1C1B29);

  /// Slightly lifted surface for nested / selected elements.
  static const surfaceHigh = Color(0xFF262538);

  static const onSurface = Color(0xFFECEBF5);
  static const onSurfaceMuted = Color(0xFF9B99B4);

  /// Hero-card gradient, indigo shading into a deeper violet.
  static const heroGradient = [Color(0xFF5C6BC0), Color(0xFF4A3F8C)];
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
      color: Color(0xFF2C2B3D),
      space: 1,
      thickness: 1,
    ),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith(
        (s) => s.contains(WidgetState.selected)
            ? Colors.white
            : const Color(0xFF6E6C86),
      ),
      trackColor: WidgetStateProperty.resolveWith(
        (s) => s.contains(WidgetState.selected)
            ? BedrockColors.accent
            : const Color(0xFF2C2B3D),
      ),
      trackOutlineColor: WidgetStateProperty.all(Colors.transparent),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: const Color(0xFF17161F),
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
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
        shape: const StadiumBorder(),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: BedrockColors.onSurface,
        side: const BorderSide(color: Color(0xFF34334A)),
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
