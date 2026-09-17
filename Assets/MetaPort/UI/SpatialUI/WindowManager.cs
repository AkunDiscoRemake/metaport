using UnityEngine;
using System.Collections.Generic;
using System.Linq;

namespace MetaPort.UI.SpatialUI
{
    /// <summary>
    /// Window Manager - gerencia multi-janelas 3D, foco, z-order, snapping
    /// Igual Horizon OS mas 100% spatial
    /// </summary>
    public class WindowManager : MonoBehaviour
    {
        public static WindowManager Instance;

        [Header("Window Manager")]
        public List<SpatialWindow> openWindows = new List<SpatialWindow>();
        public SpatialWindow focusedWindow;
        public float windowSpacing = 0.3f;
        public bool enableAutoLayout = true;
        public bool enableSnapping = true;

        [Header("Snapping")]
        public float snapDistance = 0.2f;
        public Material snapGuideMaterial;

        void Awake()
        {
            Instance = this;
        }

        void Update()
        {
            HandleFocus();
            if (enableAutoLayout) UpdateAutoLayout();
        }

        void HandleFocus()
        {
            // Janela mais próxima da câmera = foco
            if (openWindows.Count == 0) return;
            var cam = Camera.main;
            if (cam == null) return;

            SpatialWindow closest = null;
            float minDist = float.MaxValue;
            foreach (var w in openWindows)
            {
                if (w == null) continue;
                float dist = Vector3.Distance(w.transform.position, cam.transform.position);
                // Prioriza janela na frente da câmera
                float angle = Vector3.Angle(cam.transform.forward, w.transform.position - cam.transform.position);
                if (angle > 60f) continue;
                if (dist < minDist)
                {
                    minDist = dist;
                    closest = w;
                }
            }

            if (closest != null && closest != focusedWindow)
            {
                SetFocusedWindow(closest);
            }
        }

        void UpdateAutoLayout()
        {
            // Se muitas janelas, organiza em arco ao redor do usuário
            if (openWindows.Count <= 1) return;
            if (openWindows.Count > 3)
            {
                ArrangeInArc();
            }
        }

        void ArrangeInArc()
        {
            var cam = Camera.main;
            if (cam == null) return;
            float radius = 1.8f;
            float angleStep = 35f;
            float startAngle = - (openWindows.Count - 1) * angleStep * 0.5f;

            for (int i = 0; i < openWindows.Count; i++)
            {
                var w = openWindows[i];
                if (w == null) continue;
                float angle = startAngle + i * angleStep;
                Vector3 pos = cam.transform.position + Quaternion.Euler(0, angle, 0) * cam.transform.forward * radius;
                pos.y = cam.transform.position.y;
                Quaternion rot = Quaternion.LookRotation(pos - cam.transform.position);

                // Anima suavemente
                w.transform.position = Vector3.Lerp(w.transform.position, pos, Time.deltaTime * 3f);
                w.transform.rotation = Quaternion.Slerp(w.transform.rotation, rot, Time.deltaTime * 3f);
            }
        }

        public void RegisterWindow(SpatialWindow window)
        {
            if (!openWindows.Contains(window))
            {
                openWindows.Add(window);
                window.OnWindowFocused.AddListener(() => SetFocusedWindow(window));
                window.OnWindowClosed.AddListener(() => UnregisterWindow(window));
            }
        }

        public void UnregisterWindow(SpatialWindow window)
        {
            openWindows.Remove(window);
            if (focusedWindow == window) focusedWindow = null;
        }

        public void SetFocusedWindow(SpatialWindow window)
        {
            focusedWindow = window;
            // Traz para frente - aumenta escala levemente
            foreach (var w in openWindows)
            {
                if (w == null) continue;
                bool isFocused = w == window;
                w.transform.localScale = Vector3.Lerp(w.transform.localScale, isFocused ? Vector3.one * 1.05f : Vector3.one, Time.deltaTime * 10f);
                // Brilho no focado
                if (w.windowMaterial != null)
                    w.windowMaterial.SetFloat("_HoverIntensity", isFocused ? 0.2f : 0f);
            }
        }

        public void TileWindows()
        {
            // Lado a lado
            var cam = Camera.main;
            if (cam == null) return;
            float totalWidth = openWindows.Count * (1.2f + windowSpacing);
            for (int i = 0; i < openWindows.Count; i++)
            {
                var w = openWindows[i];
                if (w == null) continue;
                float x = (i - openWindows.Count * 0.5f + 0.5f) * (1.2f + windowSpacing);
                Vector3 pos = cam.transform.position + cam.transform.forward * 1.5f + cam.transform.right * x;
                w.transform.position = pos;
                w.transform.rotation = Quaternion.LookRotation(pos - cam.transform.position);
            }
        }

        public void CloseAll()
        {
            foreach (var w in openWindows.ToList()) w.Close();
            openWindows.Clear();
        }
    }
}
