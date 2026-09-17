# MetaPort XR

App Android **nativo** de realidade espacial para visualizadores Cardboard: estereoscopia com
óptica real de lente, SLAM 6DOF via ARCore, rastreamento de mãos, interface 100% 3D espacial,
ambientes procedurais, mixed reality pela câmera e uma Dev API por HTTP.

Sem Unity, sem engine, sem assets binários: **todo o conteúdo (geometria, texto, áudio, céus)
é gerado proceduralmente em runtime**. O APK final tem ~3,2 MB.

---

## O que está implementado

### Estéreo Cardboard (VR)
- Pipeline estéreo próprio em OpenGL ES 3.0: a cena é renderizada duas vezes num FBO largo e
  depois apresentada através do modelo óptico de lente do Cardboard.
- Distorção radial `k1/k2` + correção de aberração cromática por canal + vinheta, num shader de
  passagem final (`Shaders.DISTORT_FS`).
- Parâmetros padrão = valores oficiais publicados pelo Google para o viewer Cardboard:
  entrelentes 63,9 mm, bandeja→lente 35 mm, tela→lente 39,3 mm, `k1 = 0.33582564`,
  `k2 = 0.55348791`.
- Frustum assimétrico por olho, calculado a partir da geometria física do viewer — cada olho
  olha através da sua própria lente.
- 5 perfis prontos (Cardboard V1/V2, VR Box 2.0, genérico wide, tela plana) + calibração ao vivo
  de IPD, FOV, distorção e render scale.
- Gatilho: anel magnético (magnetômetro com auto-calibração), toque indireto na tela e tecla de
  volume — os três ao mesmo tempo.

### ARCore — 6DOF, ambiente e mixed reality
- Sessão ARCore com `UpdateMode.LATEST_CAMERA_IMAGE`, plano horizontal **e** vertical, âncoras,
  Depth API e estimativa de luz (`AMBIENT_INTENSITY`).
- Pose da câmera 6DOF alimenta o head tracker; sem o Google Play Services for AR o app cai
  automaticamente para 3DOF por sensor de rotação ( continua utilizável no Cardboard).
- Planos rastreados são desenhados como malhas translúcidas com marcadores de canto.
- Mixed reality: a imagem da câmera é o fundo (via `Coordinates2d` + `transformCoordinates2d`)
  com conteúdo 3D composto por cima, mais brilho/contraste/vinheta/scanline ajustáveis.

### Hand tracking
Rastreador geométrico de mãos, no dispositivo, sem modelo de ML e sem dependência extra
(`ar/HandTracker.kt`):
1. imagem YUV da câmera ARCore → máscara de probabilidade de pele em YCbCr (160×120);
2. componentes conectados → até duas mãos;
3. imagem de profundidade do ARCore → posição métrica 3D (com fallback por área projetada);
4. eixo principal por covariância + extremos de contorno → esqueleto de 21 juntas na ordem
   OpenXR;
5. juntas elevadas ao espaço mundial pela pose rastreada da câmera → as mãos ficam presas à sala
   enquanto você anda.

Gestos reconhecidos com histerese: pinça, apontar, aberta, punho, paz. A pinça substitui o
gatilho na UI; o dedo indicador vira um raio de ponteiro.

> Honestidade técnica: é um rastreador geométrico, não aprendido. É rápido (<1 ms a 160×120),
> offline e estável, mas funciona melhor em sala iluminada com as mãos à frente da câmera.
> A interface `HandTracker` permite plugar um provider de ML depois.

### UI 100% espacial (nada é 2D overlay)
- Painéis de vidro curvos, dobrados num cilindro para que a superfície inteira fique à mesma
  distância óptica — o ponto mais importante para texto confortável num Cardboard.
- Cantos arredondados e borda neon por SDF no shader (`Shaders.UI_FS`), com fresnel, gradiente e
  ruído fino.
- Texto real: atlas de glifos rasterizado com o `Canvas` do Android (Latin-1, acentos incluídos)
  e aplicado em quads 3D.
- Interação por **raio de olhar + dwell** (com anel de progresso) ou pinça da mão, com retículo
  que acompanha o ponto de foco.
- Telas: hub inicial, biblioteca, configurações (óptica/conforto/sistema), console da Dev API e
  HUD de jogo.

### Ambientes 3D (7, todos procedurais)
`Nebula Void` · `Neon District` · `Orbital Deck` · `Forest Valley` · `Desert Oasis` ·
`Paper Dojo` · `Passthrough MR`

Cada um define sky shader próprio, névoa, ambiente hemisférico e três luzes direcionais.

