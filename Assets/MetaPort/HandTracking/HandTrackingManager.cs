using UnityEngine;
using System.Collections.Generic;

namespace MetaPort.HandTracking
{
    /// <summary>
    /// Hand Tracking Manager - MediaPipe + OpenXR hybrid
    /// Provides 21 landmarks per hand, gesture recognition
    /// 100% 3D spatial interaction
    /// </summary>
    public class HandTrackingManager : MonoBehaviour
    {
        public static HandTrackingManager Instance;

        [Header("Hand Tracking")]
        public bool enableHandTracking = true;
        public bool enableHandMesh = true;
        public bool enablePhysicsInteraction = true;
        public float detectionConfidence = 0.7f;

        [Header("References")]
        public Camera xrCamera;
        public Transform leftHandRoot;
        public Transform rightHandRoot;
        public Material handMaterial;
        public Material jointMaterial;

        [Header("Performance")]
        public int targetFPS = 60;
        public bool runOnGPU = true;
        public bool useMediaPipe = true;

        private XRHandSkeleton _leftSkeleton;
        private XRHandSkeleton _rightSkeleton;
        private bool _leftTracking = false;
        private bool _rightTracking = false;
        private GestureRecognizer _gestureRecognizer;

        // MediaPipe provider
        private MediaPipeHandProvider _mediaPipeProvider;

        void Awake()
        {
            Instance = this;
            if (xrCamera == null) xrCamera = Camera.main;
            _gestureRecognizer = new GestureRecognizer();
            
            SetupHandRoots();
        }

        void SetupHandRoots()
        {
            if (leftHandRoot == null)
            {
                var go = new GameObject("LeftHand");
                go.transform.SetParent(transform, false);
                leftHandRoot = go.transform;
            }
            if (rightHandRoot == null)
            {
                var go = new GameObject("RightHand");
                go.transform.SetParent(transform, false);
                rightHandRoot = go.transform;
            }

            _leftSkeleton = new XRHandSkeleton(Core.MonadoOpenXRLoader.Handedness.Left);
            _rightSkeleton = new XRHandSkeleton(Core.MonadoOpenXRLoader.Handedness.Right);
        }

        void Start()
        {
            if (useMediaPipe)
            {
                _mediaPipeProvider = gameObject.AddComponent<MediaPipeHandProvider>();
                _mediaPipeProvider.OnHandsDetected += OnMediaPipeHands;
            }
        }

        void Update()
        {
            if (!enableHandTracking) return;

            UpdateHandTracking();
            UpdateGestures();
        }

        void UpdateHandTracking()
        {
            // Try OpenXR first (Monado)
            var monado = Core.MonadoOpenXRLoader.Instance;
            if (monado != null && monado.isMonadoAvailable)
            {
                // Query OpenXR hand joints
                // This would be native OpenXR call
            }

            // Fallback: Use camera + MediaPipe (already handled via event)
            
            // Update visualizers
            if (leftHandRoot != null && _leftTracking)
                VisualizeHand(_leftSkeleton, leftHandRoot);
            if (rightHandRoot != null && _rightTracking)
                VisualizeHand(_rightSkeleton, rightHandRoot);
        }

        void OnMediaPipeHands(MediaPipeHand[] hands)
        {
            _leftTracking = false;
            _rightTracking = false;

            foreach (var hand in hands)
            {
                if (hand.handedness == 0) // Left
                {
                    _leftSkeleton.UpdateFromMediaPipe(hand);
                    _leftTracking = hand.confidence > detectionConfidence;
                }
                else
                {
                    _rightSkeleton.UpdateFromMediaPipe(hand);
                    _rightTracking = hand.confidence > detectionConfidence;
                }
            }
        }

