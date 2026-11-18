package dev.aero.menu

sealed class Item {
    abstract val key: String
    abstract val label: String
}

data class Toggle(
    override val key: String,
    override val label: String,
    val def: Boolean = false,
) : Item()

data class Slider(
    override val key: String,
    override val label: String,
    val min: Int,
    val max: Int,
    val def: Int,
    val unit: String = "",
) : Item()

data class Tab(val title: String, val items: List<Item>)

// Menu layout lives here. Adding a feature = adding an Item; the UI picks it up automatically.
object MenuSpec {
    val tabs: List<Tab> = listOf(
        Tab(
            "Visuals", listOf(
                Toggle("esp.enabled", "ESP"),
                Toggle("esp.box", "Box"),
                Toggle("esp.skeleton", "Skeleton"),
                Toggle("esp.health", "Health bar"),
                Toggle("esp.name", "Name"),
                Toggle("esp.distance", "Distance"),
                Toggle("esp.lines", "Snaplines"),
            )
        ),
        Tab(
            "Aim", listOf(
                Toggle("aim.enabled", "Aim assist"),
                Toggle("aim.visibleOnly", "Visible only", true),
                Toggle("aim.drawFov", "Draw FOV"),
                Slider("aim.fov", "FOV", 1, 180, 30, "\u00B0"),
                Slider("aim.smooth", "Smoothing", 1, 100, 20),
            )
        ),
        Tab(
            "Misc", listOf(
                Toggle("misc.radar", "Radar"),
                Toggle("misc.recoil", "Recoil compensation"),
                Toggle("misc.fovChanger", "FOV changer"),
                Slider("misc.fovValue", "Camera FOV", 60, 120, 90),
            )
        ),
        Tab(
            "Settings", listOf(
                Slider("ui.opacity", "Menu opacity", 40, 100, 95, "%"),
            )
        ),
    )
}

// Single place that holds every value the menu edits. The future feature code reads from here
// or subscribes to onChange; the UI never talks to the game directly.
object MenuState {
    private val toggles = HashMap<String, Boolean>()
    private val sliders = HashMap<String, Int>()

    @Volatile
    var onChange: ((String, Any) -> Unit)? = null

    fun get(item: Toggle): Boolean = toggles[item.key] ?: item.def
    fun get(item: Slider): Int = sliders[item.key] ?: item.def

    fun set(key: String, value: Boolean) {
        toggles[key] = value
        onChange?.invoke(key, value)
    }

    fun set(key: String, value: Int) {
        sliders[key] = value
        onChange?.invoke(key, value)
    }
}