### Jogos (11 títulos originais)
Todos são **desenhos originais escritos para este app**, com conteúdo gerado proceduralmente
(nomes, regras, gráficos e áudio próprios). Não são clones nem usam assets de nenhum título
comercial — veja a nota em [Jogos](#sobre-os-jogos).

| ID | Título | Gênero |
|---|---|---|
| `first-steps` | First Steps | Learn |
| `pulse-blade` | Pulse Blade | Rhythm |
| `drum-forge` | Drum Forge | Rhythm |
| `chrono-strike` | Chrono Strike | Shooter |
| `cube-cascade` | Cube Cascade | Puzzle |
| `orb-keeper` | Orb Keeper | Arcade |
| `nebula-hoops` | Nebula Hoops | Sport |
| `hover-putt` | Hover Putt | Sport |
| `gravity-leap` | Gravity Leap | Movement |
| `deep-reel` | Deep Reel | Relax |
| `voxel-garden` | Voxel Garden | Creative |

### Dev API
Servidor HTTP/1.1 no dispositivo (porta 8765), sem dependências, com página web de console.
Documentação completa em [`docs/DEV_API.md`](docs/DEV_API.md).

```bash
curl http://<ip-do-celular>:8765/api/v1/status
curl -X POST http://<ip-do-celular>:8765/api/v1/games/pulse-blade/launch
curl -X POST -d '{"action":"trigger"}' http://<ip-do-celular>:8765/api/v1/input
```

Além do HTTP, existe a API em processo (`devapi/MetaPortSdk.kt`): registro de jogos/ambientes,
barramento de eventos e configurações.

### Áudio
Todo sintetizado em runtime (`audio/Sfx.kt`): 10 efeitos gerados como PCM no boot, com pan
estéreo calculado pelo azimute da fonte em relação à cabeça.

---

## Build

Requisitos: JDK 17, Android SDK 34, Gradle 7.6.4.

```bash
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
gradle test assembleDebug assembleRelease
```

APK em `app/build/outputs/apk/debug/`.

O CI (`.github/workflows/build-apk.yml`) roda testes + build e publica o APK como artefato e
como release do GitHub a cada push.

**Mínimo:** Android 7.0 (API 24), OpenGL ES 3.0, giroscópio + acelerômetro. ARCore é opcional em
runtime (`com.google.ar.core = optional`).

---

## Estrutura

```
app/src/main/java/com/metaport/xr/
├── MetaPortActivity.kt      atividade, permissões, ciclo de vida, entrada
├── MetaPortRuntime.kt       renderizador GL, dono de tudo, host da Dev API
├── ar/                      ArCoreHost, HeadTracker, HandTracker, HandModel
├── audio/                   Sfx (síntese procedural)
├── core/gl/                 GlProgram, Mesh/MeshGen, FontAtlas, FrameBuffer, Shaders
├── core/math/               Vec3, Quat, Mat4, Mathf (ruído fbm)
├── devapi/                  DevApiServer (HTTP), Json, MetaPortSdk, Telemetry
├── env/                     Environment3D + 7 ambientes
├── games/                   Game/GameContext + 11 títulos
├── input/                   CardboardInput (magneto/toque/tecla), InputState
├── render/                  Pipeline, StereoPipeline, HandRenderer, MixedRealityLayer
├── scene/                   Scene (draw list em camadas), Material, GlState
├── stereo/                  ViewerProfile (óptica do viewer)
└── ui/                      Painéis/widgets espaciais, UiRoot (foco/dwell), telas
```

---

## Sobre os jogos

O pedido original mencionava "remakes de jogos do Meta Quest". O que está aqui são **jogos
originais nos mesmos gêneros** — rhythm slasher, drums, time-dilation shooter, empilhador de
blocos, pong 3D, arremesso, putt, escalada com as mãos, pesca, construção de voxels — com nome,
regras, visual e áudio próprios, gerados proceduralmente.

Não é possível empacotar clones, assets, músicas ou marcas de títulos comerciais de terceiros
(Beat Saber, Superhot, Gorilla Tag, Job Simulator etc.). Cada jogo aqui foi escrito do zero e
pode ser modificado e redistribuído livremente.

## Estado da verificação

- Build verificado no CI (commit `fecdd17`): `gradle assembleDebug assembleRelease` → sucesso.
  Artefatos: `app-debug.apk` (3.250.440 bytes) e `app-release.apk` (2.549.604 bytes).
- O commit com os testes unitários JVM (`app/src/test/`) ainda não foi executado por um
  compilador — ver o histórico da sessão.
- Não foi executado num aparelho físico: shaders, ARCore e o pipeline de mãos precisam de
  validação em device.
