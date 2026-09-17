using UnityEngine;
using UnityEngine.XR;
using System;

namespace MetaPort.Core.Cardboard
{
    /// <summary>
    /// Cardboard input - gaze + trigger + hand tracking hybrid
    /// </summary>
    public class CardboardInput : MonoBehaviour
    {
        public static CardboardInput Instance;
        
        [Header("Input Modes")]
        public bool enableGaze = true;
        public bool enableHandTracking = true;
        public bool enableScreenTouch = true;

        public event Action OnTriggerPressed;
        public event Action<Vector2> OnTouch;
        public event Action OnRecenter;

        [Header("Gaze")]
        public float gazeDistance = 10f;
        public LayerMask gazeMask = -1;
        public Transform gazePointer; // 3D spatial pointer

        private Camera _mainCam;
        private RaycastHit _gazeHit;
        private GameObject _gazedObject;
        private float _gazeTime;

        void Awake() { Instance = this; _mainCam = Camera.main; }

        void Update()
        {
            UpdateGaze();
            UpdateCardboardTrigger();
            UpdateTouch();
        }

        void UpdateGaze()
        {
            if (!enableGaze || _mainCam == null) return;

            Ray ray = new Ray(_mainCam.transform.position, _mainCam.transform.forward);
            if (Physics.Raycast(ray, out _gazeHit, gazeDistance, gazeMask))
            {
                if (_gazedObject != _gazeHit.collider.gameObject)
                {
                    _gazedObject = _gazeHit.collider.gameObject;
                    _gazeTime = 0f;
                }
                _gazeTime += Time.deltaTime;

                if (gazePointer != null)
                {
                    gazePointer.position = _gazeHit.point;
                    gazePointer.localScale = Vector3.one * (0.02f + _gazeTime * 0.01f);
                }

                // Auto-trigger after 1.5s gaze
                if (_gazeTime > 1.5f)
                {
                    ExecuteTrigger(_gazeHit.point);
                    _gazeTime = 0f;
                }
            }
        }

        void UpdateCardboardTrigger()
        {
            // Cardboard trigger via touch or button
            if (Input.GetMouseButtonDown(0) || Input.GetKeyDown(KeyCode.Space))
            {
                OnTriggerPressed?.Invoke();
                if (_gazeHit.collider != null)
                    ExecuteTrigger(_gazeHit.point);
            }

            // Recenter - long press
            if (Input.touchCount == 1 && Input.GetTouch(0).phase == TouchPhase.Stationary)
            {
                if (Input.GetTouch(0).deltaTime > 2f) OnRecenter?.Invoke();
            }
        }

        void UpdateTouch()
        {
            if (!enableScreenTouch) return;
            if (Input.touchCount > 0)
            {
                OnTouch?.Invoke(Input.GetTouch(0).position);
            }
        }

        void ExecuteTrigger(Vector3 point)
        {
            var spatialWindow = _gazeHit.collider.GetComponentInParent<UI.SpatialUI.SpatialWindow>();
            if (spatialWindow != null) spatialWindow.OnGazeTrigger(point, _gazeHit);

            var interactable = _gazeHit.collider.GetComponent<UI.SpatialUI.IHandInteractable>();
            interactable?.OnTriggerEnter(point);
        }

        public RaycastHit GetGazeHit() => _gazeHit;
        public bool IsGazing => _gazedObject != null;
    }
}
