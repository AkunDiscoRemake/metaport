using UnityEngine;
using UnityEngine.XR.ARFoundation;
using UnityEngine.XR.ARSubsystems;
using System;

namespace MetaPort.Core
{
    /// <summary>
    /// 6DOF SLAM via ARCore - Real positional tracking for Cardboard
    /// Converts Cardboard 3DOF into 6DOF using ARCore Visual Inertial Odometry
    /// </summary>
    [RequireComponent(typeof(ARSession))]
    [RequireComponent(typeof(ARSessionOrigin))]
    public class ARCore6DoFManager : MonoBehaviour
    {
        public static ARCore6DoFManager Instance;

        [Header("ARCore SLAM")]
        public ARSession arSession;
        public ARSessionOrigin sessionOrigin;
        public ARCameraManager cameraManager;
        public ARPointCloudManager pointCloudManager;
        public ARPlaneManager planeManager;
        public ARAnchorManager anchorManager;

        [Header("6DOF Config")]
        public bool enableSLAM = true;
        public float positionSmoothing = 0.15f;
        public float rotationSmoothing = 0.1f;
        public float maxTrackingDistance = 10f;
        public bool autoRecenterOnTrackingLoss = true;

        [Header("Quality")]
        public TrackingState currentTrackingState = TrackingState.None;
        public Vector3 currentPosition;
        public Quaternion currentRotation;
        public bool isTracking => currentTrackingState == TrackingState.Tracking;

        public event Action<Vector3, Quaternion, bool> OnPoseUpdated;
        public event Action<TrackingState> OnTrackingStateChanged;

        private Vector3 _filteredPos;
        private Quaternion _filteredRot;
        private Vector3 _originPos;
        private Quaternion _originRot;
        private bool _initialized = false;

        void Awake()
        {
            Instance = this;
            if (arSession == null) arSession = GetComponent<ARSession>();
            if (sessionOrigin == null) sessionOrigin = GetComponent<ARSessionOrigin>();
            
            _originPos = sessionOrigin.transform.position;
            _originRot = sessionOrigin.transform.rotation;
        }

        void OnEnable()
        {
            if (cameraManager != null)
                cameraManager.frameReceived += OnCameraFrame;
        }

        void OnDisable()
        {
            if (cameraManager != null)
                cameraManager.frameReceived -= OnCameraFrame;
        }

        void OnCameraFrame(ARCameraFrameEventArgs args)
        {
            // ARCore provides VIO pose through sessionOrigin
            if (sessionOrigin.camera != null)
            {
                var cam = sessionOrigin.camera;
                var newPos = cam.transform.position;
                var newRot = cam.transform.rotation;

                // Filter
                _filteredPos = Vector3.Lerp(_filteredPos, newPos, 1f - positionSmoothing);
                _filteredRot = Quaternion.Slerp(_filteredRot, newRot, 1f - rotationSmoothing);

                // Clamp distance
                if (Vector3.Distance(_originPos, _filteredPos) > maxTrackingDistance)
                {
                    _filteredPos = _originPos + (_filteredPos - _originPos).normalized * maxTrackingDistance;
                }

                currentPosition = _filteredPos;
                currentRotation = _filteredRot;

                // Determine tracking quality from ARCore
                var trackingState = TrackingState.Tracking;
                if (args.lightEstimation.averageBrightness.HasValue && args.lightEstimation.averageBrightness.Value < 0.1f)
                    trackingState = TrackingState.Limited;

                if (currentTrackingState != trackingState)
                {
                    currentTrackingState = trackingState;
                    OnTrackingStateChanged?.Invoke(currentTrackingState);
                    
                    if (currentTrackingState == TrackingState.None && autoRecenterOnTrackingLoss)
                        Recenter();
                }

                OnPoseUpdated?.Invoke(currentPosition, currentRotation, isTracking);
                
                if (!_initialized)
                {
                    _initialized = true;
                    _filteredPos = newPos;
                    _filteredRot = newRot;
                }
            }
        }

        public void Recenter()
        {
            _originPos = currentPosition;
            _originRot = currentRotation;
            sessionOrigin.transform.position = _originPos;
            // Keep Y rotation
            sessionOrigin.transform.rotation = Quaternion.Euler(0, _originRot.eulerAngles.y, 0);
            _filteredPos = Vector3.zero;
            Debug.Log("[MetaPort] ARCore 6DOF Recentered");
        }

        public void CreateAnchorAt(Vector3 position, Quaternion rotation)
        {
            if (anchorManager == null) return;
            var anchor = new GameObject("SpatialAnchor").AddComponent<ARAnchor>();
            anchor.transform.position = position;
            anchor.transform.rotation = rotation;
        }

        // Called by CardboardRig
        public Vector3 GetSLAMPosition() => currentPosition - _originPos;
        public Quaternion GetSLAMRotation() => currentRotation;
    }
}
