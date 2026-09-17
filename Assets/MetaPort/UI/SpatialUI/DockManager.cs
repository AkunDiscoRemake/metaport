using UnityEngine;
using System.Collections.Generic;

namespace MetaPort.UI.SpatialUI
{
    /// <summary>
    /// Dock Manager - Horizon OS style dock but 100% 3D spatial
    /// Like in images: bottom bar with icons, 3D depth, curved
    /// </summary>
    public class DockManager : MonoBehaviour
    {
        public static DockManager Instance;

        [Header("Dock - 3D Spatial")]
        public Transform dockRoot; // 3D curved dock
        public float dockWidth = 1.8f;
        public float dockHeight = 0.18f;
        public float dockDepth = 0.08f;
        public float iconSize = 0.12f;
        public float iconSpacing = 0.16f;
        public bool isCurved = true;
        public float curvatureRadius = 2.5f;

        [Header("Icons - 3D")]
        public List<DockIcon> icons = new List<DockIcon>();
        public Material dockMaterial;
        public Material iconMaterial;

        [Header("Interaction")]
        public float hoverScale = 1.4f;
        public float hoverLift = 0.03f;
        public AnimationCurve hoverCurve = AnimationCurve.EaseInOut(0,0,1,1);

        private int _hoveredIndex = -1;
        private Camera _mainCam;

        void Awake()
        {
            Instance = this;
            _mainCam = Camera.main;
            if (dockRoot == null) dockRoot = transform;
            CreateDockMesh();
            CreateDefaultIcons();
        }

        void CreateDockMesh()
        {
            // Create 3D dock mesh - rounded capsule with depth
            var mf = dockRoot.GetComponent<MeshFilter>();
            if (mf == null) mf = dockRoot.gameObject.AddComponent<MeshFilter>();
            var mr = dockRoot.GetComponent<MeshRenderer>();
            if (mr == null) mr = dockRoot.gameObject.AddComponent<MeshRenderer>();

            Mesh mesh = GenerateDockMesh(dockWidth, dockHeight, dockDepth);
            mf.mesh = mesh;

            if (dockMaterial == null)
            {
                dockMaterial = new Material(Shader.Find("MetaPort/SpatialWindow"));
                dockMaterial.SetColor("_BaseColor", new Color(0.08f, 0.08f, 0.1f, 0.85f));
                dockMaterial.SetFloat("_CornerRadius", 0.09f);
            }
            mr.material = dockMaterial;

            var col = dockRoot.GetComponent<BoxCollider>();
            if (col == null) col = dockRoot.gameObject.AddComponent<BoxCollider>();
            col.size = new Vector3(dockWidth, dockHeight, dockDepth);
        }

        Mesh GenerateDockMesh(float w, float h, float d)
        {
            // Rounded box
            Mesh m = new Mesh();
            // Simplified - real would use rounded box generation
            Vector3[] verts = new Vector3[8];
            verts[0] = new Vector3(-w/2, -h/2, 0);
            verts[1] = new Vector3(w/2, -h/2, 0);
            verts[2] = new Vector3(w/2, h/2, 0);
            verts[3] = new Vector3(-w/2, h/2, 0);
            verts[4] = new Vector3(-w/2, -h/2, d);
            verts[5] = new Vector3(w/2, -h/2, d);
            verts[6] = new Vector3(w/2, h/2, d);
            verts[7] = new Vector3(-w/2, h/2, d);
            int[] tris = {0,1,2, 0,2,3, 4,7,6, 4,6,5, 0,4,5, 0,5,1, 1,5,6, 1,6,2, 2,6,7, 2,7,3, 3,7,4, 3,4,0};
            m.vertices = verts;
            m.triangles = tris;
            m.RecalculateNormals();
            return m;
        }

        void CreateDefaultIcons()
        {
            if (icons.Count > 0) return;

            // Default apps like in images: Store, Browser, Files, Settings, etc.
            string[] defaultApps = {
                "Store", "Feed", "Chats", "Browser", "TV", "Files", "Facebook", "Instagram", 
                "WhatsApp", "Messenger", "Remote Desktop", "Asgard's Wrath 2", "Beat Saber",
                "Spotify", "YouTube Music", "Crunchyroll", "HBO Max", "Tubi", "Peacock", "Twitch",
                "Steam", "Camera", "Gallery", "Settings"
            };

            for (int i = 0; i < defaultApps.Length; i++)
            {
                CreateIcon(defaultApps[i], i);
            }
        }

        void CreateIcon(string appName, int index)
        {
            var go = new GameObject($"Icon_{appName}");
            go.transform.SetParent(dockRoot, false);

            // Position with curvature
            float totalWidth = icons.Count * iconSpacing;
            float x = (index - icons.Count * 0.5f) * iconSpacing;
            float z = 0f;
            float y = 0f;

            if (isCurved)
            {
                // Curve icons along arc
                float angle = x / curvatureRadius;
                x = Mathf.Sin(angle) * curvatureRadius;
                z = (1 - Mathf.Cos(angle)) * curvatureRadius * 0.3f;
            }

            go.transform.localPosition = new Vector3(x, y, z + dockDepth * 0.5f + 0.01f);

            // 3D icon - not flat quad, but rounded cube with depth
            var mf = go.AddComponent<MeshFilter>();
            var mr = go.AddComponent<MeshRenderer>();
            
            // Create rounded cube mesh for icon
            Mesh iconMesh = CreateRoundedCubeMesh(iconSize, iconSize * 0.2f, 0.02f);
            mf.mesh = iconMesh;

            var mat = new Material(Shader.Find("MetaPort/Icon3D"));
            // Assign color based on app
            mat.SetColor("_BaseColor", GetAppColor(appName));
            mr.material = mat;

            // Collider for 3D interaction
            var col = go.AddComponent<BoxCollider>();
            col.size = Vector3.one * iconSize;
            col.isTrigger = false;

            // Icon component
            var icon = go.AddComponent<DockIcon>();
            icon.appName = appName;
            icon.index = index;
            icon.dockManager = this;

            icons.Add(icon);
        }

