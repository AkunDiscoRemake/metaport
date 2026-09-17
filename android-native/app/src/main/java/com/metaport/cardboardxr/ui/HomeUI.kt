package com.metaport.cardboardxr.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.metaport.cardboardxr.arcore.ARCoreManager
import com.metaport.cardboardxr.render.CardboardRendererView

class HomeUI(private val context: Context, private val arCoreManager: ARCoreManager?, private val isARCoreSupported: Boolean) {
    private var rootView: ViewGroup? = null
    private var cardboardRendererView: CardboardRendererView? = null
    private var statusText: TextView? = null

    private val environments = listOf(
        Environment("Tropical Island", "#4FC3F7", true),
        Environment("Cyber City", "#E040FB", false),
        Environment("Space Station", "#212121", false),
        Environment("Forest Valley", "#66BB6A", false),
        Environment("Nebula Void", "#7C4DFF", false),
        Environment("Void Space", "#000000", false),
        Environment("Japanese Dojo", "#FF7043", false),
        Environment("Desert Oasis", "#FFCA28", true),
        Environment("Passthrough", "#9E9E9E", true)
    )

    private val games = listOf(
        Game("Beat Saber", "Sabres nas mãos", "R$ 89,90", 4.9f),
        Game("Pong Spatial", "Pong 3D", "Grátis", 4.8f),
        Game("Real VR Fishing", "Pesca relaxante", "R$ 49,90", 4.8f),
        Game("Walkabout Mini Golf", "Mini golf", "R$ 59,90", 4.9f),
        Game("Smash Drums", "Bateria 3D", "R$ 39,90", 4.7f),
        Game("Superhot VR", "Tempo anda quando você anda", "R$ 69,90", 4.7f),
        Game("Asgard's Wrath 2", "Espada e escudo", "R$ 149,90", 4.8f),
        Game("Gorilla Tag", "Locomoção gorila", "Grátis", 4.6f),
        Game("Job Simulator", "Trabalhos divertidos", "R$ 39,90", 4.5f),
        Game("Tetris Effect", "Tetris 3D", "R$ 59,90", 4.8f),
        Game("Puzzling Places", "Puzzles 3D", "R$ 49,90", 4.7f),
        Game("First Steps", "Tutorial", "Grátis", 4.9f)
    )

    private val dockApps = listOf("Store", "Browser", "Files", "Settings", "Facebook", "Instagram", "WhatsApp", "Spotify", "YouTube", "Netflix", "Beat Saber", "Pong")

    fun getView(): View {
        return try { createMainView() } catch (e: Exception) { createFallbackView() }
    }

