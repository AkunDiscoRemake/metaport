using UnityEngine;
using UnityEngine.XR.ARFoundation;
using UnityEngine.XR.ARSubsystems;
using System.Collections.Generic;

namespace MetaPort.Core
{
    /// <summary>
    /// Environment tracking via ARCore - planes, meshes, depth
    /// For mixed reality spatial anchoring
    /// </summary>
    public class ARCoreEnvironmentTracker : MonoBehaviour
    {
        [Header("ARCore Environment")]
        public ARPlaneManager planeManager;
        public ARPointCloudManager pointCloud;
        public ARMeshManager meshManager; // ARCore Depth API
        public AROcclusionManager occlusionManager;

        [Header("Spatial Mapping")]
        public bool enablePlaneDetection = true;
        public bool enableMeshing = true;
        public bool enableDepthOcclusion = true;
        public float meshUpdateRate = 0.5f;

        [Header("Materials - 3D Spatial Only")]
        public Material planeVisualizerMaterial;
        public Material meshOcclusionMaterial;
        public Material wireframeMaterial;

        private Dictionary<TrackableId, ARPlane> _planes = new Dictionary<TrackableId, ARPlane>();
        private List<Vector3> _environmentPoints = new List<Vector3>();
        private Bounds _environmentBounds;

        void Awake()
        {
            if (planeManager == null) planeManager = FindObjectOfType<ARPlaneManager>();
            if (occlusionManager == null) occlusionManager = FindObjectOfType<AROcclusionManager>();
        }

        void OnEnable()
        {
            if (planeManager != null)
            {
                planeManager.planesChanged += OnPlanesChanged;
                planeManager.enabled = enablePlaneDetection;
            }
            if (occlusionManager != null)
            {
                occlusionManager.enabled = enableDepthOcclusion;
            }
        }

        void OnDisable()
        {
            if (planeManager != null) planeManager.planesChanged -= OnPlanesChanged;
        }

        void OnPlanesChanged(ARPlanesChangedEventArgs args)
        {
            foreach (var plane in args.added)
            {
                _planes[plane.trackableId] = plane;
                SetupPlaneVisual(plane);
                UpdateEnvironmentBounds(plane);
            }
            foreach (var plane in args.updated)
            {
                _planes[plane.trackableId] = plane;
                UpdateEnvironmentBounds(plane);
            }
            foreach (var plane in args.removed)
            {
                _planes.Remove(plane.trackableId);
            }
        }

        void SetupPlaneVisual(ARPlane plane)
        {
            // 3D spatial visual - never 2D overlay
            var renderer = plane.GetComponent<Renderer>();
            if (renderer != null && planeVisualizerMaterial != null)
            {
                renderer.material = planeVisualizerMaterial;
                // Make it subtle for MR
                renderer.material.SetFloat("_Opacity", 0.15f);
            }

            // Add 3D collider for spatial interaction
            if (plane.GetComponent<MeshCollider>() == null)
            {
                var col = plane.gameObject.AddComponent<MeshCollider>();
                col.convex = false;
            }

            // Tag for spatial window placement
            plane.gameObject.tag = "SpatialSurface";
        }

        void UpdateEnvironmentBounds(ARPlane plane)
        {
            if (_environmentPoints.Count == 0)
                _environmentBounds = new Bounds(plane.center, Vector3.zero);
            _environmentBounds.Encapsulate(plane.center);
        }

        public bool TryGetFloorPlane(out ARPlane floor)
        {
            floor = null;
            float lowestY = float.MaxValue;
            foreach (var p in _planes.Values)
            {
                if (p.alignment == PlaneAlignment.HorizontalUp && p.center.y < lowestY)
                {
                    lowestY = p.center.y;
                    floor = p;
                }
            }
            return floor != null;
        }

        public bool TryGetWallPlane(out ARPlane wall)
        {
            wall = null;
            foreach (var p in _planes.Values)
            {
                if (p.alignment == PlaneAlignment.Vertical)
                {
                    wall = p;
                    return true;
                }
            }
            return false;
        }

        public Vector3 GetBestWindowPlacement(Vector3 size)
        {
            // Place window on best detected wall or floating in front of user
            if (TryGetWallPlane(out var wallPlane))
            {
                return wallPlane.center + wallPlane.normal * 0.1f;
            }
            // Fallback: 2m in front of camera
            var cam = Camera.main;
            if (cam != null) return cam.transform.position + cam.transform.forward * 2f;
            return Vector3.forward * 2f;
        }

        public Bounds GetEnvironmentBounds() => _environmentBounds;
        public int PlaneCount => _planes.Count;
    }
}
