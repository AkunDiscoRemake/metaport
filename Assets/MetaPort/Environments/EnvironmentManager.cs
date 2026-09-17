using UnityEngine;
using System.Collections.Generic;

namespace MetaPort.Environments
{
    /// <summary>
    /// Environment Manager - 3D spatial environments like Horizon OS
    /// Tropical island, lounge, void, Japanese dojo etc.
    /// </summary>
    public class EnvironmentManager : MonoBehaviour
    {
        public static EnvironmentManager Instance;

        [Header("Environments - 3D Spatial")]
        public List<EnvironmentDefinition> environments = new List<EnvironmentDefinition>();
        public string currentEnvironmentId = "TropicalIsland";
        public Transform environmentRoot;
        public Material skyboxMaterial;

        [Header("Lighting")]
        public Light sunLight;
        public Color ambientColor = new Color(0.5f,0.6f,0.8f);

        private GameObject _currentEnvInstance;
        private Dictionary<string, EnvironmentDefinition> _envMap = new Dictionary<string, EnvironmentDefinition>();

        void Awake()
        {
            Instance = this;
            if (environmentRoot == null) environmentRoot = transform;
            PopulateEnvironments();
        }

        void PopulateEnvironments()
        {
            if (environments.Count > 0) return;

            environments.Add(new EnvironmentDefinition{
                id="TropicalIsland", name="Tropical Island", description="Beach with palm trees", isMixedReality=false,
                skyboxColorTop=new Color(0.3f,0.7f,1f), skyboxColorBottom=new Color(0.9f,0.7f,0.4f),
                ambientIntensity=1.2f, hasWater=true, hasVegetation=true
            });
            environments.Add(new EnvironmentDefinition{
                id="Lounge", name="Lounge", description="Cozy indoor lounge", isMixedReality=false,
                skyboxColorTop=new Color(0.2f,0.2f,0.25f), skyboxColorBottom=new Color(0.15f,0.15f,0.18f),
                ambientIntensity=0.8f
            });
            environments.Add(new EnvironmentDefinition{
                id="VoidSpace", name="Void", description="Infinite dark void", isMixedReality=false,
                skyboxColorTop=Color.black, skyboxColorBottom=new Color(0.05f,0.05f,0.1f),
                ambientIntensity=0.3f
            });
            environments.Add(new EnvironmentDefinition{
                id="JapaneseDojo", name="Japanese Dojo", description="Sunset temple", isMixedReality=false,
                skyboxColorTop=new Color(1f,0.5f,0.2f), skyboxColorBottom=new Color(0.8f,0.3f,0.2f),
                ambientIntensity=1f, hasVegetation=true
            });
            environments.Add(new EnvironmentDefinition{
                id="DesertOasis", name="Desert Oasis", description="Desert with oasis", isMixedReality=true,
                skyboxColorTop=new Color(0.5f,0.8f,1f), skyboxColorBottom=new Color(0.9f,0.8f,0.5f),
                ambientIntensity=1.5f
            });
            environments.Add(new EnvironmentDefinition{
                id="Passthrough", name="Passthrough", description="Real world via camera", isMixedReality=true,
                skyboxColorTop=Color.clear, skyboxColorBottom=Color.clear,
                ambientIntensity=1f
            });
            environments.Add(new EnvironmentDefinition{
                id="CyberCity", name="Cyber City", description="Neon cyberpunk city", isMixedReality=false,
                skyboxColorTop=new Color(0.1f,0.05f,0.3f), skyboxColorBottom=new Color(0.8f,0.2f,0.6f),
                ambientIntensity=0.9f
            });
            environments.Add(new EnvironmentDefinition{
                id="SpaceStation", name="Space Station", description="Orbital station", isMixedReality=false,
                skyboxColorTop=Color.black, skyboxColorBottom=new Color(0.02f,0.02f,0.05f),
                ambientIntensity=0.6f
            });
            environments.Add(new EnvironmentDefinition{
                id="ForestValley", name="Forest Valley", description="Lush forest valley", isMixedReality=false,
                skyboxColorTop=new Color(0.4f,0.7f,1f), skyboxColorBottom=new Color(0.2f,0.6f,0.3f),
                ambientIntensity=1.3f, hasVegetation=true
            });
            environments.Add(new EnvironmentDefinition{
                id="NebulaVoid", name="Nebula Void", description="Deep space nebula", isMixedReality=false,
                skyboxColorTop=new Color(0.6f,0.1f,0.8f), skyboxColorBottom=new Color(0.05f,0.02f,0.2f),
                ambientIntensity=0.4f
            });

            foreach(var env in environments) _envMap[env.id]=env;
        }

        void Start()
        {
            LoadEnvironment(currentEnvironmentId);
        }

        public void LoadEnvironment(string envId)
        {
            if (!_envMap.ContainsKey(envId)) envId = "TropicalIsland";
            var def = _envMap[envId];
            currentEnvironmentId = envId;

            // Destroy previous
            if (_currentEnvInstance != null) Destroy(_currentEnvInstance);

            _currentEnvInstance = new GameObject($"Env_{def.name}");
            _currentEnvInstance.transform.SetParent(environmentRoot, false);

            // Generate procedural 3D environment
            GenerateEnvironment(def, _currentEnvInstance.transform);

            // Update lighting
            RenderSettings.ambientMode = UnityEngine.Rendering.AmbientMode.Trilight;
            RenderSettings.ambientSkyColor = def.skyboxColorTop;
            RenderSettings.ambientEquatorColor = Color.Lerp(def.skyboxColorTop, def.skyboxColorBottom, 0.5f);
            RenderSettings.ambientGroundColor = def.skyboxColorBottom;
            RenderSettings.ambientIntensity = def.ambientIntensity;

            Debug.Log($"[MetaPort] Loaded environment: {def.name}");

            // Notify MR manager
            var mr = Core.MixedReality.MRModeManager.Instance;
            if (mr != null)
            {
                if (def.isMixedReality) mr.SetMode(Core.MixedReality.MRModeManager.XRMode.MixedReality);
                else mr.SetMode(Core.MixedReality.MRModeManager.XRMode.VirtualReality);
            }
        }