    private fun createMainView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val statusBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(20,20,20,20)
        }
        statusText = TextView(context).apply {
            text = if (isARCoreSupported) "MetaPort v2.3 - 6DOF ✅ - 9 Ambientes - 12 Jogos" else "MetaPort v2.3 - 3DOF - 9 Ambientes - 12 Jogos"
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        statusBar.addView(statusText)
        root.addView(statusBar)

        try {
            cardboardRendererView = CardboardRendererView(context, arCoreManager)
            root.addView(cardboardRendererView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } catch (e: Exception) {
            val placeholder = TextView(context).apply {
                text = "3D Renderer\n${if (isARCoreSupported) "6DOF Ativo" else "3DOF"}\nAmbiente: Tropical Island"
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#16213E"))
            }
            root.addView(placeholder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        val libraryLabel = TextView(context).apply {
            text = "App Library - 12 Jogos + 8 Apps"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20,15,20,10)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }
        root.addView(libraryLabel)

        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = GameAdapter(games) { game -> launchGame(game) }
            setBackgroundColor(Color.parseColor("#0F0F1E"))
        }
        root.addView(recyclerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400))

        val dockRecycler = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 6)
            adapter = DockAdapter(dockApps) { app -> launchDockApp(app) }
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(10,10,10,20)
        }
        root.addView(dockRecycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120))

        val envScroll = HorizontalScrollView(context).apply { setBackgroundColor(Color.parseColor("#16213E")) }
        val envLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(10,10,10,10) }
        environments.forEach { env ->
            val btn = Button(context).apply {
                text = env.name
                setOnClickListener { setEnvironment(env) }
                setBackgroundColor(Color.parseColor(env.color))
                setTextColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.setMargins(5,5,5,5)
                layoutParams = params
            }
            envLayout.addView(btn)
        }
        envScroll.addView(envLayout)
        root.addView(envScroll)

        rootView = root
        return root
    }

    private fun createFallbackView(): View {
        return TextView(context).apply {
            text = "MetaPort VR v2.3\nFallback UI\n9 Ambientes\n12 Jogos\n6DOF: ${if (isARCoreSupported) "Sim" else "Não"}"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setPadding(50,50,50,50)
            gravity = Gravity.CENTER
        }
    }

    private fun setEnvironment(env: Environment) {
        try {
            android.widget.Toast.makeText(context, "Ambiente: ${env.name}", android.widget.Toast.LENGTH_SHORT).show()
            cardboardRendererView?.setEnvironment(env.name)
            statusText?.text = "Ambiente: ${env.name}"
        } catch (e: Exception) {}
    }

    private fun launchGame(game: Game) {
        try {
            android.widget.Toast.makeText(context, "Iniciando ${game.name}", android.widget.Toast.LENGTH_SHORT).show()
            cardboardRendererView?.launchGame(game.name)
            statusText?.text = "Jogando: ${game.name}"
        } catch (e: Exception) {}
    }

    private fun launchDockApp(app: String) {
        try {
            android.widget.Toast.makeText(context, "Abrindo $app", android.widget.Toast.LENGTH_SHORT).show()
            val game = games.find { it.name.contains(app, ignoreCase = true) }
            if (game != null) launchGame(game)
        } catch (e: Exception) {}
    }

    fun onResume() { try { cardboardRendererView?.onResume() } catch (e: Exception) {} }
    fun onPause() { try { cardboardRendererView?.onPause() } catch (e: Exception) {} }
    fun onDestroy() { try { cardboardRendererView?.onDestroy() } catch (e: Exception) {} }

    data class Environment(val name: String, val color: String, val isMixedReality: Boolean)
    data class Game(val name: String, val desc: String, val price: String, val rating: Float)

    class GameAdapter(private val games: List<Game>, private val onClick: (Game) -> Unit) : RecyclerView.Adapter<GameAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(1001)
            val desc: TextView = view.findViewById(1002)
            val price: TextView = view.findViewById(1003)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val card = CardView(parent.context).apply {
                radius = 20f; cardElevation = 8f; setCardBackgroundColor(Color.parseColor("#2A2A3E"))
                val params = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 180)
                params.setMargins(8,8,8,8); layoutParams = params
            }
            val layout = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL; setPadding(15,15,15,15); gravity = Gravity.CENTER }
            val nameView = TextView(parent.context).apply { id = 1001; setTextColor(Color.WHITE); textSize = 13f; maxLines = 2; gravity = Gravity.CENTER }
            val descView = TextView(parent.context).apply { id = 1002; setTextColor(Color.LTGRAY); textSize = 10f; maxLines = 2; gravity = Gravity.CENTER }
            val priceView = TextView(parent.context).apply { id = 1003; setTextColor(Color.parseColor("#4FC3F7")); textSize = 11f; gravity = Gravity.CENTER }
            layout.addView(nameView); layout.addView(descView); layout.addView(priceView); card.addView(layout)
            return ViewHolder(card)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val game = games[position]
            holder.name.text = game.name; holder.desc.text = game.desc; holder.price.text = "${game.price} ★${game.rating}"
            holder.itemView.setOnClickListener { onClick(game) }
        }
        override fun getItemCount() = games.size
    }

    class DockAdapter(private val apps: List<String>, private val onClick: (String) -> Unit) : RecyclerView.Adapter<DockAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val name: TextView = view.findViewById(2001) }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val card = CardView(parent.context).apply {
                radius = 30f; cardElevation = 6f; setCardBackgroundColor(Color.parseColor("#3A3A4E"))
                val params = ViewGroup.MarginLayoutParams(120, 100); params.setMargins(6,6,6,6); layoutParams = params
            }
            val text = TextView(parent.context).apply { id = 2001; setTextColor(Color.WHITE); textSize = 11f; gravity = Gravity.CENTER; setPadding(5,5,5,5) }
            card.addView(text); return ViewHolder(card)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.name.text = apps[position]
            holder.itemView.setOnClickListener { onClick(apps[position]) }
        }
        override fun getItemCount() = apps.size
    }
}
