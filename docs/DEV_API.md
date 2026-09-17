# MetaPort Dev API

Servidor HTTP/1.1 embutido no app, na porta **8765**, sem dependências externas.
Serve tanto JSON para ferramentas quanto uma página de console em `/`.

- Bind apenas na LAN do aparelho.
- Desligável em `Settings → Dev API server` ou por `POST /api/v1/settings`.
- CORS liberado (`Access-Control-Allow-Origin: *`) para chamar direto do navegador.

## Descoberta

Os endereços aparecem na tela **Dev API** dentro do headset e em
`GET /api/v1/status` (campo do dispositivo). Em código:
`DevApiServer.localAddresses()`.

## Índice

`GET /api/v1` lista os endpoints disponíveis.

### `GET /api/v1/status`
Estado completo do runtime.

```json
{
  "app": "MetaPort XR",
  "version": "3.0.0",
  "uptimeSec": "183.4",
  "stereo": true,
  "mixedReality": false,
  "handTracking": true,
  "environment": "nebula-void",
  "game": "pulse-blade",
  "arcore": {
    "available": true, "sessionActive": true, "running": true, "tracking": true,
    "depthApi": true, "planes": 4, "anchors": 0, "frames": 11023,
    "lightIntensity": 1.12
  },
  "input": {
    "down": false, "source": "magnet", "magnetEnabled": true, "magnetPresent": true,
    "fieldMagnitude": "48.21", "fieldBaseline": "43.07", "threshold": 18.0
  },
  "viewer": { "id": "cardboard-v2", "interLensDistance": 0.0639, "fovDegrees": 92.0 },
  "telemetry": { "fps": "71.8", "frameMs": "13.92", "drawCalls": 214 }
}
```

### `GET /api/v1/telemetry`
Métricas de quadro + últimos eventos do barramento.

### `GET /api/v1/hands`
Esqueleto completo das duas mãos.

```json
{
  "enabled": true, "processingMs": "0.62", "detected": 2,
  "right": {
    "hand": "right", "visible": true, "confidence": 0.87,
    "gesture": "Pinch", "pinch": 0.81, "palmSize": 0.089,
    "joints": [0.12, 1.31, -0.42, ...]
  }
}
```

`joints` são 63 floats: 21 juntas × (x, y, z) em metros, espaço mundial, na ordem OpenXR
(`WRIST`, `THUMB_CMC…THUMB_TIP`, `INDEX_MCP…`, `MIDDLE_…`, `RING_…`, `PINKY_…`).

### `GET /api/v1/planes`
Planos rastreados pelo ARCore: pose, extensão, orientação e estado.

### `GET /api/v1/games`
Lista os títulos registrados, com o que está rodando.

### `POST /api/v1/games/{id}/launch`
Inicia um jogo. Exemplo: `POST /api/v1/games/pulse-blade/launch`.

### `POST /api/v1/games/stop`
Volta ao hub.

### `GET /api/v1/environments` · `POST /api/v1/environments/{id}`
Lista e troca o ambiente (`nebula-void`, `cyber-city`, `space-station`, `forest-valley`,
`desert-oasis`, `dojo`, `passthrough`).

### `POST /api/v1/input`
Injeta entrada remota.

| `action` | efeito |
|---|---|
| `trigger` | pressiona o gatilho |
| `trigger_release` | solta o gatilho |
| `snap_left` / `snap_right` | giro por snap (usa o ângulo configurado) |
| `move` / `strafe` | eixo de locomoção (`value` de −1 a 1) |
| `restart` | reinicia o jogo atual |
| `back` | volta ao hub |

```bash
curl -X POST -d '{"action":"trigger","value":1}' http://<ip>:8765/api/v1/input
```

### `POST /api/v1/settings`
Ajusta runtime sem recompilar.

```bash
curl -X POST -d '{"key":"handTracking","value":"true"}' http://<ip>:8765/api/v1/settings
```

Chaves: `handTracking`, `stereo`, `mixedReality`, `devApi`, `showPlanes`, `volume`,
`uiScale`, `uiDistance`, `snapTurn`, `ipdAdjust`, `fov`, `renderScale`.

---

## API em processo

`devapi/MetaPortSdk.kt` é a superfície para código dentro do app:

```kotlin
MetaPortSdk.addListener { name, payload -> Log.d("MP", "$name $payload") }
MetaPortSdk.emit("my.event", mapOf("score" to 42))
MetaPortSdk.setSetting("difficulty", "hard")
```

Eventos publicados pelo runtime: `game.launched`, `game.exited`, `environment.changed`,
`hand.found`, `hand.lost`, `tracking.changed`, `plane.added`.

### Registrando um jogo

Implemente `games/Game` e registre antes da primeira criação do runtime:

```kotlin
class MyGame : GameBase() {
    override val id = "my-game"
    override val title = "My Game"
    override val tagline = "Descrição curta"
    override val category = "Arcade"
    override val accentIndex = 3

    override fun onEnter(ctx: GameContext) { /* reinicie estado */ }
    override fun onExit(ctx: GameContext) { /* libere estado */ }
    override fun update(ctx: GameContext) { /* física, entrada */ }
    override fun render(ctx: GameContext) { /* ctx.scene.push(...) */ }
    override fun hud() = listOf("Score" to score().toString())
}

GameRegistry.register(MyGame())
```

O jogo aparece automaticamente no hub, na biblioteca, na Dev API e no console web.

### Registrando um ambiente

Estenda `env/Environment3D`, defina `skyMode`, névoa, luzes e o `render(...)`, e chame
`Environments.register(...)` junto com os demais.

---

## Segurança

A Dev API é ferramenta de desenvolvimento: não tem autenticação e expõe telemetria e controle.
Mantenha-a desligada fora da rede de desenvolvimento e não publique builds com ela ligada em
rede não confiável.
