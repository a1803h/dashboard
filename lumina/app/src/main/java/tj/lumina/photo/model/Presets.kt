package tj.lumina.photo.model

/** All adjustments are deterministic pixel transforms: no generative editing. */
data class FilmPreset(
    val id: String,
    val title: String,
    val family: String,
    val description: String,
    val thumbnail: String,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val fade: Float = 0f,
    val grain: Float = 0f,
    val gamma: Float = 1f,
    val shadowBlue: Float = 0f,
    val highlightWarmth: Float = 0f
)

object Presets {
    val all = listOf(
        FilmPreset("portra", "Kodak Portra", "Плёнка", "Тёплая плёнка с мягкими светами", "film.jpg", contrast = -0.08f, saturation = -0.09f, warmth = 0.14f, fade = 0.07f, grain = 0.19f, gamma = 0.97f, shadowBlue = 0.045f, highlightWarmth = 0.04f),
        FilmPreset("gold", "Kodak Gold", "Плёнка", "Золотистый винтажный оттенок", "city.jpg", contrast = 0.04f, saturation = 0.12f, warmth = 0.21f, fade = 0.04f, grain = 0.24f, gamma = 0.96f, highlightWarmth = 0.10f),
        FilmPreset("fuji", "Fuji Classic", "Плёнка", "Прохладные тени, приглушённые цвета", "mountain.jpg", contrast = 0.06f, saturation = -0.17f, warmth = -0.045f, fade = 0.05f, grain = 0.12f, shadowBlue = 0.09f),
        FilmPreset("natural", "Fuji Natural", "Плёнка", "Нежная естественная цветопередача", "hero.jpg", contrast = -0.045f, saturation = -0.065f, warmth = 0.025f, grain = 0.09f),
        FilmPreset("dslr", "DSLR Natural", "DSLR", "Естественные оттенки и мягкая резкость", "portrait.jpg", contrast = 0.025f, saturation = -0.035f, warmth = 0.015f, gamma = 0.99f),
        FilmPreset("cinema", "Cinematic", "Кино", "Глубокие тени и кинематографичный цвет", "cinema.jpg", contrast = 0.20f, saturation = -0.11f, warmth = 0.08f, fade = 0.018f, gamma = 1.045f, shadowBlue = 0.10f, highlightWarmth = 0.11f),
        FilmPreset("bw", "Monochrome", "Ч/Б", "Контрастная чёрно-белая плёнка", "bw.jpg", contrast = 0.16f, saturation = -1f, grain = 0.14f, gamma = 0.98f),
        FilmPreset("clean", "Clean Pro", "Натур.", "Нейтральная коррекция без изменения сюжета", "cat.jpg", contrast = 0.035f, saturation = 0.005f, warmth = 0f, gamma = 0.99f)
    )
    val families = listOf("Плёнка", "DSLR", "Кино", "Ч/Б", "Натур.")
    fun get(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

data class EditSettings(
    val intensity: Float = 0.8f,  // 0..1
    val grain: Float = 0.3f,      // 0..1 (multiplies the preset's grain)
    val contrast: Float = 0f,    // -1..1
    val warmth: Float = 0f,      // -1..1
    val fade: Float = 0f,        // 0..1
    val saturation: Float = 0f   // -1..1
)
