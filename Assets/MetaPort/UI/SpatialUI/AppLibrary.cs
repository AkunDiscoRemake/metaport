using UnityEngine;
using System.Collections.Generic;
using System.Linq;

namespace MetaPort.UI.SpatialUI
{
    /// <summary>
    /// App Library - 3D spatial grid like Meta Quest
    /// </summary>
    public class AppLibrary : MonoBehaviour
    {
        public static AppLibrary Instance;

        [Header("Library - 3D Spatial")]
        public Transform gridRoot;
        public float cellSize = 0.35f;
        public float cellDepth = 0.08f;
        public int columns = 4;
        public float spacing = 0.05f;
        public Material libraryMaterial;

        [Header("Apps")]
        public List<MPAppDefinition> allApps = new List<MPAppDefinition>();

        private List<SpatialWindow> _openWindows = new List<SpatialWindow>();

        void Awake()
        {
            Instance = this;
            if (gridRoot == null) gridRoot = transform;
            CreateLibraryMesh();
            PopulateApps();
        }

        void CreateLibraryMesh()
        {
            var mf = gridRoot.GetComponent<MeshFilter>();
            if (mf == null) mf = gridRoot.gameObject.AddComponent<MeshFilter>();
            var mr = gridRoot.GetComponent<MeshRenderer>();
            if (mr == null) mr = gridRoot.gameObject.AddComponent<MeshRenderer>();

            // Background panel 3D
            float w = columns * (cellSize + spacing) + spacing;
            int rows = Mathf.CeilToInt(allApps.Count / (float)columns);
            float h = rows * (cellSize + spacing) + spacing + 0.2f; // header

            Mesh mesh = new Mesh();
            Vector3[] verts = {
                new Vector3(-w/2,-h/2,0), new Vector3(w/2,-h/2,0), new Vector3(w/2,h/2,0), new Vector3(-w/2,h/2,0),
                new Vector3(-w/2,-h/2,0.05f), new Vector3(w/2,-h/2,0.05f), new Vector3(w/2,h/2,0.05f), new Vector3(-w/2,h/2,0.05f)
            };
            int[] tris = {0,1,2,0,2,3,4,7,6,4,6,5};
            mesh.vertices = verts;
            mesh.triangles = tris;
            mesh.RecalculateNormals();
            mf.mesh = mesh;

            if (libraryMaterial == null)
            {
                libraryMaterial = new Material(Shader.Find("MetaPort/SpatialWindow"));
                libraryMaterial.SetColor("_BaseColor", new Color(0.15f,0.18f,0.22f,0.95f));
            }
            mr.material = libraryMaterial;
        }

        void PopulateApps()
        {
            // Add built-in apps + games
            if (allApps.Count == 0)
            {
                allApps.Add(new MPAppDefinition{ appId="com.metaport.store", appName="Store", category="System", iconColor=new Color(0.95f,0.6f,0.15f), isGame=false });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.browser", appName="Browser", category="System", iconColor=Color.blue, isGame=false });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.files", appName="Files", category="System", iconColor=new Color(0.5f,0.7f,0.9f), isGame=false });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.settings", appName="Settings", category="System", iconColor=Color.gray, isGame=false });
                
                // Games - Quest remakes
                allApps.Add(new MPAppDefinition{ appId="com.metaport.beatsaber", appName="Beat Saber", category="Games", iconColor=new Color(0.9f,0.15f,0.25f), isGame=true, sceneName="BeatSaberRemake" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.fishing", appName="Real VR Fishing", category="Games", iconColor=new Color(0.2f,0.6f,0.9f), isGame=true, sceneName="FishingVR" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.minigolf", appName="Walkabout Mini Golf", category="Games", iconColor=new Color(0.3f,0.8f,0.3f), isGame=true, sceneName="MiniGolf" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.synthriders", appName="Synth Riders", category="Games", iconColor=new Color(0.8f,0.2f,0.9f), isGame=true, sceneName="BeatSaberRemake" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.myst", appName="Myst", category="Games", iconColor=new Color(0.6f,0.8f,0.9f), isGame=true, sceneName="PuzzlingPlaces" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.vermillion", appName="Vermillion", category="Games", iconColor=new Color(0.9f,0.5f,0.3f), isGame=true, sceneName="PuzzlingPlaces" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.tetris", appName="Tetris Effect", category="Games", iconColor=new Color(0.2f,0.3f,0.9f), isGame=true, sceneName="Tetris3D" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.residentevil", appName="Resident Evil 4", category="Games", iconColor=new Color(0.5f,0.1f,0.1f), isGame=true, sceneName="FirstSteps" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.smashdrums", appName="Smash Drums", category="Games", iconColor=new Color(0.9f,0.6f,0.1f), isGame=true, sceneName="SmashDrums" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.puzzling", appName="Puzzling Places", category="Games", iconColor=new Color(0.9f,0.8f,0.3f), isGame=true, sceneName="PuzzlingPlaces" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.wooworld", appName="Wooworld", category="Games", iconColor=new Color(0.3f,0.8f,0.6f), isGame=true, sceneName="FishingVR" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.firststeps", appName="First Steps", category="Games", iconColor=new Color(0.4f,0.6f,0.9f), isGame=true, sceneName="FirstSteps" });
                allApps.Add(new MPAppDefinition{ appId="com.metaport.pong", appName="Pong Spatial", category="Games", iconColor=Color.white, isGame=true, sceneName="PongSpatial" });
            }

            // Create 3D icons in grid
            for (int i = 0; i < allApps.Count; i++)
            {
                int col = i % columns;
                int row = i / columns;
                Vector3 pos = new Vector3(
                    - (columns * (cellSize + spacing)) * 0.5f + col * (cellSize + spacing) + cellSize * 0.5f + spacing,
                    (Mathf.CeilToInt(allApps.Count/(float)columns) * (cellSize+spacing))*0.5f - row * (cellSize+spacing) - cellSize*0.5f - spacing - 0.1f,
                    0.06f
                );
                CreateAppIcon(allApps[i], pos);
            }
        }

