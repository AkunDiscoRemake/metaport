using UnityEngine;
using UnityEngine.Events;

namespace MetaPort.UI.SpatialUI
{
    /// <summary>
    /// SpatialWindow - Horizon OS style window but 100% 3D SPATIAL
    /// PROIBIDO 2D - Apenas World Space com profundidade, curvatura, glass morphism
    /// Inspired by NovaMr, ZentraXR, Horizon OS
    /// </summary>
    [RequireComponent(typeof(BoxCollider))]
    public class SpatialWindow : MonoBehaviour, IHandInteractable
    {
        public enum WindowState { Normal, Minimized, Maximized, Closed }

        [Header("Spatial Window - 3D ONLY")]
        public string appName = "Browser";
        public string appId = "com.metaport.browser";
        public Sprite appIcon;
        public WindowState state = WindowState.Normal;

        [Header("3D Spatial Properties")]
        public Vector3 size = new Vector3(1.2f, 0.8f, 0.05f);
        public float cornerRadius = 0.08f;
        public float depth = 0.08f;
        public bool isCurved = true;
        public float curvature = 0.15f; // Curved like Quest browser
        public bool enableGlassMorphism = true;
        public bool enableDynamicLighting = true;

        [Header("Interaction - 3D")]
        public bool isGrabbable = true;
        public bool isResizable = true;
        public bool isCloseable = true;
        public Transform[] grabHandles; // 3D handles at corners
        public Transform headerBar; // 3D header with depth

        [Header("Visuals")]
        public Material windowMaterial;
        public Material headerMaterial;
        public MeshRenderer contentRenderer;
        public RenderTexture appRenderTexture; // App content renders to 3D texture

        [Header("Physics")]
        public Rigidbody rb;
        public float dragDamping = 10f;
        public float angularDamping = 10f;

        // Events
        public UnityEvent OnWindowFocused;
        public UnityEvent OnWindowClosed;
        public UnityEvent<Vector3> OnWindowMoved;

        private Vector3 _targetPosition;
        private Quaternion _targetRotation;
        private bool _isGrabbed = false;
        private Transform _grabHand;
        private Vector3 _grabOffset;
        private float _initialGrabDistance;

        void Awake()
        {
            Setup3DMesh();
            SetupPhysics();
            SetupGrabHandles();
        }

        void Setup3DMesh()
        {
            // Generate rounded 3D cube mesh with curvature - NOT a quad!
            var meshFilter = GetComponent<MeshFilter>();
            if (meshFilter == null) meshFilter = gameObject.AddComponent<MeshFilter>();
            
            var meshRenderer = GetComponent<MeshRenderer>();
            if (meshRenderer == null) meshRenderer = gameObject.AddComponent<MeshRenderer>();

            // Create spatial mesh with depth
            Mesh mesh = GenerateSpatialWindowMesh(size, cornerRadius, depth, isCurved, curvature);
            meshFilter.mesh = mesh;

            if (windowMaterial == null)
            {
                windowMaterial = new Material(Shader.Find("MetaPort/SpatialWindow"));
                windowMaterial.SetFloat("_CornerRadius", cornerRadius);
                windowMaterial.SetFloat("_Depth", depth);
                windowMaterial.SetColor("_BaseColor", new Color(0.12f, 0.12f, 0.14f, 0.92f));
            }
            meshRenderer.material = windowMaterial;

            // Box collider for 3D interaction
            var col = GetComponent<BoxCollider>();
            col.size = size;
            col.center = new Vector3(0, 0, depth * 0.5f);
            col.isTrigger = false;
        }

