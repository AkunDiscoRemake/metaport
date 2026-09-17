using UnityEngine;
using System.Collections.Generic;

namespace MetaPort.UI.SpatialUI
{
    /// <summary>
    /// Settings Panel - igual screenshots Horizon OS
    /// Categorias em grid 3D com profundidade
    /// </summary>
    public class SettingsPanel : MonoBehaviour
    {
        [Header("Settings - Horizon OS Style")]
        public Transform categoriesRoot;
        public float cellWidth = 0.35f;
        public float cellHeight = 0.18f;
        public float cellDepth = 0.04f;

        private List<SettingsCategory> _categories = new List<SettingsCategory>
        {
            new SettingsCategory{ id="system", name="System", desc="Display, Voice Commands", icon="⚙️" },
            new SettingsCategory{ id="wifi", name="Wi-Fi", desc="VR", icon="📶" },
            new SettingsCategory{ id="guardian", name="Guardian", desc="Boundary, Passthrough", icon="🛡️" },
            new SettingsCategory{ id="personalization", name="Personalization", desc="Virtual Environment", icon="🎨" },
            new SettingsCategory{ id="storage", name="Storage", desc="53.85 GB free", icon="💾" },
            new SettingsCategory{ id="apps", name="Apps", desc="App Permissions", icon="📱" },
            new SettingsCategory{ id="notifications", name="Notifications", desc="Phone Notifications", icon="🔔" },
            new SettingsCategory{ id="accounts", name="Accounts", desc="Add Account, App Sharing", icon="👤" },
            new SettingsCategory{ id="hands", name="Hands and Controllers", desc="Hand Tracking", icon="🖐️" },
            new SettingsCategory{ id="accessibility", name="Accessibility", desc="Color Correction", icon="♿" },
            new SettingsCategory{ id="privacy", name="Privacy", desc="Activity, Friends List", icon="🔒" },
            new SettingsCategory{ id="security", name="Security", desc="Unlock pattern", icon="🔐" },
            new SettingsCategory{ id="experimental", name="Experimental", desc="Features in development", icon="🧪" },
        };

        void Awake()
        {
            if (categoriesRoot == null) categoriesRoot = transform;
            CreateCategories3D();
        }

        void CreateCategories3D()
        {
            int cols = 4;
            float spacing = 0.02f;

            for (int i = 0; i < _categories.Count; i++)
            {
                int col = i % cols;
                int row = i / cols;
                Vector3 pos = new Vector3(
                    (col - cols*0.5f + 0.5f) * (cellWidth + spacing),
                    (1.5f - row * (cellHeight + spacing)),
                    0.06f
                );

                var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
                go.name = $"Settings_{_categories[i].id}_3D";
                go.transform.SetParent(categoriesRoot, false);
                go.transform.localPosition = pos;
                go.transform.localScale = new Vector3(cellWidth, cellHeight, cellDepth);

                var mat = new Material(Shader.Find("MetaPort/SpatialWindow"));
                mat.SetColor("_BaseColor", new Color(0.22f,0.24f,0.28f,0.95f));
                go.GetComponent<Renderer>().material = mat;

                var cat = go.AddComponent<SettingsCategory3D>();
                cat.category = _categories[i];
                cat.panel = this;
            }
        }

        public void OpenCategory(string id)
        {
            Debug.Log($"[Settings] Open category: {id}");
            // Abriria sub-janela 3D
            switch(id)
            {
                case "system": ShowSystemSettings(); break;
                case "wifi": ShowWiFiSettings(); break;
                case "guardian": ShowGuardianSettings(); break;
                case "hands": ShowHandTrackingSettings(); break;
            }
        }

        void ShowSystemSettings()
        {
            var window = SDK.MetaPortAPI.CreateSpatialWindow("System", new Vector3(0.8f,0.6f,0.05f));
        }
        void ShowWiFiSettings() {}
        void ShowGuardianSettings() {}
        void ShowHandTrackingSettings()
        {
            var window = SDK.MetaPortAPI.CreateSpatialWindow("Hands and Controllers", new Vector3(0.8f,0.6f,0.05f));
        }
    }

    [System.Serializable]
    public class SettingsCategory
    {
        public string id;
        public string name;
        public string desc;
        public string icon;
    }

    public class SettingsCategory3D : MonoBehaviour, IHandInteractable
    {
        public SettingsCategory category;
        public SettingsPanel panel;

        public void OnHandHover(Vector3 point, Core.MonadoOpenXRLoader.Handedness hand)
        {
            GetComponent<Renderer>().material.SetColor("_BaseColor", new Color(0.3f,0.35f,0.45f,1f));
        }
        public void OnHandGrab(Core.MonadoOpenXRLoader.Handedness hand, Vector3 grabPoint, Quaternion grabRot) => panel.OpenCategory(category.id);
        public void OnHandRelease() {}
        public void OnTriggerEnter(Vector3 point) => panel.OpenCategory(category.id);
    }
}