        void GenerateEnvironment(EnvironmentDefinition def, Transform root)
        {
            // Procedural generation - 3D spatial only

            // Ground plane with depth
            var ground = GameObject.CreatePrimitive(PrimitiveType.Plane);
            ground.name = "Ground_3D";
            ground.transform.SetParent(root, false);
            ground.transform.localScale = Vector3.one * 20f;
            ground.transform.localPosition = Vector3.down * 1.5f;
            var groundMat = new Material(Shader.Find("MetaPort/Icon3D"));
            groundMat.SetColor("_BaseColor", def.id=="TropicalIsland" ? new Color(0.9f,0.85f,0.6f) : new Color(0.2f,0.2f,0.22f));
            ground.GetComponent<Renderer>().material = groundMat;

            if (def.hasWater)
            {
                var water = GameObject.CreatePrimitive(PrimitiveType.Plane);
                water.name = "Water_3D";
                water.transform.SetParent(root, false);
                water.transform.localPosition = new Vector3(0,-1.4f,5f);
                water.transform.localScale = Vector3.one * 15f;
                var waterMat = new Material(Shader.Find("MetaPort/Water3D"));
                waterMat.SetColor("_BaseColor", new Color(0.2f,0.5f,0.8f,0.8f));
                water.GetComponent<Renderer>().material = waterMat;
            }

            if (def.hasVegetation)
            {
                // Palm trees for tropical
                for (int i=0;i<8;i++)
                {
                    var tree = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                    tree.name = $"Tree_{i}_3D";
                    tree.transform.SetParent(root, false);
                    tree.transform.localPosition = new Vector3(Random.Range(-15f,15f), -0.5f, Random.Range(5f,20f));
                    tree.transform.localScale = new Vector3(0.2f, Random.Range(1f,3f), 0.2f);
                    var treeMat = new Material(Shader.Find("MetaPort/Icon3D"));
                    treeMat.SetColor("_BaseColor", new Color(0.4f,0.25f,0.15f));
                    tree.GetComponent<Renderer>().material = treeMat;

                    var leaves = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                    leaves.transform.SetParent(tree.transform, false);
                    leaves.transform.localPosition = Vector3.up * 1.2f;
                    leaves.transform.localScale = new Vector3(2f,1f,2f);
                    var leafMat = new Material(Shader.Find("MetaPort/Icon3D"));
                    leafMat.SetColor("_BaseColor", new Color(0.2f,0.6f,0.2f));
                    leaves.GetComponent<Renderer>().material = leafMat;
                }
            }

            if (def.id == "JapaneseDojo")
            {
                var gate = GameObject.CreatePrimitive(PrimitiveType.Cube);
                gate.name = "DojoGate";
                gate.transform.SetParent(root, false);
                gate.transform.localPosition = new Vector3(0,0.5f,8f);
                gate.transform.localScale = new Vector3(4f,3f,0.3f);
                var gateMat = new Material(Shader.Find("MetaPort/Icon3D"));
                gateMat.SetColor("_BaseColor", new Color(0.6f,0.2f,0.1f));
                gate.GetComponent<Renderer>().material = gateMat;
            }
            if (def.id == "CyberCity")
            {
                var env = root.gameObject.AddComponent<CyberCity.CyberCityEnv>();
            }
            if (def.id == "SpaceStation")
            {
                var env = root.gameObject.AddComponent<SpaceStation.SpaceStationEnv>();
            }
            if (def.id == "ForestValley")
            {
                var env = root.gameObject.AddComponent<ForestValley.ForestValleyEnv>();
            }
            if (def.id == "NebulaVoid")
            {
                var env = root.gameObject.AddComponent<NebulaVoid.NebulaVoidEnv>();
            }

            // Horizon backdrop - curved 3D mesh
            var backdrop = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            backdrop.name = "SkyBackdrop_3D";
            backdrop.transform.SetParent(root, false);
            backdrop.transform.localScale = Vector3.one * 100f;
            // Invert normals
            var mesh = backdrop.GetComponent<MeshFilter>().mesh;
            var triangles = mesh.triangles;
            System.Array.Reverse(triangles);
            mesh.triangles = triangles;
            mesh.RecalculateNormals();
            var skyMat = new Material(Shader.Find("MetaPort/Skybox3D"));
            skyMat.SetColor("_TopColor", def.skyboxColorTop);
            skyMat.SetColor("_BottomColor", def.skyboxColorBottom);
            backdrop.GetComponent<Renderer>().material = skyMat;
        }

        public EnvironmentDefinition GetCurrentEnvironment() => _envMap.ContainsKey(currentEnvironmentId) ? _envMap[currentEnvironmentId] : null;
    }

    [System.Serializable]
    public class EnvironmentDefinition
    {
        public string id;
        public string name;
        public string description;
        public bool isMixedReality;
        public Color skyboxColorTop;
        public Color skyboxColorBottom;
        public float ambientIntensity = 1f;
        public bool hasWater = false;
        public bool hasVegetation = false;
        public Texture2D previewImage;
    }
}
