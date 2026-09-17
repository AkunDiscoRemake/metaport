using UnityEngine;

namespace MetaPort.HandTracking
{
    /// <summary>
    /// Visualizador de malha da mão 3D - melhor que Quest
    /// Mostra esqueleto + mesh com profundidade real
    /// </summary>
    public class HandMeshVisualizer : MonoBehaviour
    {
        [Header("Hand Mesh - 3D")]
        public bool showSkeleton = true;
        public bool showMesh = true;
        public bool showJoints = true;
        public Material handMaterial;
        public Material jointMaterial;
        public Material lineMaterial;

        private XRHandSkeleton _skeleton;
        private LineRenderer[] _fingerLines;
        private Transform[] _jointSpheres;
        private Mesh _handMesh;
        private MeshFilter _meshFilter;

        void Awake()
        {
            _meshFilter = gameObject.AddComponent<MeshFilter>();
            var renderer = gameObject.AddComponent<MeshRenderer>();
            if (handMaterial == null)
            {
                handMaterial = new Material(Shader.Find("MetaPort/HandOutline"));
                handMaterial.SetColor("_Color", new Color(0.8f, 0.9f, 1f, 0.6f));
            }
            renderer.material = handMaterial;

            SetupFingerLines();
        }

        void SetupFingerLines()
        {
            _fingerLines = new LineRenderer[5];
            for (int i = 0; i < 5; i++)
            {
                var go = new GameObject($"FingerLine_{i}");
                go.transform.SetParent(transform, false);
                var lr = go.AddComponent<LineRenderer>();
                lr.startWidth = 0.008f;
                lr.endWidth = 0.004f;
                lr.material = lineMaterial ?? new Material(Shader.Find("Unlit/Color"));
                lr.material.color = Color.white;
                lr.positionCount = 4;
                _fingerLines[i] = lr;
            }

            _jointSpheres = new Transform[26];
            for (int i = 0; i < 26; i++)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                go.name = $"Joint_{i}";
                go.transform.SetParent(transform, false);
                go.transform.localScale = Vector3.one * 0.012f;
                Destroy(go.GetComponent<Collider>());
                var renderer = go.GetComponent<Renderer>();
                renderer.material = jointMaterial ?? new Material(Shader.Find("Unlit/Color"));
                _jointSpheres[i] = go.transform;
            }
        }

        public void SetSkeleton(XRHandSkeleton skeleton)
        {
            _skeleton = skeleton;
        }

        void Update()
        {
            if (_skeleton == null) return;

            // Atualiza esferas
            for (int i = 0; i < _skeleton.joints.Length && i < _jointSpheres.Length; i++)
            {
                _jointSpheres[i].position = _skeleton.joints[i].position;
                _jointSpheres[i].gameObject.SetActive(showJoints && _skeleton.joints[i].isTracked);
            }

            // Atualiza linhas dos dedos
            if (showSkeleton)
            {
                UpdateFingerLine(0, new int[] { 1, 2, 3, 5 }); // Thumb
                UpdateFingerLine(1, new int[] { 6, 7, 8, 10 }); // Index
                UpdateFingerLine(2, new int[] { 11, 12, 13, 15 }); // Middle
                UpdateFingerLine(3, new int[] { 16, 17, 18, 20 }); // Ring
                UpdateFingerLine(4, new int[] { 21, 22, 23, 25 }); // Little
            }
        }

        void UpdateFingerLine(int fingerIndex, int[] jointIndices)
        {
            var lr = _fingerLines[fingerIndex];
            for (int i = 0; i < jointIndices.Length; i++)
            {
                int jointIdx = jointIndices[i];
                if (jointIdx < _skeleton.joints.Length)
                    lr.SetPosition(i, _skeleton.joints[jointIdx].position);
            }
        }
    }
}
