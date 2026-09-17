using UnityEngine;

namespace MetaPort.Games.SuperhotVR
{
    /// <summary>
    /// Superhot VR remake - tempo só anda quando você anda (6DOF)
    /// Perfeito para Cardboard 6DOF
    /// </summary>
    public class SuperhotManager : MonoBehaviour
    {
        [Header("Superhot - 6DOF")]
        public Transform[] enemies;
        public Transform[] bullets;
        public float timeScale = 0.05f;
        public float normalTimeScale = 1f;

        private Vector3 _lastHeadPos;
        private float _headMoveSpeed = 0f;

        void Awake()
        {
            _lastHeadPos = Camera.main.transform.position;
            SpawnEnemies();
        }

        void SpawnEnemies()
        {
            enemies = new Transform[5];
            for (int i = 0; i < 5; i++)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
                go.name = $"Enemy_{i}_3D";
                go.transform.SetParent(transform, false);
                go.transform.position = transform.position + new Vector3(Random.Range(-3f,3f), Random.Range(0.5f,1.8f), Random.Range(2f,6f));
                go.transform.localScale = Vector3.one * 0.5f;
                var mat = new Material(Shader.Find("MetaPort/Icon3D"));
                mat.SetColor("_BaseColor", Color.red);
                mat.SetColor("_Emission", Color.red * 0.5f);
                go.GetComponent<Renderer>().material = mat;
                enemies[i] = go.transform;
                go.AddComponent<Enemy>().manager = this;
            }
        }

        void Update()
        {
            // Calcula velocidade da cabeça via 6DOF ARCore
            Vector3 headPos = Camera.main.transform.position;
            _headMoveSpeed = Vector3.Distance(headPos, _lastHeadPos) / Time.deltaTime;
            _lastHeadPos = headPos;

            // Tempo só anda quando você se move - SUPERHOT
            float targetTimeScale = Mathf.Clamp(_headMoveSpeed * 2f, 0.05f, 1f);
            timeScale = Mathf.Lerp(timeScale, targetTimeScale, Time.deltaTime * 5f);
            Time.timeScale = timeScale;

            // Mover inimigos lentamente
            foreach (var enemy in enemies)
            {
                if (enemy == null) continue;
                enemy.position += (Camera.main.transform.position - enemy.position).normalized * Time.deltaTime * timeScale * 0.5f;
            }
        }

        public void OnEnemyHit(Transform enemy)
        {
            // Shatter
            Destroy(enemy.gameObject);
            Debug.Log("[Superhot] Enemy destroyed!");
        }

        public class Enemy : MonoBehaviour
        {
            public SuperhotManager manager;
            void OnTriggerEnter(Collider other)
            {
                if (other.GetComponent<HandTracking.HandMeshVisualizer>() != null || other.name.Contains("Saber"))
                {
                    manager.OnEnemyHit(transform);
                }
            }
        }
    }
}
