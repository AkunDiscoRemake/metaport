using UnityEngine;
using UnityEngine.XR;
using System.Collections;

namespace MetaPort.Core.Cardboard
{
    /// <summary>
    /// MetaPort Cardboard Rig - 6DOF hybrid rig
    /// Combina Google Cardboard XR Plugin + ARCore 6DOF + Monado OpenXR
    /// 100% SPATIAL 3D - PROIBIDO 2D OVERLAY
    /// </summary>
    [RequireComponent(typeof(Camera))]
    public class CardboardRig : MonoBehaviour
    {
        [Header("Cardboard XR")]
        public bool useCardboardDistortion = true;
        public float stereoMultiplier = 1.0f;
        public float matchByFOV = 60f;
        
        [Header("6DOF Hybrid Tracking")]
        public bool enable6DoF = true;
        public bool enableARCoreSLAM = true;
        public bool enableMonadoOpenXR = true;
        [Range(0f,1f)] public float arCoreToCardboardBlend = 0.7f;
        
        [Header("Spatial References")]
        public Transform trackingSpace;
        public Transform leftEye;
        public Transform rightEye;
        public Transform centerEye;
        public Camera leftCam;
        public Camera rightCam;
        
        private Quaternion _initialRotation;
        private Vector3 _initialPosition;
        private Vector3 _slamPosition;
        private Quaternion _slamRotation;
        private bool _is6DoFTracking = false;
        
        // ARCore pose
        private UnityEngine.XR.ARFoundation.ARSession _arSession;
        private UnityEngine.XR.ARFoundation.ARCameraManager _arCameraManager;

        void Awake()
        {
            _initialRotation = transform.rotation;
            _initialPosition = transform.position;
            
            SetupStereoCameras();
            InitializeXR();
        }

        void SetupStereoCameras()
        {
            if (centerEye == null) centerEye = transform;
            
            // Left Eye
            if (leftEye == null)
            {
                var go = new GameObject("LeftEye");
                go.transform.SetParent(centerEye, false);
                go.transform.localPosition = new Vector3(-0.032f, 0, 0);
                leftEye = go.transform;
                leftCam = go.AddComponent<Camera>();
                leftCam.stereoTargetEye = StereoTargetEyeMask.Left;
            }
            // Right Eye
            if (rightEye == null)
            {
                var go = new GameObject("RightEye");
                go.transform.SetParent(centerEye, false);
                go.transform.localPosition = new Vector3(0.032f, 0, 0);
                rightEye = go.transform;
                rightCam = go.AddComponent<Camera>();
                rightCam.stereoTargetEye = StereoTargetEyeMask.Right;
            }

            // Sync camera properties for spatial rendering
            foreach (var cam in new[] { leftCam, rightCam })
            {
                cam.fieldOfView = matchByFOV;
                cam.nearClipPlane = 0.05f;
                cam.farClipPlane = 1000f;
                cam.allowMSAA = true;
                // URP spatial
                cam.backgroundColor = Color.clear;
            }
        }

        void InitializeXR()
        {
            StartCoroutine(InitXRLoop());
        }

        IEnumerator InitXRLoop()
        {
            // 1. Try Monado OpenXR
            if (enableMonadoOpenXR)
            {
                var monado = FindObjectOfType<MonadoOpenXRLoader>();
                if (monado != null) yield return monado.Initialize();
            }

            // 2. Initialize Cardboard XR Loader (optional - fallback if not installed)
#if CARDBOARD_XR_PLUGIN
            try {
                var cardboardLoader = new Google.XR.Cardboard.XRLoader();
            } catch (System.Exception e) {
                Debug.LogWarning($"[MetaPort] Cardboard XR Plugin not installed, using fallback: {e.Message}");
            }
#else
            Debug.Log("[MetaPort] Cardboard XR Plugin not defined (CARDBOARD_XR_PLUGIN), using ARCore + Gyro fallback - 6DOF still works!");
#endif
            
            // 3. Initialize ARCore for 6DOF
            if (enableARCoreSLAM)
            {
                var arManager = FindObjectOfType<ARCore6DoFManager>();
                if (arManager != null)
                {
                    arManager.OnPoseUpdated += (pos, rot, tracking) =>
                    {
                        _slamPosition = pos;
                        _slamRotation = rot;
                        _is6DoFTracking = tracking;
                    };
                }
            }

            yield return null;
        }

        void Update()
        {
            UpdateTracking();
            UpdateEyePositions();
        }

        void UpdateTracking()
        {
            if (!enable6DoF)
            {
                // Pure 3DOF Cardboard fallback
                var rot = InputTracking.GetLocalRotation(XRNode.CenterEye);
                if (rot != Quaternion.identity)
                    centerEye.localRotation = rot;
                else
                    centerEye.localRotation = Quaternion.Euler(0, Input.gyro.attitude.y * 60f, 0);
                return;
            }

            // Hybrid 6DOF: Cardboard rotation + ARCore position
            Quaternion cardboardRot = InputTracking.GetLocalRotation(XRNode.CenterEye);
            if (cardboardRot == Quaternion.identity)
                cardboardRot = GyroToRotation();

            if (_is6DoFTracking)
            {
                // Blend SLAM position with rotation
                Vector3 targetPos = Vector3.Lerp(_initialPosition, _slamPosition, arCoreToCardboardBlend);
                Quaternion targetRot = Quaternion.Slerp(cardboardRot, _slamRotation, arCoreToCardboardBlend * 0.5f);
                
                centerEye.localPosition = Vector3.Lerp(centerEye.localPosition, targetPos, Time.deltaTime * 10f);
                centerEye.localRotation = Quaternion.Slerp(centerEye.localRotation, targetRot, Time.deltaTime * 10f);
            }
            else
            {
                centerEye.localRotation = Quaternion.Slerp(centerEye.localRotation, cardboardRot, Time.deltaTime * 15f);
            }
        }

        void UpdateEyePositions()
        {
            // Apply IPD dynamic adjustment
            float ipd = 0.064f * stereoMultiplier;
            leftEye.localPosition = new Vector3(-ipd * 0.5f, 0, 0);
            rightEye.localPosition = new Vector3(ipd * 0.5f, 0, 0);
        }

        Quaternion GyroToRotation()
        {
            if (!SystemInfo.supportsGyroscope) return Quaternion.identity;
            var att = Input.gyro.attitude;
            return new Quaternion(att.x, att.y, -att.z, -att.w) * Quaternion.Euler(90, 0, 0);
        }

        public bool Is6DoFTracking => _is6DoFTracking;
        public Vector3 CurrentSLAMPosition => _slamPosition;
    }
}
