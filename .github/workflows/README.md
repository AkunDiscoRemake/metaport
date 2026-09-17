# GitHub Actions - Build Automático APK

Este workflow compila o APK sem precisar de Unity local!

## Como funciona:

1. Você dá push no GitHub
2. GitHub Actions roda `game-ci/unity-builder`
3. Compila APK Android automaticamente
4. Cria Release com APK + UnityPackage

## Para Unity Build funcionar, precisa adicionar Secrets no GitHub:

Vai em Settings -> Secrets and variables -> Actions -> New repository secret:

- `UNITY_LICENSE` - Sua licença Unity (pega via game-ci)
- `UNITY_EMAIL` - Seu email Unity
- `UNITY_PASSWORD` - Sua senha Unity

Ou usa Unity Gaming Services com Personal License (free).

### Como pegar UNITY_LICENSE:

1. Vai em https://game.ci/docs/github/activation
2. Segue tutorial pra gerar .ulf file
3. Cola conteúdo no secret UNITY_LICENSE

## Build Nativo (sem Unity):

O job `buildAndroidNoUnity` compila o projeto `android-native/` que é um app Android nativo com ARCore + Cardboard + MediaPipe, sem precisar Unity!

Esse build funciona sem secrets, só com Android SDK.

## Resultado:

A cada push, você recebe:
- `MetaPort-APK` - APK Unity
- `MetaPort-Native-APK` - APK nativo
- `MetaPort-UnityPackage` - Package
- Release automático com tag v2.1-{run_number}

Baixa em Actions -> Artifacts ou Releases