        void CreateAppIcon(MPAppDefinition app, Vector3 localPos)
        {
            var go = new GameObject($"App_{app.appName}");
            go.transform.SetParent(gridRoot, false);
            go.transform.localPosition = localPos;

            var mf = go.AddComponent<MeshFilter>();
            var mr = go.AddComponent<MeshRenderer>();
            
            // 3D app tile with depth
            Mesh mesh = GenerateAppTileMesh(cellSize, cellDepth, 0.04f);
            mf.mesh = mesh;

            var mat = new Material(Shader.Find("MetaPort/Icon3D"));
            mat.SetColor("_BaseColor", app.iconColor);
            mat.SetFloat("_Metallic", 0.1f);
            mat.SetFloat("_Smoothness", 0.8f);
            mr.material = mat;

            var col = go.AddComponent<BoxCollider>();
            col.size = new Vector3(cellSize, cellSize, cellDepth);

            var icon = go.AddComponent<AppIcon>();
            icon.appDefinition = app;
            icon.library = this;
        }

        Mesh GenerateAppTileMesh(float size, float depth, float radius)
        {
            Mesh m = new Mesh();
            float s = size*0.5f;
            Vector3[] verts = {
                new Vector3(-s,-s,0), new Vector3(s,-s,0), new Vector3(s,s,0), new Vector3(-s,s,0),
                new Vector3(-s,-s,depth), new Vector3(s,-s,depth), new Vector3(s,s,depth), new Vector3(-s,s,depth)
            };
            int[] tris = {0,1,2,0,2,3,4,7,6,4,6,5,0,4,5,0,5,1,1,5,6,1,6,2,2,6,7,2,7,3,3,7,4,3,4,0};
            m.vertices = verts;
            m.triangles = tris;
            m.RecalculateNormals();
            return m;
        }

        public void LaunchApp(string appName)
        {
            var app = allApps.FirstOrDefault(a => a.appName == appName);
            if (app == null) app = allApps.FirstOrDefault(a => a.appId == appName);
            if (app == null) return;

            if (app.isGame)
            {
                // Launch game
                var gameManager = FindObjectOfType<Games.GameManager>();
                if (gameManager != null) gameManager.LaunchGame(app.sceneName);
                else Debug.Log($"[MetaPort] Launching game {app.appName} -> {app.sceneName}");
            }
            else
            {
                // Launch app as spatial window
                SpawnAppWindow(app);
            }
        }

        void SpawnAppWindow(MPAppDefinition app)
        {
            var cam = Camera.main;
            Vector3 spawnPos = cam.transform.position + cam.transform.forward * 1.5f;
            
            // Check environment tracker for best placement
            var envTracker = FindObjectOfType<Core.ARCoreEnvironmentTracker>();
            if (envTracker != null) spawnPos = envTracker.GetBestWindowPlacement(new Vector3(1.2f,0.8f,0.05f));

            var go = new GameObject($"Window_{app.appName}");
            go.transform.position = spawnPos;
            go.transform.rotation = Quaternion.LookRotation(go.transform.position - cam.transform.position);

            var window = go.AddComponent<SpatialWindow>();
            window.appName = app.appName;
            window.appId = app.appId;
            window.size = new Vector3(1.2f, 0.8f, 0.05f);

            // Add app-specific content
            if (app.appId == "com.metaport.browser")
            {
                go.AddComponent<BrowserWindow>();
            }
            else if (app.appId == "com.metaport.settings")
            {
                go.AddComponent<SettingsPanel>();
            }

            _openWindows.Add(window);
        }
    }

    [System.Serializable]
    public class MPAppDefinition
    {
        public string appId;
        public string appName;
        public string category;
        public Color iconColor;
        public bool isGame;
        public string sceneName;
        public Sprite icon;
    }

    public class AppIcon : MonoBehaviour, IHandInteractable
    {
        public MPAppDefinition appDefinition;
        public AppLibrary library;

        public void OnHandHover(Vector3 point, Core.MonadoOpenXRLoader.Handedness hand)
        {
            transform.localScale = Vector3.one * 1.1f;
        }

        public void OnHandGrab(Core.MonadoOpenXRLoader.Handedness hand, Vector3 grabPoint, Quaternion grabRot)
        {
            library.LaunchApp(appDefinition.appName);
        }

        public void OnHandRelease() => transform.localScale = Vector3.one;
        public void OnTriggerEnter(Vector3 point) => library.LaunchApp(appDefinition.appName);
    }

    public class BrowserWindow : MonoBehaviour
    {
        // 3D browser content
        void Start()
        {
            // Would create WebView texture in 3D
        }
    }

    public class SettingsPanel : MonoBehaviour
    {
        // 3D settings like in images
    }
}