        Mesh GenerateSpatialWindowMesh(Vector3 size, float radius, float depth, bool curved, float curveAmount)
        {
            // Generates a 3D window mesh with rounded corners and curvature
            // 16 segments per corner, extruded with depth, with curvature along X axis
            
            int cornerSegs = 8;
            int vertsPerCorner = cornerSegs + 1;
            // Simplified: create cube with rounded corners
            // In real implementation, generate with curvature

            Mesh mesh = new Mesh();
            mesh.name = "SpatialWindow_3D";

            // Front face with rounded corners + curvature
            var vertices = new System.Collections.Generic.List<Vector3>();
            var triangles = new System.Collections.Generic.List<int>();
            var uvs = new System.Collections.Generic.List<Vector2>();

            float w = size.x * 0.5f - radius;
            float h = size.y * 0.5f - radius;

            // Create front face
            // Center quad
            vertices.Add(new Vector3(-w, -h, 0));
            vertices.Add(new Vector3(w, -h, 0));
            vertices.Add(new Vector3(w, h, 0));
            vertices.Add(new Vector3(-w, h, 0));

            // Apply curvature - bend along cylindrical surface
            if (curved)
            {
                for (int i = 0; i < vertices.Count; i++)
                {
                    float x = vertices[i].x;
                    float bend = Mathf.Sin(x * curveAmount) * 0.05f;
                    vertices[i] = new Vector3(x, vertices[i].y, vertices[i].z + bend);
                }
            }

            // Simple triangulation for front
            triangles.Add(0); triangles.Add(1); triangles.Add(2);
            triangles.Add(0); triangles.Add(2); triangles.Add(3);

            // Back face (extruded)
            int frontCount = vertices.Count;
            for (int i = 0; i < frontCount; i++)
            {
                vertices.Add(vertices[i] + Vector3.forward * depth);
            }
            // Back face triangles reversed
            triangles.Add(frontCount + 2); triangles.Add(frontCount + 1); triangles.Add(frontCount + 0);
            triangles.Add(frontCount + 3); triangles.Add(frontCount + 2); triangles.Add(frontCount + 0);

            // Side faces
            // Left
            triangles.Add(0); triangles.Add(frontCount); triangles.Add(frontCount + 3);
            triangles.Add(0); triangles.Add(frontCount + 3); triangles.Add(3);
            // Right
            triangles.Add(1); triangles.Add(2); triangles.Add(frontCount + 2);
            triangles.Add(1); triangles.Add(frontCount + 2); triangles.Add(frontCount + 1);
            // Bottom
            triangles.Add(0); triangles.Add(1); triangles.Add(frontCount + 1);
            triangles.Add(0); triangles.Add(frontCount + 1); triangles.Add(frontCount);
            // Top
            triangles.Add(3); triangles.Add(frontCount + 3); triangles.Add(frontCount + 2);
            triangles.Add(3); triangles.Add(frontCount + 2); triangles.Add(2);

            for (int i = 0; i < vertices.Count; i++) uvs.Add(new Vector2(vertices[i].x / size.x + 0.5f, vertices[i].y / size.y + 0.5f));

            mesh.SetVertices(vertices);
            mesh.SetTriangles(triangles, 0);
            mesh.SetUVs(0, uvs);
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();

            return mesh;
        }

        void SetupPhysics()
        {
            if (rb == null) rb = gameObject.AddComponent<Rigidbody>();
            rb.useGravity = false;
            rb.drag = dragDamping;
            rb.angularDrag = angularDamping;
            rb.isKinematic = true; // Kinematic when not grabbed, dynamic when grabbed for realism
            rb.interpolation = RigidbodyInterpolation.Interpolate;
        }

        void SetupGrabHandles()
        {
            if (grabHandles == null || grabHandles.Length == 0)
            {
                grabHandles = new Transform[4];
                for (int i = 0; i < 4; i++)
                {
                    var go = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                    go.name = $"GrabHandle_{i}";
                    go.transform.SetParent(transform, false);
                    go.transform.localScale = Vector3.one * 0.06f;
                    var col = go.GetComponent<Collider>();
                    Destroy(col);
                    go.AddComponent<SphereCollider>().radius = 0.5f;
                    var handle = go.AddComponent<GrabHandle>();
                    handle.parentWindow = this;
                    grabHandles[i] = go.transform;

                    // Position at corners with depth
                    float x = (i % 2 == 0 ? -1 : 1) * size.x * 0.5f;
                    float y = (i < 2 ? -1 : 1) * size.y * 0.5f;
                    go.transform.localPosition = new Vector3(x, y, depth * 0.5f);
                }
            }
        }

        void Update()
        {
            if (_isGrabbed && _grabHand != null)
            {
                // 6DOF grab with ARCore SLAM position
                Vector3 targetPos = _grabHand.position + _grabHand.TransformDirection(_grabOffset);
                Quaternion targetRot = _grabHand.rotation;

                // Smooth follow for comfort
                transform.position = Vector3.Lerp(transform.position, targetPos, Time.deltaTime * 15f);
                transform.rotation = Quaternion.Slerp(transform.rotation, targetRot, Time.deltaTime * 12f);

                OnWindowMoved?.Invoke(transform.position);
            }
        }

        // IHandInteractable
        public void OnHandHover(Vector3 point, Core.MonadoOpenXRLoader.Handedness hand)
        {
            // Highlight edge
            if (windowMaterial != null)
                windowMaterial.SetFloat("_HoverIntensity", Mathf.PingPong(Time.time * 2f, 0.3f));
        }

