using UnityEngine;
using System;
using System.Collections.Generic;

namespace MetaPort.SDK
{
    /// <summary>
    /// MetaPort DEV API - Allows developers to create spatial 3D apps for Cardboard VR
    /// Like Horizon OS SDK but for Cardboard + ARCore + Monado
    /// </summary>
    public static class MetaPortAPI
    {
        public static bool IsInitialized { get; private set; }
        public static event Action OnInitialized;
        public static event Action<MPApp> OnAppLaunched;

        private static List<MPApp> _registeredApps = new List<MPApp>();
        private static MPEnvironment _currentEnvironment;

        // Initialize MetaPort OS
        public static void Initialize()
        {
            if (IsInitialized) return;
            Debug.Log("[MetaPort SDK] Initializing MetaPort OS v1.0 - Cardboard XR + ARCore 6DOF + Monado OpenXR");
            
            // Check dependencies
            var cardboard = UnityEngine.Object.FindObjectOfType<Core.Cardboard.CardboardRig>();
            var arcore = UnityEngine.Object.FindObjectOfType<Core.ARCore6DoFManager>();
            var monado = UnityEngine.Object.FindObjectOfType<Core.MonadoOpenXRLoader>();

            Debug.Log($"[MetaPort SDK] Cardboard: {(cardboard!=null?"OK":"Missing")} ARCore: {(arcore!=null?"OK":"Missing")} Monado: {(monado!=null? (monado.isMonadoAvailable?"Monado":"Hybrid"):"Hybrid")}");

            IsInitialized = true;
            OnInitialized?.Invoke();
        }

        // App registration
        public static void RegisterApp(MPApp app)
        {
            if (_registeredApps.Contains(app)) return;
            _registeredApps.Add(app);
            Debug.Log($"[MetaPort SDK] Registered app: {app.AppName} ({app.AppId})");
        }

        public static MPApp CreateApp(string appId, string appName, string version = "1.0.0")
        {
            var app = new MPApp(appId, appName, version);
            RegisterApp(app);
            return app;
        }

        // Window management - 3D spatial only
        public static MPWindow CreateSpatialWindow(string title, Vector3 size, Vector3? position = null)
        {
            Vector3 pos = position ?? Camera.main.transform.position + Camera.main.transform.forward * 1.5f;
            
            var go = new GameObject($"MPWindow_{title}");
            go.transform.position = pos;
            go.transform.rotation = Quaternion.LookRotation(go.transform.position - Camera.main.transform.position);

            var window = go.AddComponent<MPWindow>();
            window.Title = title;
            window.Size = size;

            var spatialWindow = go.AddComponent<UI.SpatialUI.SpatialWindow>();
            spatialWindow.appName = title;
            spatialWindow.size = size;

            return window;
        }

        // Environment
        public static void SetEnvironment(MPEnvironment env)
        {
            _currentEnvironment = env;
            var envManager = UnityEngine.Object.FindObjectOfType<Environments.EnvironmentManager>();
            if (envManager != null) envManager.LoadEnvironment(env.Id);
        }

        public static MPEnvironment GetCurrentEnvironment() => _currentEnvironment;

        // Hand tracking
        public static bool TryGetHandJoint(Core.MonadoOpenXRLoader.Handedness hand, HandTracking.XRHandJoint joint, out Vector3 pos, out Quaternion rot)
        {
            pos = Vector3.zero;
            rot = Quaternion.identity;
            var manager = HandTracking.HandTrackingManager.Instance;
            if (manager == null || !manager.IsTracking(hand)) return false;
            var skeleton = manager.GetSkeleton(hand);
            var j = skeleton.GetJoint(joint);
            pos = j.position;
            rot = j.rotation;
            return j.isTracked;
        }

        // 6DOF
        public static bool Is6DoFTracking
        {
            get
            {
                var rig = UnityEngine.Object.FindObjectOfType<Core.Cardboard.CardboardRig>();
                return rig != null && rig.Is6DoFTracking;
            }
        }

        public static Vector3 GetHeadPosition() => Camera.main.transform.position;
        public static Quaternion GetHeadRotation() => Camera.main.transform.rotation;

        // MR
        public static void SetXRMode(Core.MixedReality.MRModeManager.XRMode mode)
        {
            var mr = Core.MixedReality.MRModeManager.Instance;
            if (mr != null) mr.SetMode(mode);
        }

        // Input
        public static bool GetTriggerDown() => Input.GetMouseButtonDown(0);
        public static Vector3 GetGazePoint()
        {
            var input = Core.Cardboard.CardboardInput.Instance;
            if (input != null && input.IsGazing) return input.GetGazeHit().point;
            return Camera.main.transform.position + Camera.main.transform.forward * 2f;
        }
    }

    public class MPApp
    {
        public string AppId { get; }
        public string AppName { get; }
        public string Version { get; }
        public List<MPWindow> Windows { get; } = new List<MPWindow>();

        public MPApp(string id, string name, string version)
        {
            AppId = id;
            AppName = name;
            Version = version;
        }

        public MPWindow CreateWindow(string title, Vector3 size) => MetaPortAPI.CreateSpatialWindow(title, size);

        public void Launch()
        {
            Debug.Log($"[MetaPort] Launching {AppName}");
        }
    }

    public class MPWindow : MonoBehaviour
    {
        public string Title;
        public Vector3 Size = new Vector3(1.2f, 0.8f, 0.05f);
        public bool IsGrabbable = true;
        public bool IsResizable = true;

        private UI.SpatialUI.SpatialWindow _spatialWindow;

        void Awake()
        {
            _spatialWindow = GetComponent<UI.SpatialUI.SpatialWindow>();
        }

        public void SetContent(Texture texture)
        {
            // Assign texture to 3D window material
            if (_spatialWindow != null && _spatialWindow.contentRenderer != null)
            {
                _spatialWindow.contentRenderer.material.mainTexture = texture;
            }
        }

        public void SetContent(RenderTexture rt)
        {
            if (_spatialWindow != null)
                _spatialWindow.appRenderTexture = rt;
        }

        public void Close() => _spatialWindow?.Close();
        public void Focus() => _spatialWindow?.OnWindowFocused?.Invoke();
    }

    public class MPEnvironment
    {
        public string Id;
        public string Name;
        public bool IsMixedReality;
        public Color AmbientColor;
    }

    // Input helpers
    public static class MPInput
    {
        public static bool IsHandTracking => HandTracking.HandTrackingManager.Instance != null;
        public static bool IsLeftHandTracking => HandTracking.HandTrackingManager.Instance?.IsTracking(Core.MonadoOpenXRLoader.Handedness.Left) ?? false;
        public static bool IsRightHandTracking => HandTracking.HandTrackingManager.Instance?.IsTracking(Core.MonadoOpenXRLoader.Handedness.Right) ?? false;

        public static Vector3 GetPinchPosition(Core.MonadoOpenXRLoader.Handedness hand)
        {
            var manager = HandTracking.HandTrackingManager.Instance;
            if (manager == null) return Vector3.zero;
            return manager.GetSkeleton(hand).GetPinchPosition();
        }

        public static float GetPinchStrength(Core.MonadoOpenXRLoader.Handedness hand)
        {
            var manager = HandTracking.HandTrackingManager.Instance;
            if (manager == null) return 0f;
            return manager.GetSkeleton(hand).GetPinchStrength();
        }
    }
}
