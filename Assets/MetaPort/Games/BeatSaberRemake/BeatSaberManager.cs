using UnityEngine;
using System.Collections.Generic;

namespace MetaPort.Games.BeatSaberRemake
{
    /// <summary>
    /// Beat Saber Remake - 3D spatial, hand tracking sabers
    /// </summary>
    public class BeatSaberManager : MonoBehaviour
    {
        [Header("Beat Saber - 3D Spatial")]
        public Transform leftSaber;
        public Transform rightSaber;
        public Transform spawnPoint;
        public float noteSpeed = 4f;
        public float spawnInterval = 0.8f;
        public AudioSource musicSource;

        [Header("Materials")]
        public Material redSaberMat;
        public Material blueSaberMat;
        public Material noteMat;

        private List<Note> _activeNotes = new List<Note>();
        private float _spawnTimer = 0f;
        private int _score = 0;
        private int _combo = 0;

        void Awake()
        {
            SetupSabers();
            SetupSpawn();
        }

        void SetupSabers()
        {
            // Left saber - blue - attached to left hand tracking
            if (leftSaber == null)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                go.name = "LeftSaber_Blue";
                go.transform.SetParent(transform, false);
                go.transform.localScale = new Vector3(0.05f, 0.6f, 0.05f);
                leftSaber = go.transform;
                if (blueSaberMat == null)
                {
                    blueSaberMat = new Material(Shader.Find("MetaPort/Saber"));
                    blueSaberMat.SetColor("_Color", new Color(0.2f, 0.5f, 1f));
                    blueSaberMat.SetColor("_Emission", new Color(0.2f, 0.5f, 1f) * 2f);
                }
                go.GetComponent<Renderer>().material = blueSaberMat;
                go.AddComponent<Saber>().isLeft = true;
            }

            if (rightSaber == null)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                go.name = "RightSaber_Red";
                go.transform.SetParent(transform, false);
                go.transform.localScale = new Vector3(0.05f, 0.6f, 0.05f);
                rightSaber = go.transform;
                if (redSaberMat == null)
                {
                    redSaberMat = new Material(Shader.Find("MetaPort/Saber"));
                    redSaberMat.SetColor("_Color", new Color(1f, 0.2f, 0.2f));
                    redSaberMat.SetColor("_Emission", new Color(1f, 0.2f, 0.2f) * 2f);
                }
                go.GetComponent<Renderer>().material = redSaberMat;
                go.AddComponent<Saber>().isLeft = false;
            }

            if (spawnPoint == null)
            {
                var go = new GameObject("SpawnPoint");
                go.transform.SetParent(transform, false);
                go.transform.localPosition = Vector3.forward * 8f + Vector3.up * 1.2f;
                spawnPoint = go.transform;
            }
        }

        void SetupSpawn()
        {
            _spawnTimer = spawnInterval;
        }

        void Update()
        {
            UpdateHandSaberTracking();
            UpdateSpawning();
            UpdateNotes();
        }

        void UpdateHandSaberTracking()
        {
            var handManager = HandTracking.HandTrackingManager.Instance;
            if (handManager == null) return;

            if (handManager.IsTracking(Core.MonadoOpenXRLoader.Handedness.Left))
            {
                var leftPos = handManager.GetHandPosition(Core.MonadoOpenXRLoader.Handedness.Left);
                var skeleton = handManager.GetSkeleton(Core.MonadoOpenXRLoader.Handedness.Left);
                var dir = skeleton.GetJointDirection(HandTracking.XRHandJoint.IndexTip);
                leftSaber.position = leftPos;
                leftSaber.rotation = Quaternion.LookRotation(dir);
            }

            if (handManager.IsTracking(Core.MonadoOpenXRLoader.Handedness.Right))
            {
                var rightPos = handManager.GetHandPosition(Core.MonadoOpenXRLoader.Handedness.Right);
                var skeleton = handManager.GetSkeleton(Core.MonadoOpenXRLoader.Handedness.Right);
                var dir = skeleton.GetJointDirection(HandTracking.XRHandJoint.IndexTip);
                rightSaber.position = rightPos;
                rightSaber.rotation = Quaternion.LookRotation(dir);
            }
        }

        void UpdateSpawning()
        {
            _spawnTimer -= Time.deltaTime;
            if (_spawnTimer <= 0f)
            {
                SpawnNote();
                _spawnTimer = spawnInterval;
            }
        }

        void SpawnNote()
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = "Note";
            go.transform.position = spawnPoint.position + new Vector3(Random.Range(-1f,1f), Random.Range(-0.5f,0.5f), 0);
            go.transform.localScale = Vector3.one * 0.35f;
            go.transform.rotation = Quaternion.Euler(0,0, Random.Range(0,360));

            var note = go.AddComponent<Note>();
            note.speed = noteSpeed;
            note.isRed = Random.value > 0.5f;
            note.manager = this;

            var renderer = go.GetComponent<Renderer>();
            renderer.material = note.isRed ? redSaberMat : blueSaberMat;

            _activeNotes.Add(note);
        }

        void UpdateNotes()
        {
            for (int i = _activeNotes.Count - 1; i >= 0; i--)
            {
                var note = _activeNotes[i];
                if (note == null) { _activeNotes.RemoveAt(i); continue; }
                note.transform.position += Vector3.back * note.speed * Time.deltaTime;

                if (note.transform.position.z < -2f)
                {
                    // Missed
                    _combo = 0;
                    Destroy(note.gameObject);
                    _activeNotes.RemoveAt(i);
                }
            }
        }

        public void OnNoteHit(Note note, bool correctColor)
        {
            if (correctColor)
            {
                _score += 100 + _combo * 10;
                _combo++;
                Debug.Log($"[BeatSaber] Hit! Score:{_score} Combo:{_combo}");
            }
            else
            {
                _combo = 0;
            }
            _activeNotes.Remove(note);
            Destroy(note.gameObject);
            // Haptic via Cardboard?
        }
    }

    public class Note : MonoBehaviour
    {
        public float speed = 4f;
        public bool isRed = true;
        public BeatSaberManager manager;

        void OnTriggerEnter(Collider other)
        {
            var saber = other.GetComponent<Saber>();
            if (saber != null)
            {
                bool correct = (isRed && !saber.isLeft) || (!isRed && saber.isLeft);
                // In Beat Saber, red is left? Actually blue left, red right - but we simplify
                manager.OnNoteHit(this, true);
            }
        }
    }

    public class Saber : MonoBehaviour
    {
        public bool isLeft = true;
        void Awake()
        {
            var col = GetComponent<Collider>();
            if (col == null) col = gameObject.AddComponent<BoxCollider>();
            col.isTrigger = true;
            var rb = gameObject.AddComponent<Rigidbody>();
            rb.isKinematic = true;
        }
    }
}
