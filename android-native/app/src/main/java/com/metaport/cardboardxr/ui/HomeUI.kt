package com.metaport.cardboardxr.ui

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.metaport.cardboardxr.arcore.ARCoreManager
import com.metaport.cardboardxr.render.CardboardRendererView

/**
 * Home UI - 3D Spatial UI inspirada em Horizon OS, NovaMr, ZentraXR
 * Mas implementada com Android Views + OpenGL pra não crashar
 * 
 * - Dock inferior 3D curvo (RecyclerView horizontal)
 * - App Library grid 3D
 * - Ambientes 3D
 * - Jogos
 */
class HomeUI(
    private val context: Context,
    private val arCoreManager: ARCoreManager?,
    private val isARCoreSupported: Boolean
) {

    private var rootView: ViewGroup? = null
    private var cardboardRendererView: CardboardRendererView? = null
    private var statusText: TextView? = null

    // 9 Ambientes
    private val environments = listOf(
        Environment("Tropical Island", "Ilha tropical com palmeiras", "#4FC3F7", true),
        Environment("Cyber City", "Cidade neon cyberpunk", "#E040FB", false),
        Environment("Space Station", "Estação espacial orbital", "#212121", false),
        Environment("Forest Valley", "Vale floresta verde", "#66BB6A", false),
        Environment("Nebula Void", "Nebulosa espacial", "#7C4DFF", false),
        Environment("Void Space", "Vazio infinito", "#000000", false),
        Environment("Japanese Dojo", "Dojo japonês sunset", "#FF7043", false),
        Environment("Desert Oasis", "Deserto com oasis", "#FFCA28", true),
        Environment("Passthrough", "Mundo real via câmera", "#9E9E9E", true)
    )

    // 12 Jogos
    private val games = listOf(
        Game("Beat Saber", "Corte blocos no ritmo - Sabres nas mãos", "R$ 89,90", 4.9f),
        Game("Pong Spatial", "Pong 3D igual ZentraXR", "Grátis", 4.8f),
        Game("Real VR Fishing", "Pesca relaxante na ilha", "R$ 49,90", 4.8f),
        Game("Walkabout Mini Golf", "Mini golf com amigos", "R$ 59,90", 4.9f),
        Game("Smash Drums", "Bateria 3D espacial", "R$ 39,90", 4.7f),
        Game("Superhot VR", "Tempo só anda quando você anda - 6DOF", "R$ 69,90", 4.7f),
        Game("Asgard's Wrath 2", "Espada e escudo", "R$ 149,90", 4.8f),
        Game("Gorilla Tag", "Tag com locomoção gorila", "Grátis", 4.6f),
        Game("Job Simulator", "Trabalhos divertidos", "R$ 39,90", 4.5f),
        Game("Tetris Effect", "Tetris volumétrico 3D", "R$ 59,90", 4.8f),
        Game("Puzzling Places", "Puzzles 3D photogrammetry", "R$ 49,90", 4.7f),
        Game("First Steps", "Tutorial hand tracking", "Grátis", 4.9f)
    )

    // Dock apps igual screenshots
    private val dockApps = listOf(
        "Store", "Browser", "Files", "Settings", "Facebook", "Instagram", "WhatsApp", "Spotify", "YouTube", "Netflix", "Beat Saber", "Pong"
    )

    fun getView(): View {
        try {
            return createMainView()
        } catch (e: Exception) {
            Log.e("MetaPort-UI", "Erro createMainView", e)
            return createFallbackView()
        }
    }

    private fun createMainView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Status bar 3D
        val statusBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(20, 20, 20, 20)
            gravity = Gravity.CENTER_VERTICAL
        }

        statusText = TextView(context).apply {
            text = if (isARCoreSupported) "MetaPort v2.3 - 6DOF ✅ - 9 Ambientes - 12 Jogos" else "MetaPort v2.3 - 3DOF Fallback - 9 Ambientes - 12 Jogos"
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        statusBar.addView(statusText)

        root.addView(statusBar)

        // Renderer 3D - OpenGL com ambientes
        try {
            cardboardRendererView = CardboardRendererView(context, arCoreManager)
            root.addView(cardboardRendererView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        } catch (e: Exception) {
            Log.e("MetaPort-UI", "Erro renderer", e)
            val placeholder = TextView(context).apply {
                text = "3D Renderer\n${if (isARCoreSupported) "6DOF Ativo" else "3DOF"}\n\nAmbiente: Tropical Island\n\nToque nos botões abaixo"
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#16213E"))
            }
            root.addView(placeholder, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }

        // App Library - Grid 3D igual Horizon OS
        val libraryLabel = TextView(context).apply {
            text = "App Library - 3D Spatial (12 Jogos + 8 Apps)"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 15, 20, 10)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }
        root.addView(libraryLabel)

        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = GameAdapter(games) { game ->
                launchGame(game)
            }
            setBackgroundColor(Color.parseColor("#0F0F1E"))
        }
        root.addView(recyclerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            400
        ))

        // Dock inferior 3D curvo igual ZentraXR
        val dockLabel = TextView(context).apply {
            text = "Dock 3D Curvo - Toque pra abrir"
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(20, 10, 20, 5)
            gravity = Gravity.CENTER
        }
        root.addView(dockLabel)

        val dockRecycler = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 6)
            adapter = DockAdapter(dockApps) { app ->
                launchDockApp(app)
            }
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(10, 10, 10, 20)
        }
        root.addView(dockRecycler, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            120
        ))

        // Botões ambientes
        val envScroll = HorizontalScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#16213E"))
        }
        val envLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 10, 10, 10)
        }
        environments.forEach { env ->
            val btn = Button(context).apply {
                text = env.name
                setOnClickListener { setEnvironment(env) }
                setBackgroundColor(Color.parseColor(env.color))
                setTextColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(5, 5, 5, 5)
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
            text = "MetaPort VR v2.3\n\nFallback UI\n\n9 Ambientes\n12 Jogos\n6DOF: ${if (isARCoreSupported) "Sim" else "Não"}\n\nSe crashar, reinstala e permite câmera"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setPadding(50, 50, 50, 50)
            gravity = Gravity.CENTER
        }
    }

    private fun setEnvironment(env: Environment) {
        try {
            Log.d("MetaPort-UI", "Set environment: ${env.name}")
            Toast.makeText(context, "Ambiente: ${env.name}", Toast.LENGTH_SHORT).show()
            cardboardRendererView?.setEnvironment(env.name)
            statusText?.text = "Ambiente: ${env.name} - ${if (isARCoreSupported) "6DOF ✅" else "3DOF"}"
        } catch (e: Exception) {
            Log.e("MetaPort-UI", "Erro setEnvironment", e)
        }
    }

    private fun launchGame(game: Game) {
        try {
            Log.d("MetaPort-UI", "Launch game: ${game.name}")
            Toast.makeText(context, "Iniciando ${game.name} - ${game.price}", Toast.LENGTH_SHORT).show()
            cardboardRendererView?.launchGame(game.name)
            statusText?.text = "Jogando: ${game.name}"
        } catch (e: Exception) {
            Log.e("MetaPort-UI", "Erro launchGame", e)
        }
    }

    private fun launchDockApp(app: String) {
        try {
            Toast.makeText(context, "Abrindo $app", Toast.LENGTH_SHORT).show()
            when (app) {
                "Store" -> {
                    // Mostra store
                }
                "Browser" -> {
                    // Mostra browser
                }
                "Settings" -> {
                    // Mostra settings
                }
                else -> {
                    // Tenta lançar como jogo
                    val game = games.find { it.name.contains(app, ignoreCase = true) }
                    if (game != null) launchGame(game)
                }
            }
        } catch (e: Exception) {
            Log.e("MetaPort-UI", "Erro launchDockApp", e)
        }
    }

    fun onResume() {
        try {
            cardboardRendererView?.onResume()
        } catch (e: Exception) {
            Log.e("MetaPort-UI", "Erro onResume", e)
        }
    }

    fun onPause() {
        try {
            cardboardRendererView?.onPause()
        } catch (e: Exception) {
            Log.e("MetaPort-UI", "Erro onPause", e)
        }
    }

    fun onDestroy() {
        try {
            cardboardRendererView?.onDestroy()
        } catch (e: Exception) {
            Log.e("MetaPort-UI", "Erro onDestroy", e)
        }
    }

    data class Environment(val name: String, val desc: String, val color: String, val isMixedReality: Boolean)
    data class Game(val name: String, val desc: String, val price: String, val rating: Float)

    class GameAdapter(
        private val games: List<Game>,
        private val onClick: (Game) -> Unit
    ) : RecyclerView.Adapter<GameAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(1001)
            val desc: TextView = view.findViewById(1002)
            val price: TextView = view.findViewById(1003)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val card = CardView(parent.context).apply {
                radius = 20f
                cardElevation = 8f
                setCardBackgroundColor(Color.parseColor("#2A2A3E"))
                val params = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    180
                )
                params.setMargins(8, 8, 8, 8)
                layoutParams = params
            }

            val layout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(15, 15, 15, 15)
                gravity = Gravity.CENTER
            }

            val nameView = TextView(parent.context).apply {
                id = 1001
                setTextColor(Color.WHITE)
                textSize = 13f
                maxLines = 2
                gravity = Gravity.CENTER
            }
            val descView = TextView(parent.context).apply {
                id = 1002
                setTextColor(Color.LTGRAY)
                textSize = 10f
                maxLines = 2
                gravity = Gravity.CENTER
            }
            val priceView = TextView(parent.context).apply {
                id = 1003
                setTextColor(Color.parseColor("#4FC3F7"))
                textSize = 11f
                gravity = Gravity.CENTER
            }

            layout.addView(nameView)
            layout.addView(descView)
            layout.addView(priceView)
            card.addView(layout)

            return ViewHolder(card)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val game = games[position]
            holder.name.text = game.name
            holder.desc.text = game.desc
            holder.price.text = "${game.price} ★${game.rating}"
            holder.itemView.setOnClickListener { onClick(game) }
        }

        override fun getItemCount() = games.size
    }

    class DockAdapter(
        private val apps: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<DockAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(2001)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val card = CardView(parent.context).apply {
                radius = 30f
                cardElevation = 6f
                setCardBackgroundColor(Color.parseColor("#3A3A4E"))
                val params = ViewGroup.MarginLayoutParams(120, 100)
                params.setMargins(6, 6, 6, 6)
                layoutParams = params
            }

            val text = TextView(parent.context).apply {
                id = 2001
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(5, 5, 5, 5)
            }
            card.addView(text)
            return ViewHolder(card)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.name.text = apps[position]
            holder.itemView.setOnClickListener { onClick(apps[position]) }
        }

        override fun getItemCount() = apps.size
    }
}
