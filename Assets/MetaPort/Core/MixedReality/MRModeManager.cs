using UnityEngine;
using UnityEngine.XR.ARFoundation;

namespace MetaPort.Core.MixedReality
{
    /// <summary>
    /// Mixed Reality Mode Manager - VR vs Passthrough MR
    /// Uses camera for mixed reality, with full VR fallback
    /// </summary>
    public class MRModeManager : MonoBehaviour
    {
        public static MRModeManager Instance;

        public enum XRMode { VirtualReality, MixedReality, PassthroughOnly }

        [Header("Mode")]
        public XRMode currentMode = XRMode.MixedReality;
        public bool autoSwitchBasedOnEnvironment = true;

        [Header("References")]
        public ARCameraManager arCameraManager;
        public Camera xrCamera;
        public Material passthroughMaterial;
        public Material vrSkyboxMaterial;

        [Header("Environments")]
        public GameObject vrEnvironmentRoot;
        public GameObject mrEnvironmentRoot;

        [Header("Transition")]
        public float transitionDuration = 1.2f;
        public AnimationCurve transitionCurve = AnimationCurve.EaseInOut(0,0,1,1);

        private float _passthroughOpacity = 1f;
        private float _targetOpacity = 1f;
        private bool _isTransitioning = false;

        public System.Action<XRMode> OnModeChanged;

        void Awake()
        {
            Instance = this;
            if (xrCamera == null) xrCamera = Camera.main;
        }

        void Start()
        {
            ApplyMode(currentMode, instant:true);
        }

        void Update()
        {
            // Double tap to switch mode
            if (Input.touchCount == 2 && Input.GetTouch(0).phase == TouchPhase.Began)
            {
                ToggleMode();
            }

            // Keyboard shortcut
            if (Input.GetKeyDown(KeyCode.M)) ToggleMode();

            UpdateTransition();
        }

        void UpdateTransition()
        {
            if (!_isTransitioning && Mathf.Abs(_passthroughOpacity - _targetOpacity) < 0.01f) return;

            _passthroughOpacity = Mathf.MoveTowards(_passthroughOpacity, _targetOpacity, Time.deltaTime / transitionDuration);
            
            // Apply to materials
            if (passthroughMaterial != null)
                passthroughMaterial.SetFloat("_Opacity", _passthroughOpacity);

            if (vrEnvironmentRoot != null)
            {
                // In MR mode, VR environment is semi-transparent or hidden
                float vrOpacity = currentMode == XRMode.VirtualReality ? 1f : 1f - _passthroughOpacity;
                SetEnvironmentOpacity(vrEnvironmentRoot, vrOpacity);
            }

            if (_passthroughOpacity == _targetOpacity) _isTransitioning = false;
        }

        void SetEnvironmentOpacity(GameObject root, float opacity)
        {
            foreach (var renderer in root.GetComponentsInChildren<Renderer>())
            {
                if (renderer.material.HasProperty("_Opacity"))
                    renderer.material.SetFloat("_Opacity", opacity);
            }
        }

        public void SetMode(XRMode mode)
        {
            if (currentMode == mode) return;
            currentMode = mode;
            ApplyMode(mode);
            OnModeChanged?.Invoke(mode);
        }

        void ApplyMode(XRMode mode, bool instant = false)
        {
            _isTransitioning = !instant;
            switch (mode)
            {
                case XRMode.VirtualReality:
                    _targetOpacity = 0f;
                    if (arCameraManager != null) arCameraManager.enabled = false;
                    if (vrEnvironmentRoot != null) vrEnvironmentRoot.SetActive(true);
                    if (xrCamera != null) xrCamera.clearFlags = CameraClearFlags.Skybox;
                    break;
                case XRMode.MixedReality:
                    _targetOpacity = 0.85f;
                    if (arCameraManager != null) arCameraManager.enabled = true;
                    if (vrEnvironmentRoot != null) vrEnvironmentRoot.SetActive(true);
                    if (mrEnvironmentRoot != null) mrEnvironmentRoot.SetActive(true);
                    break;
                case XRMode.PassthroughOnly:
                    _targetOpacity = 1f;
                    if (arCameraManager != null) arCameraManager.enabled = true;
                    if (vrEnvironmentRoot != null) vrEnvironmentRoot.SetActive(false);
                    break;
            }
            if (instant) _passthroughOpacity = _targetOpacity;
            Debug.Log($"[MetaPort] XR Mode: {mode} Opacity:{_targetOpacity}");
        }

        public void ToggleMode()
        {
            var next = currentMode == XRMode.VirtualReality ? XRMode.MixedReality : 
                       currentMode == XRMode.MixedReality ? XRMode.PassthroughOnly : XRMode.VirtualReality;
            SetMode(next);
        }

        public bool IsInVR => currentMode == XRMode.VirtualReality;
        public bool IsInMR => currentMode == XRMode.MixedReality;
        public bool IsPassthrough => currentMode != XRMode.VirtualReality;
    }
}