        void VisualizeHand(XRHandSkeleton skeleton, Transform root)
        {
            if (!enableHandMesh) return;

            // Create or update joint spheres - 3D spatial
            for (int i = 0; i < skeleton.joints.Length; i++)
            {
                var joint = skeleton.joints[i];
                var jointName = $"Joint_{i}_{(XRHandJoint)i}";
                var jointT = root.Find(jointName);
                if (jointT == null)
                {
                    var go = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                    go.name = jointName;
                    go.transform.SetParent(root, false);
                    go.transform.localScale = Vector3.one * 0.012f;
                    var renderer = go.GetComponent<Renderer>();
                    if (jointMaterial != null) renderer.material = jointMaterial;
                    jointT = go.transform;
                    // Remove collider for performance
                    Destroy(go.GetComponent<Collider>());
                }
                jointT.position = joint.position;
                jointT.rotation = joint.rotation;
            }

            // Draw lines between joints (bones) - 3D
            // Thumb: 0-1-2-3-4
            // Index: 0-5-6-7-8 etc.
        }

        void UpdateGestures()
        {
            if (_leftTracking)
            {
                var gesture = _gestureRecognizer.Recognize(_leftSkeleton);
                HandleGesture(Core.MonadoOpenXRLoader.Handedness.Left, gesture);
            }
            if (_rightTracking)
            {
                var gesture = _gestureRecognizer.Recognize(_rightSkeleton);
                HandleGesture(Core.MonadoOpenXRLoader.Handedness.Right, gesture);
            }
        }

        void HandleGesture(Core.MonadoOpenXRLoader.Handedness hand, GestureType gesture)
        {
            // Map gestures to spatial UI interactions
            switch (gesture)
            {
                case GestureType.Pinch:
                    // Grab / Click
                    TryGrab(hand);
                    break;
                case GestureType.OpenPalm:
                    // Open menu
                    break;
                case GestureType.Fist:
                    // Close
                    break;
                case GestureType.Point:
                    // Laser pointer
                    UpdatePointing(hand);
                    break;
            }
        }

        void TryGrab(Core.MonadoOpenXRLoader.Handedness hand)
        {
            var skeleton = hand == Core.MonadoOpenXRLoader.Handedness.Left ? _leftSkeleton : _rightSkeleton;
            var pinchPos = skeleton.GetPinchPosition();
            
            // Raycast for spatial windows
            Collider[] hits = Physics.OverlapSphere(pinchPos, 0.05f);
            foreach (var hit in hits)
            {
                var window = hit.GetComponentInParent<UI.SpatialUI.SpatialWindow>();
                if (window != null)
                {
                    window.OnHandGrab(hand, pinchPos, skeleton.GetPinchRotation());
                }
            }
        }

        void UpdatePointing(Core.MonadoOpenXRLoader.Handedness hand)
        {
            var skeleton = hand == Core.MonadoOpenXRLoader.Handedness.Left ? _leftSkeleton : _rightSkeleton;
            var indexTip = skeleton.GetJoint(XRHandJoint.IndexTip);
            var indexDir = skeleton.GetJointDirection(XRHandJoint.IndexTip);

            Ray ray = new Ray(indexTip.position, indexDir);
            if (Physics.Raycast(ray, out var hit, 10f))
            {
                var interactable = hit.collider.GetComponent<UI.SpatialUI.IHandInteractable>();
                interactable?.OnHandHover(hit.point, hand);
            }
        }

        public bool IsTracking(Core.MonadoOpenXRLoader.Handedness hand) => hand == Core.MonadoOpenXRLoader.Handedness.Left ? _leftTracking : _rightTracking;
        public XRHandSkeleton GetSkeleton(Core.MonadoOpenXRLoader.Handedness hand) => hand == Core.MonadoOpenXRLoader.Handedness.Left ? _leftSkeleton : _rightSkeleton;
        public Vector3 GetHandPosition(Core.MonadoOpenXRLoader.Handedness hand) => GetSkeleton(hand).palmPosition;
    }

    public enum GestureType { None, Pinch, OpenPalm, Fist, Point, ThumbsUp, Victory }

    public struct MediaPipeHand
    {
        public int handedness; // 0 left, 1 right
        public float confidence;
        public Vector3[] landmarks; // 21
        public Vector3 palmPosition;
    }
}