        Mesh CreateRoundedCubeMesh(float size, float depth, float radius)
        {
            Mesh m = new Mesh();
            float s = size * 0.5f;
            Vector3[] v = {
                new Vector3(-s,-s,0), new Vector3(s,-s,0), new Vector3(s,s,0), new Vector3(-s,s,0),
                new Vector3(-s,-s,depth), new Vector3(s,-s,depth), new Vector3(s,s,depth), new Vector3(-s,s,depth)
            };
            int[] t = {0,1,2, 0,2,3, 4,7,6, 4,6,5, 0,4,5, 0,5,1, 1,5,6, 1,6,2, 2,6,7, 2,7,3, 3,7,4, 3,4,0};
            m.vertices = v;
            m.triangles = t;
            m.RecalculateNormals();
            return m;
        }

        Color GetAppColor(string appName)
        {
            switch(appName)
            {
                case "Store": return new Color(0.95f, 0.6f, 0.15f);
                case "Facebook": return new Color(0.23f, 0.35f, 0.92f);
                case "Instagram": return new Color(0.9f, 0.3f, 0.5f);
                case "WhatsApp": return new Color(0.3f, 0.9f, 0.4f);
                case "Spotify": return new Color(0.12f, 0.8f, 0.3f);
                case "Beat Saber": return new Color(0.9f, 0.15f, 0.25f);
                case "YouTube Music": return new Color(0.9f, 0.9f, 0.9f);
                case "Crunchyroll": return new Color(0.95f, 0.5f, 0.15f);
                default: return new Color(0.3f, 0.5f, 0.9f);
            }
        }

        void Update()
        {
            UpdateHover();
            UpdateDockPosition();
        }

        void UpdateDockPosition()
        {
            // Dock follows user at bottom of view, but in 3D space (not screen overlay)
            if (_mainCam == null) return;
            Vector3 targetPos = _mainCam.transform.position + _mainCam.transform.forward * 1.2f + _mainCam.transform.up * -0.4f;
            Quaternion targetRot = Quaternion.LookRotation(dockRoot.position - _mainCam.transform.position);
            
            dockRoot.position = Vector3.Lerp(dockRoot.position, targetPos, Time.deltaTime * 5f);
            dockRoot.rotation = Quaternion.Slerp(dockRoot.rotation, targetRot, Time.deltaTime * 5f);
        }

        void UpdateHover()
        {
            // Raycast from gaze or hand
            Ray ray = new Ray(_mainCam.transform.position, _mainCam.transform.forward);
            if (Physics.Raycast(ray, out var hit, 3f))
            {
                var icon = hit.collider.GetComponent<DockIcon>();
                if (icon != null)
                {
                    if (_hoveredIndex != icon.index)
                    {
                        // Unhover previous
                        if (_hoveredIndex >= 0 && _hoveredIndex < icons.Count)
                            icons[_hoveredIndex].SetHovered(false);
                        _hoveredIndex = icon.index;
                        icon.SetHovered(true);
                    }
                }
            }
        }

        public void LaunchApp(string appName)
        {
            Debug.Log($"[MetaPort] Launching {appName}");
            // Find AppLibrary and launch
            var lib = AppLibrary.Instance;
            if (lib != null) lib.LaunchApp(appName);
        }
    }

    public class DockIcon : MonoBehaviour, IHandInteractable
    {
        public string appName;
        public int index;
        public DockManager dockManager;
        public bool isHovered = false;

        private Vector3 _originalScale;
        private Vector3 _originalPos;

        void Awake()
        {
            _originalScale = transform.localScale;
            _originalPos = transform.localPosition;
        }

        public void SetHovered(bool hovered)
        {
            isHovered = hovered;
            if (hovered)
            {
                LeanTween.scale(gameObject, _originalScale * dockManager.hoverScale, 0.2f);
                // Lift up in Z (depth)
                LeanTween.move(gameObject, _originalPos + Vector3.forward * dockManager.hoverLift, 0.2f);
            }
            else
            {
                LeanTween.scale(gameObject, _originalScale, 0.2f);
            }
        }

        public void OnHandHover(Vector3 point, Core.MonadoOpenXRLoader.Handedness hand) => SetHovered(true);
        public void OnHandGrab(Core.MonadoOpenXRLoader.Handedness hand, Vector3 grabPoint, Quaternion grabRot) => Launch();
        public void OnHandRelease() {}
        public void OnTriggerEnter(Vector3 point) => Launch();

        void Launch() => dockManager.LaunchApp(appName);
    }
}
