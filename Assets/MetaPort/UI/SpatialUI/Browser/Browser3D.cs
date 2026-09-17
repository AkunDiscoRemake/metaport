using UnityEngine;

namespace MetaPort.UI.SpatialUI.Browser
{
    /// <summary>
    /// Browser 3D espacial - navega web em janela curva 3D
    /// Igual screenshot NovaMr com atalhos Spotify, YouTube etc
    /// </summary>
    public class Browser3D : MonoBehaviour
    {
        [Header("Browser 3D")]
        public string currentUrl = "https://www.google.com";
        public RenderTexture webViewTexture;
        public Material webViewMaterial;
        public Transform addressBar; // 3D address bar com profundidade

        [Header("Atalhos - igual imagem")]
        public BrowserShortcut[] shortcuts = new BrowserShortcut[]
        {
            new BrowserShortcut{ name="Google", url="https://google.com", color=new Color(0.3f,0.6f,1f) },
            new BrowserShortcut{ name="YouTube", url="https://youtube.com", color=new Color(1f,0.2f,0.2f) },
            new BrowserShortcut{ name="Discord", url="https://discord.com", color=new Color(0.4f,0.5f,0.9f) },
            new BrowserShortcut{ name="TikTok", url="https://tiktok.com", color=Color.black },
            new BrowserShortcut{ name="Facebook", url="https://facebook.com", color=new Color(0.2f,0.4f,0.8f) },
            new BrowserShortcut{ name="Instagram", url="https://instagram.com", color=new Color(0.8f,0.3f,0.6f) },
            new BrowserShortcut{ name="Reddit", url="https://reddit.com", color=new Color(1f,0.4f,0.1f) },
            new BrowserShortcut{ name="Netflix", url="https://netflix.com", color=new Color(0.9f,0.1f,0.1f) },
            new BrowserShortcut{ name="Spotify", url="https://spotify.com", color=new Color(0.1f,0.8f,0.3f) },
            new BrowserShortcut{ name="YouTube Music", url="https://music.youtube.com", color=Color.white },
            new BrowserShortcut{ name="Crunchyroll", url="https://crunchyroll.com", color=new Color(1f,0.6f,0.1f) },
            new BrowserShortcut{ name="HBO Max", url="https://max.com", color=new Color(0.3f,0.1f,0.6f) },
        };

        private SpatialWindow _window;

        void Awake()
        {
            _window = GetComponent<SpatialWindow>();
            CreateShortcuts3D();
            SetupWebView();
        }

        void SetupWebView()
        {
            // Em build real, usaria plugin WebView (UniWebView, 3D WebView)
            // Aqui cria RenderTexture placeholder
            if (webViewTexture == null)
            {
                webViewTexture = new RenderTexture(1920, 1080, 0);
                webViewTexture.Create();
            }

            if (_window != null)
            {
                _window.appRenderTexture = webViewTexture;
            }
        }

        void CreateShortcuts3D()
        {
            // Cria atalhos como tiles 3D com profundidade - igual screenshot
            Transform gridRoot = new GameObject("ShortcutsGrid_3D").transform;
            gridRoot.SetParent(transform, false);
            gridRoot.localPosition = new Vector3(0, -0.1f, 0.06f);

            int cols = 4;
            float cellSize = 0.22f;
            float spacing = 0.03f;

            for (int i = 0; i < shortcuts.Length; i++)
            {
                int col = i % cols;
                int row = i / cols;
                Vector3 pos = new Vector3(
                    (col - cols*0.5f + 0.5f) * (cellSize + spacing),
                    -row * (cellSize + spacing),
                    0
                );

                var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
                go.name = $"Shortcut_{shortcuts[i].name}_3D";
                go.transform.SetParent(gridRoot, false);
                go.transform.localPosition = pos;
                go.transform.localScale = new Vector3(cellSize, cellSize*0.6f, 0.02f);

                var mat = new Material(Shader.Find("MetaPort/Icon3D"));
                mat.SetColor("_BaseColor", shortcuts[i].color);
                go.GetComponent<Renderer>().material = mat;

                var shortcut = go.AddComponent<Shortcut3D>();
                shortcut.data = shortcuts[i];
                shortcut.browser = this;
            }
        }

        public void NavigateTo(string url)
        {
            currentUrl = url;
            Debug.Log($"[Browser3D] Navegando para: {url}");
            // Em real: webView.Load(url)
        }

        public void NavigateToShortcut(BrowserShortcut shortcut) => NavigateTo(shortcut.url);
    }

    [System.Serializable]
    public class BrowserShortcut
    {
        public string name;
        public string url;
        public Color color;
        public Sprite icon;
    }

    public class Shortcut3D : MonoBehaviour, IHandInteractable
    {
        public BrowserShortcut data;
        public Browser3D browser;

        public void OnHandHover(Vector3 point, Core.MonadoOpenXRLoader.Handedness hand) => transform.localScale *= 1.05f;
        public void OnHandGrab(Core.MonadoOpenXRLoader.Handedness hand, Vector3 grabPoint, Quaternion grabRot) => browser.NavigateToShortcut(data);
        public void OnHandRelease() => transform.localScale = new Vector3(0.22f, 0.22f*0.6f, 0.02f);
        public void OnTriggerEnter(Vector3 point) => browser.NavigateToShortcut(data);
    }
}
