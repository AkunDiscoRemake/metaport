# MetaPort Dev API - Documentação Completa

## Inicialização

```csharp
MetaPortAPI.Initialize();
```

## Criar Janela Espacial 3D

```csharp
// Janela curva 3D com profundidade real - não é quad 2D!
var window = MetaPortAPI.CreateSpatialWindow("Meu Navegador", new Vector3(1.5f, 1f, 0.08f));

// Posição customizada no mundo real (ARCore anchor)
Vector3 pos = envTracker.GetBestWindowPlacement(new Vector3(1.5f, 1f, 0.08f));
var window2 = MetaPortAPI.CreateSpatialWindow("YouTube", new Vector3(1.2f, 0.8f, 0.05f), pos);

// Conteúdo via RenderTexture (WebView, etc)
window.SetContent(myRenderTexture);
```

## Hand Tracking

```csharp
// Checar se mão está rastreando
if (MPInput.IsRightHandTracking)
{
    Vector3 pinchPos = MPInput.GetPinchPosition(Handedness.Right);
    float pinchStrength = MPInput.GetPinchStrength(Handedness.Right);
    
    if (pinchStrength > 0.8f)
    {
        // Grab!
    }
}

// Joint específico
if (MetaPortAPI.TryGetHandJoint(Handedness.Right, XRHandJoint.IndexTip, out Vector3 pos, out Quaternion rot))
{
    // Use pos
}
```

## 6DOF SLAM

```csharp
if (MetaPortAPI.Is6DoFTracking)
{
    Vector3 headPos = MetaPortAPI.GetHeadPosition();
    // headPos muda quando usuário anda fisicamente!
}

// Criar anchor espacial persistente
var anchorSystem = FindObjectOfType<ARCoreAnchorSystem>();
ARAnchor anchor = anchorSystem.CreateAnchor(position, rotation);
// anchor fica fixo no mundo real
```

## Mixed Reality

```csharp
// Trocar modo
MetaPortAPI.SetXRMode(MRModeManager.XRMode.VirtualReality); // Só ambiente 3D
MetaPortAPI.SetXRMode(MRModeManager.XRMode.MixedReality); // Ambiente + câmera
MetaPortAPI.SetXRMode(MRModeManager.XRMode.PassthroughOnly); // Só câmera + janelas

// Ouvir mudanças
MRModeManager.Instance.OnModeChanged += (mode) => Debug.Log($"Modo: {mode}");
```

## Ambientes 3D

```csharp
var envManager = EnvironmentManager.Instance;
envManager.LoadEnvironment("TropicalIsland"); // TropicalIsland, Lounge, VoidSpace, JapaneseDojo, DesertOasis, Passthrough

// Criar ambiente customizado
var customEnv = new MPEnvironment{
    Id = "MeuAmbiente",
    Name = "Meu Ambiente",
    IsMixedReality = false,
    AmbientColor = Color.blue
};
MetaPortAPI.SetEnvironment(customEnv);
```

## Jogos

```csharp
var gameManager = GameManager.Instance;
gameManager.LaunchGame("BeatSaberRemake"); // ou PongSpatial, FishingVR, etc
gameManager.ExitGame();
```

## Input Cardboard

```csharp
if (MetaPortAPI.GetTriggerDown())
{
    Vector3 gazePoint = MetaPortAPI.GetGazePoint();
    // Usuário apertou botão Cardboard olhando para gazePoint
}
```

## Exemplo Completo - App de Fotos 3D Espacial

```csharp
public class FotosApp : MonoBehaviour
{
    public Texture2D[] fotos;

    void Start()
    {
        MetaPortAPI.Initialize();

        // Janela principal
        var mainWindow = MetaPortAPI.CreateSpatialWindow("Fotos", new Vector3(1.2f, 0.8f, 0.05f));
        
        // Grid de fotos em 3D
        for (int i=0; i<fotos.Length; i++)
        {
            var fotoWindow = MetaPortAPI.CreateSpatialWindow($"Foto {i}", new Vector3(0.4f, 0.4f, 0.02f), 
                mainWindow.transform.position + new Vector3((i%3-1)*0.5f, (i/3)*0.5f, 0.2f));
            fotoWindow.SetContent(fotos[i]);
        }
    }

    void Update()
    {
        // Interação com mão
        if (MPInput.IsRightHandTracking && MPInput.GetPinchStrength(Handedness.Right) > 0.9f)
        {
            Vector3 pinch = MPInput.GetPinchPosition(Handedness.Right);
            Collider[] hits = Physics.OverlapSphere(pinch, 0.05f);
            foreach(var hit in hits)
            {
                var window = hit.GetComponent<MPWindow>();
                if (window != null) window.Focus();
            }
        }
    }
}
```

## Performance Tips

- Janelas 3D custam draw calls - limite a 5 simultâneas
- Hand tracking a 30fps - não tente 60fps
- Use `ARCoreEnvironmentTracker.PlaneCount` para decidir quando mostrar UI de ambiente
- Desative `enableMeshing` se celular for fraco
