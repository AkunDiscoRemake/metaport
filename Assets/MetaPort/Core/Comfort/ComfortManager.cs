using UnityEngine;

namespace MetaPort.Core.Comfort
{
    /// <summary>
    /// Comfort Manager - reduz motion sickness em Cardboard
    /// Vignette, snap turn, teleport, etc.
    /// </summary>
    public class ComfortManager : MonoBehaviour
    {
        public static ComfortManager Instance;

        [Header("Comfort - Melhor VR Box possível")]
        public bool enableVignetteOnMove = true;
        public bool enableSnapTurn = true;
        public bool enableTeleport = true;
        public bool enableFloorLock = true;

        [Header("Vignette")]
        public float vignetteIntensity = 0.6f;
        public float vignetteSpeed = 5f;
        public Material vignetteMaterial;

        [Header("Movement")]
        public float moveSpeed = 2f;
        public float snapTurnAngle = 30f;
        private float _currentVignette = 0f;

        private Vector3 _lastPosition;
        private bool _isMoving = false;

        void Awake()
        {
            Instance = this;
            _lastPosition = transform.position;
        }

        void Update()
        {
            CheckMovement();
            UpdateVignette();
            HandleSnapTurn();
        }

        void CheckMovement()
        {
            float dist = Vector3.Distance(transform.position, _lastPosition);
            _isMoving = dist > 0.01f;
            _lastPosition = transform.position;

            if (_isMoving && enableVignetteOnMove)
                _currentVignette = Mathf.Lerp(_currentVignette, vignetteIntensity, Time.deltaTime * vignetteSpeed);
            else
                _currentVignette = Mathf.Lerp(_currentVignette, 0f, Time.deltaTime * vignetteSpeed);
        }

        void UpdateVignette()
        {
            if (vignetteMaterial != null)
                vignetteMaterial.SetFloat("_Vignette", _currentVignette);
        }

        void HandleSnapTurn()
        {
            if (!enableSnapTurn) return;
            if (Input.GetKeyDown(KeyCode.Q))
                transform.Rotate(0, -snapTurnAngle, 0);
            if (Input.GetKeyDown(KeyCode.E))
                transform.Rotate(0, snapTurnAngle, 0);

            // Swipe na borda da tela = snap turn (para Cardboard sem controle)
            if (Input.touchCount == 1)
            {
                var touch = Input.GetTouch(0);
                if (touch.phase == TouchPhase.Ended && touch.deltaPosition.magnitude > 100f)
                {
                    if (touch.position.x < Screen.width * 0.2f)
                        transform.Rotate(0, -snapTurnAngle, 0);
                    else if (touch.position.x > Screen.width * 0.8f)
                        transform.Rotate(0, snapTurnAngle, 0);
                }
            }
        }

        public void TeleportTo(Vector3 position)
        {
            if (!enableTeleport) return;
            // Fade out/in
            StartCoroutine(TeleportRoutine(position));
        }

        System.Collections.IEnumerator TeleportRoutine(Vector3 pos)
        {
            // Fade to black
            float t = 0;
            while (t < 0.2f)
            {
                t += Time.deltaTime;
                if (vignetteMaterial != null) vignetteMaterial.SetFloat("_Vignette", Mathf.Lerp(0, 1, t / 0.2f));
                yield return null;
            }
            transform.position = pos;
            t = 0;
            while (t < 0.2f)
            {
                t += Time.deltaTime;
                if (vignetteMaterial != null) vignetteMaterial.SetFloat("_Vignette", Mathf.Lerp(1, 0, t / 0.2f));
                yield return null;
            }
        }
    }
}
