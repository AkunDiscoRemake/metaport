# INSTALAÇÃO RÁPIDA - MetaPort Cardboard VR

## Para quem quer só usar (sem Unity)

1. Baixe `MetaPort_CardboardVR_SDK_v1.0.apk` (build via Unity)
2. Instale no Android (permita fontes desconhecidas)
3. Abra, permita câmera
4. Coloque no Cardboard
5. Pronto! Você está no Horizon OS de Cardboard

## Para devs (Unity)

### 1. Novo projeto
- Unity 2022.3 LTS
- Template 3D URP

### 2. Importar pacotes obrigatórios
Window -> Package Manager -> + Add by name:

```
com.google.xr.cardboard
com.unity.xr.arfoundation
com.unity.xr.arcore
com.unity.xr.openxr
com.unity.xr.management
```

### 3. Importar MetaPort
Assets -> Import Package -> Custom Package -> `MetaPort_CardboardVR_SDK_v1.0.unitypackage`

### 4. Setup
Menu: `MetaPort -> Setup Project`

### 5. Build
Menu: `MetaPort -> Build Android APK`

## Estrutura da cena Main

```
- MetaPort_Rig (CardboardRig + ARCore6DoFManager + MonadoOpenXRLoader + MRModeManager + HandTrackingManager + EnvironmentManager + GameManager + DockManager + AppLibrary)
  - CenterEye
    - LeftEye (Camera)
    - RightEye (Camera)
  - LeftHand
  - RightHand
  - EnvironmentRoot
  - GameRoot
  - DockRoot
```

## Testar no Editor

- Segure H para simular mão
- Mouse para gaze
- M para trocar VR/MR

## Dicas de Performance

- Celular intermediário: desative `enableMeshing` e `enableHandMesh`
- Celular fraco: use `targetFPS = 30` no HandTrackingManager
- Celular forte: ative tudo + `enableDepthOcclusion`

## FAQ

**Q: Precisa de controle?**
A: Não! Só mãos + botão Cardboard

**Q: Funciona sem ARCore?**
A: Sim, mas fica só 3DOF (sem andar)

**Q: Funciona no iOS?**
A: Cardboard sim, mas ARCore 6DOF só Android. Para iOS usaria ARKit (futuro)

**Q: É realmente 3D spatial?**
A: SIM! Nenhum Canvas Screen Space. Tudo MeshRenderer 3D com profundidade, curvatura e física.
