using UnityEngine;
using System.Collections.Generic;

namespace MetaPort.Core.Guardian
{
    /// <summary>
    /// Guardian System via ARCore - desenha limite da sala em 3D
    /// Igual Quest, mas usando plane detection
    /// </summary>
    public class GuardianSystem : MonoBehaviour
    {
        public static GuardianSystem Instance;

        [Header("Guardian")]
        public bool enableGuardian = true;
        public float boundaryHeight = 2f;
        public float warningDistance = 0.5f;
        public Material guardianMaterial;
        public Material warningMaterial;

        [Header("Detection")]
        public float minArea = 4f; // 2x2m mínimo
        public float maxArea = 100f;

        private List<Vector3> _boundaryPoints = new List<Vector3>();
        private LineRenderer _boundaryLine;
        private bool _isOutOfBounds = false;
        private Transform _player;

        void Awake()
        {
            Instance = this;
            _player = Camera.main.transform;
            CreateBoundaryVisual();
        }

        void CreateBoundaryVisual()
        {
            var go = new GameObject("GuardianBoundary_3D");
            go.transform.SetParent(transform, false);
            _boundaryLine = go.AddComponent<LineRenderer>();
            _boundaryLine.startWidth = 0.05f;
            _boundaryLine.endWidth = 0.05f;
            _boundaryLine.loop = true;
            _boundaryLine.material = guardianMaterial ?? new Material(Shader.Find("Unlit/Color"));
            _boundaryLine.material.color = new Color(0.2f, 0.6f, 1f, 0.6f);
            _boundaryLine.useWorldSpace = false;
        }

        void Update()
        {
            if (!enableGuardian) return;

            var envTracker = FindObjectOfType<ARCore.ARCoreEnvironmentTracker>();
            if (envTracker != null && envTracker.PlaneCount > 0)
            {
                UpdateBoundaryFromPlanes(envTracker);
            }

            CheckOutOfBounds();
        }

        void UpdateBoundaryFromPlanes(ARCore.ARCoreEnvironmentTracker tracker)
        {
            // Usa bounds do environment para criar guardian
            Bounds bounds = tracker.GetEnvironmentBounds();
            if (bounds.size.magnitude < 0.1f) return;

            _boundaryPoints.Clear();
            Vector3 center = bounds.center;
            center.y = 0;
            Vector3 ext = bounds.extents;
            ext.y = 0;

            // Retângulo 3D no chão
            _boundaryPoints.Add(center + new Vector3(-ext.x, 0, -ext.z));
            _boundaryPoints.Add(center + new Vector3(ext.x, 0, -ext.z));
            _boundaryPoints.Add(center + new Vector3(ext.x, 0, ext.z));
            _boundaryPoints.Add(center + new Vector3(-ext.x, 0, ext.z));

            _boundaryLine.positionCount = _boundaryPoints.Count;
            for (int i = 0; i < _boundaryPoints.Count; i++)
                _boundaryLine.SetPosition(i, _boundaryPoints[i]);
        }

        void CheckOutOfBounds()
        {
            if (_boundaryPoints.Count < 3) return;
            Vector3 playerPos = _player.position;
            playerPos.y = 0;

            // Distance to boundary
            float minDist = float.MaxValue;
            for (int i = 0; i < _boundaryPoints.Count; i++)
            {
                Vector3 a = _boundaryPoints[i];
                Vector3 b = _boundaryPoints[(i + 1) % _boundaryPoints.Count];
                float dist = DistancePointToSegment(playerPos, a, b);
                minDist = Mathf.Min(minDist, dist);
            }

            bool wasOut = _isOutOfBounds;
            _isOutOfBounds = minDist < warningDistance;

            if (_isOutOfBounds && !wasOut)
            {
                // Mostrar grid de aviso em 3D
                _boundaryLine.material.color = Color.red;
                _boundaryLine.startWidth = 0.1f;
                // Vibração
                Handheld.Vibrate();
            }
            else if (!_isOutOfBounds && wasOut)
            {
                _boundaryLine.material.color = new Color(0.2f, 0.6f, 1f, 0.6f);
                _boundaryLine.startWidth = 0.05f;
            }
        }

        float DistancePointToSegment(Vector3 p, Vector3 a, Vector3 b)
        {
            Vector3 ab = b - a;
            Vector3 ap = p - a;
            float t = Vector3.Dot(ap, ab) / ab.sqrMagnitude;
            t = Mathf.Clamp01(t);
            Vector3 closest = a + ab * t;
            return Vector3.Distance(p, closest);
        }

        public bool IsOutOfBounds => _isOutOfBounds;
    }
}