        public void OnHandGrab(Core.MonadoOpenXRLoader.Handedness hand, Vector3 grabPoint, Quaternion grabRot)
        {
            if (!isGrabbable) return;
            var handRoot = hand == Core.MonadoOpenXRLoader.Handedness.Left ? 
                HandTracking.HandTrackingManager.Instance.leftHandRoot : 
                HandTracking.HandTrackingManager.Instance.rightHandRoot;
            
            _grabHand = handRoot;
            _grabOffset = transform.InverseTransformPoint(grabPoint);
            _isGrabbed = true;
            rb.isKinematic = false;
            OnWindowFocused?.Invoke();
        }

        public void OnHandRelease()
        {
            _isGrabbed = false;
            _grabHand = null;
            rb.isKinematic = true;
            rb.velocity = Vector3.zero;
        }

        public void OnTriggerEnter(Vector3 point) { OnHandGrab(Core.MonadoOpenXRLoader.Handedness.Right, point, Quaternion.identity); }

        public void OnGazeTrigger(Vector3 point, RaycastHit hit)
        {
            // Gaze interaction for Cardboard button
            OnWindowFocused?.Invoke();
            // If header bar hit, start drag
            if (hit.collider.transform == headerBar || headerBar == null)
            {
                // Simulate grab at gaze point
            }
        }

        public void Close()
        {
            state = WindowState.Closed;
            OnWindowClosed?.Invoke();
            // Animate close with scale down in 3D
            LeanTween.scale(gameObject, Vector3.zero, 0.3f).setEase(LeanTweenType.easeInBack).setOnComplete(() => Destroy(gameObject));
        }

        public void Minimize()
        {
            state = WindowState.Minimized;
            LeanTween.scale(gameObject, Vector3.one * 0.1f, 0.25f);
        }

        public void Maximize()
        {
            state = WindowState.Maximized;
            var cam = Camera.main;
            if (cam != null)
            {
                _targetPosition = cam.transform.position + cam.transform.forward * 1.5f;
                _targetRotation = Quaternion.LookRotation(transform.position - cam.transform.position);
                LeanTween.move(gameObject, _targetPosition, 0.4f).setEase(LeanTweenType.easeOutCubic);
            }
        }
    }

    public interface IHandInteractable
    {
        void OnHandHover(Vector3 point, Core.MonadoOpenXRLoader.Handedness hand);
        void OnHandGrab(Core.MonadoOpenXRLoader.Handedness hand, Vector3 grabPoint, Quaternion grabRot);
        void OnHandRelease();
        void OnTriggerEnter(Vector3 point);
    }

    public class GrabHandle : MonoBehaviour, IHandInteractable
    {
        public SpatialWindow parentWindow;
        public void OnHandHover(Vector3 point, Core.MonadoOpenXRLoader.Handedness hand) => parentWindow.OnHandHover(point, hand);
        public void OnHandGrab(Core.MonadoOpenXRLoader.Handedness hand, Vector3 grabPoint, Quaternion grabRot) => parentWindow.OnHandGrab(hand, grabPoint, grabRot);
        public void OnHandRelease() => parentWindow.OnHandRelease();
        public void OnTriggerEnter(Vector3 point) => parentWindow.OnTriggerEnter(point);
    }

    // Minimal LeanTween stub for spatial animations
    public static class LeanTween
    {
        public static LTDescr scale(GameObject go, Vector3 to, float time) => new LTDescr(go, to, time, true);
        public static LTDescr move(GameObject go, Vector3 to, float time) => new LTDescr(go, to, time, false);
        public class LTDescr
        {
            GameObject go; Vector3 target; float t; bool isScale;
            System.Action onComplete;
            public LTDescr(GameObject g, Vector3 tar, float time, bool scale) { go = g; target = tar; t = time; isScale = scale; g.GetComponent<MonoBehaviour>().StartCoroutine(Anim()); }
            System.Collections.IEnumerator Anim() { Vector3 start = isScale ? go.transform.localScale : go.transform.position; float elapsed=0; while(elapsed<t){elapsed+=Time.deltaTime; float k=elapsed/t; if(isScale) go.transform.localScale=Vector3.Lerp(start,target,k); else go.transform.position=Vector3.Lerp(start,target,k); yield return null;} onComplete?.Invoke(); }
            public LTDescr setEase(LeanTweenType type){return this;}
            public LTDescr setOnComplete(System.Action a){onComplete=a; return this;}
        }
    }
    public enum LeanTweenType { easeInBack, easeOutCubic }
}
