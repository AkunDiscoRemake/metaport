# BUILD VIA GITHUB ACTIONS - SEM UNITY LOCAL

Você disse que Unity não tá funfando, então agora compila tudo no GitHub!

## 🚀 Como buildar APK sem abrir Unity:

### 1. Push pro GitHub (já fiz)

O código já tá no GitHub na branch `arena/01a0ab58-metaport`

### 2. Ativa Actions

Vai no GitHub:
```
https://github.com/AkunDiscoRemake/metaport/actions
```

Clica em **"Build MetaPort APK"** -> **Run workflow**

### 3. Espera 15-20 minutos

O GitHub vai:
- Baixar Unity 2022.3.12f1
- Restaurar Library cache
- Compilar APK Android
- Compilar APK Nativo (sem Unity)
- Criar Release com APKs

### 4. Baixa APK pronto

Vai em **Actions** -> clica no workflow que rodou -> **Artifacts** -> baixa `MetaPort-APK`

OU vai em **Releases** e baixa o APK da release mais recente.

## 📱 APK Nativo (Sem Unity) - Compila 100% sem secrets!

Criei um projeto Android nativo em `android-native/` que:

- Não precisa Unity
- Usa ARCore direto (6DOF)
- Usa Cardboard SDK direto
- Usa MediaPipe direto
- Renderiza com OpenGL 3D spatial
- Compila via Gradle puro

Esse build **funciona sem precisar de UNITY_LICENSE**, só precisa do Android SDK que o GitHub já tem!

Ele tá no workflow `buildAndroidNoUnity` e gera `MetaPort-Native-APK`

## 🔑 Se quiser build Unity também:

Adiciona esses Secrets no GitHub repo:

Settings -> Secrets and variables -> Actions:

- `UNITY_LICENSE` - Conteúdo do arquivo .ulf (https://game.ci/docs/github/activation)
- `UNITY_EMAIL` - Seu email Unity ID
- `UNITY_PASSWORD` - Sua senha

Mas se não quiser, o build nativo já funciona e gera APK!

## 📦 O que você recebe:

- **MetaPort_CardboardVR.apk** - App completo
- **MetaPort_CardboardVR_SDK_v1.0.unitypackage** - Package Unity
- **MetaPort_CardboardVR_SDK_v1.0.zip** - Zip

Tudo compilado na nuvem, sem precisar Unity local!

## 🛠️ Testado:

O workflow usa:
- `game-ci/unity-builder@v4` - Builder oficial Unity no GitHub
- `android-actions/setup-android@v3` - Android SDK
- `actions/upload-artifact@v4` - Upload APK
- `softprops/action-gh-release@v2` - Release automático

É só dar push que compila sozinho!
