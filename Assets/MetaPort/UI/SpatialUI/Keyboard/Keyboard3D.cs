using UnityEngine;
using System.Collections.Generic;
using System.Text;

namespace MetaPort.UI.SpatialUI.Keyboard
{
    /// <summary>
    /// Teclado 3D espacial - igual screenshot EnjoytheMR
    /// Teclas com profundidade, física, hand tracking
    /// </summary>
    public class Keyboard3D : MonoBehaviour
    {
        public static Keyboard3D Instance;

        [Header("Keyboard 3D")]
        public Transform keysRoot;
        public float keySize = 0.08f;
        public float keyDepth = 0.02f;
        public float keySpacing = 0.01f;
        public Material keyMaterial;
        public Material keyPressedMaterial;

        [Header("Layout")]
        public string[] rows = new string[] {
            "QWERTYUIOP",
            "ASDFGHJKL",
            "ZXCVBNM"
        };

        public System.Action<string> OnKeyPressed;
        public System.Action<string> OnTextChanged;
        public System.Action OnEnter;

        private List<Key3D> _keys = new List<Key3D>();
        private StringBuilder _currentText = new StringBuilder();
        private Transform _display;

        void Awake()
        {
            Instance = this;
            if (keysRoot == null) keysRoot = transform;
            CreateKeyboard();
        }

        void CreateKeyboard()
        {
            // Display 3D acima do teclado
            var displayGO = GameObject.CreatePrimitive(PrimitiveType.Cube);
            displayGO.name = "Display_3D";
            displayGO.transform.SetParent(keysRoot, false);
            displayGO.transform.localPosition = new Vector3(0, 0.3f, 0);
            displayGO.transform.localScale = new Vector3(1f, 0.15f, 0.02f);
            var displayMat = new Material(Shader.Find("MetaPort/SpatialWindow"));
            displayMat.SetColor("_BaseColor", new Color(0.1f,0.1f,0.12f,0.95f));
            displayGO.GetComponent<Renderer>().material = displayMat;
            _display = displayGO.transform;

            // Criar teclas com profundidade real
            float totalWidth = rows[0].Length * (keySize + keySpacing);
            for (int r = 0; r < rows.Length; r++)
            {
                string row = rows[r];
                float rowOffset = (totalWidth - row.Length * (keySize + keySpacing)) * 0.5f;
                for (int c = 0; c < row.Length; c++)
                {
                    char ch = row[c];
                    Vector3 pos = new Vector3(
                        -totalWidth*0.5f + rowOffset + c * (keySize + keySpacing) + keySize*0.5f,
                        -r * (keySize + keySpacing),
                        0
                    );
                    CreateKey(ch.ToString(), pos);
                }
            }

            // Teclas especiais 3D
            CreateKey("SPACE", new Vector3(0, -3 * (keySize + keySpacing), 0), new Vector3(0.5f, keySize, keyDepth));
            CreateKey("DEL", new Vector3(totalWidth*0.5f - 0.15f, -2 * (keySize + keySpacing), 0), new Vector3(0.2f, keySize, keyDepth));
            CreateKey("ENTER", new Vector3(totalWidth*0.5f - 0.15f, -3 * (keySize + keySpacing), 0), new Vector3(0.2f, keySize, keyDepth));
        }

        void CreateKey(string label, Vector3 localPos, Vector3? sizeOverride = null)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = $"Key_{label}_3D";
            go.transform.SetParent(keysRoot, false);
            go.transform.localPosition = localPos;
            Vector3 size = sizeOverride ?? new Vector3(keySize, keySize, keyDepth);
            go.transform.localScale = size;

            var mat = new Material(Shader.Find("MetaPort/Icon3D"));
            mat.SetColor("_BaseColor", new Color(0.25f,0.25f,0.28f));
            go.GetComponent<Renderer>().material = mat;

            var col = go.GetComponent<BoxCollider>();
            col.size = Vector3.one;

            var key = go.AddComponent<Key3D>();
            key.label = label;
            key.keyboard = this;
            key.originalMaterial = mat;

            _keys.Add(key);
        }

        public void PressKey(string label)
        {
            Debug.Log($"[Keyboard3D] Pressed: {label}");

            if (label == "DEL")
            {
                if (_currentText.Length > 0) _currentText.Length--;
            }
            else if (label == "SPACE")
            {
                _currentText.Append(" ");
            }
            else if (label == "ENTER")
            {
                OnEnter?.Invoke();
                return;
            }
            else
            {
                _currentText.Append(label);
            }

            OnKeyPressed?.Invoke(label);
            OnTextChanged?.Invoke(_currentText.ToString());

            // Feedback háptico
            Handheld.Vibrate();

            // Animação 3D - afunda tecla
            var key = _keys.Find(k => k.label == label);
            if (key != null) key.AnimatePress();
        }

        public string GetText() => _currentText.ToString();
        public void Clear() => _currentText.Clear();
    }

    public class Key3D : MonoBehaviour, IHandInteractable
    {
        public string label;
        public Keyboard3D keyboard;
        public Material originalMaterial;
        private Vector3 _originalPos;
        private bool _isPressed = false;

        void Awake() => _originalPos = transform.localPosition;

        public void AnimatePress()
        {
            if (_isPressed) return;
            _isPressed = true;
            // Afunda 3D
            transform.localPosition = _originalPos + Vector3.forward * 0.015f;
            GetComponent<Renderer>().material.color = Color.gray;
            Invoke(nameof(Reset), 0.15f);
        }

        void Reset()
        {
            transform.localPosition = _originalPos;
            GetComponent<Renderer>().material = originalMaterial;
            _isPressed = false;
        }

        public void OnHandHover(Vector3 point, Core.MonadoOpenXRLoader.Handedness hand)
        {
            GetComponent<Renderer>().material.SetColor("_BaseColor", Color.Lerp(Color.gray, Color.white, 0.3f));
        }

        public void OnHandGrab(Core.MonadoOpenXRLoader.Handedness hand, Vector3 grabPoint, Quaternion grabRot) => keyboard.PressKey(label);
        public void OnHandRelease() {}
        public void OnTriggerEnter(Vector3 point) => keyboard.PressKey(label);
    }
}
