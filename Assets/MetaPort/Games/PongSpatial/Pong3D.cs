using UnityEngine;

namespace MetaPort.Games.PongSpatial
{
    /// <summary>
    /// Pong Spatial - 3D spatial pong like in ZentraXR screenshot
    /// </summary>
    public class Pong3D : MonoBehaviour
    {
        [Header("Pong 3D Spatial")]
        public Transform leftPaddle;
        public Transform rightPaddle;
        public Transform ball;
        public Transform table; // 3D table with depth
        public float paddleSpeed = 5f;
        public float ballSpeed = 3f;
        public int leftScore = 0;
        public int rightScore = 0;

        private Vector3 _ballVelocity;
        private Vector3 _tableSize = new Vector3(2.5f, 1.5f, 0.1f);

        void Awake()
        {
            CreateTable();
            CreatePaddles();
            CreateBall();
            ResetBall();
        }

        void CreateTable()
        {
            if (table == null)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
                go.name = "PongTable_3D";
                go.transform.SetParent(transform, false);
                go.transform.localPosition = Vector3.forward * 2f;
                go.transform.localScale = _tableSize;
                table = go.transform;
                var mat = new Material(Shader.Find("MetaPort/SpatialWindow"));
                mat.SetColor("_BaseColor", new Color(0.05f,0.05f,0.08f,0.95f));
                go.GetComponent<Renderer>().material = mat;
            }
        }

        void CreatePaddles()
        {
            if (leftPaddle == null)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
                go.name = "LeftPaddle";
                go.transform.SetParent(table, false);
                go.transform.localPosition = new Vector3(-_tableSize.x*0.5f + 0.1f, 0, -0.1f);
                go.transform.localScale = new Vector3(0.05f, 0.3f, 0.2f);
                leftPaddle = go.transform;
                var mat = new Material(Shader.Find("MetaPort/Icon3D"));
                mat.SetColor("_BaseColor", Color.cyan);
                go.GetComponent<Renderer>().material = mat;
            }
            if (rightPaddle == null)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
                go.name = "RightPaddle";
                go.transform.SetParent(table, false);
                go.transform.localPosition = new Vector3(_tableSize.x*0.5f - 0.1f, 0, -0.1f);
                go.transform.localScale = new Vector3(0.05f, 0.3f, 0.2f);
                rightPaddle = go.transform;
                var mat = new Material(Shader.Find("MetaPort/Icon3D"));
                mat.SetColor("_BaseColor", Color.red);
                go.GetComponent<Renderer>().material = mat;
            }
        }

        void CreateBall()
        {
            if (ball == null)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                go.name = "Ball_3D";
                go.transform.SetParent(table, false);
                go.transform.localScale = Vector3.one * 0.08f;
                ball = go.transform;
                var mat = new Material(Shader.Find("MetaPort/Icon3D"));
                mat.SetColor("_BaseColor", Color.white);
                mat.SetColor("_Emission", Color.white * 0.5f);
                go.GetComponent<Renderer>().material = mat;
                var rb = go.AddComponent<Rigidbody>();
                rb.isKinematic = true;
            }
        }

        void ResetBall()
        {
            ball.localPosition = Vector3.zero + Vector3.back * 0.15f;
            _ballVelocity = new Vector3(Random.Range(-1f,1f), Random.Range(-0.5f,0.5f), 0).normalized * ballSpeed;
            if (Random.value > 0.5f) _ballVelocity.x *= -1;
        }

        void Update()
        {
            UpdatePaddles();
            UpdateBall();
            CheckScore();
        }

        void UpdatePaddles()
        {
            var handManager = HandTracking.HandTrackingManager.Instance;
            float leftY = 0, rightY = 0;

            // Hand tracking controls paddles
            if (handManager != null)
            {
                if (handManager.IsTracking(Core.MonadoOpenXRLoader.Handedness.Left))
                {
                    var pos = handManager.GetHandPosition(Core.MonadoOpenXRLoader.Handedness.Left);
                    // Map hand Y to paddle Y
                    leftY = Mathf.Clamp(pos.y - table.position.y, -_tableSize.y*0.4f, _tableSize.y*0.4f);
                }
                if (handManager.IsTracking(Core.MonadoOpenXRLoader.Handedness.Right))
                {
                    var pos = handManager.GetHandPosition(Core.MonadoOpenXRLoader.Handedness.Right);
                    rightY = Mathf.Clamp(pos.y - table.position.y, -_tableSize.y*0.4f, _tableSize.y*0.4f);
                }
            }

            // Fallback: mouse / gaze
            if (Mathf.Abs(leftY) < 0.01f) leftY = Mathf.Sin(Time.time * 1.5f) * 0.5f; // AI
            if (Mathf.Abs(rightY) < 0.01f) rightY = Input.mousePosition.y / Screen.height * _tableSize.y - _tableSize.y*0.5f;

            leftPaddle.localPosition = Vector3.Lerp(leftPaddle.localPosition, new Vector3(leftPaddle.localPosition.x, leftY, leftPaddle.localPosition.z), Time.deltaTime * paddleSpeed);
            rightPaddle.localPosition = Vector3.Lerp(rightPaddle.localPosition, new Vector3(rightPaddle.localPosition.x, rightY, rightPaddle.localPosition.z), Time.deltaTime * paddleSpeed);
        }

        void UpdateBall()
        {
            ball.localPosition += _ballVelocity * Time.deltaTime;

            // Bounce top/bottom
            if (Mathf.Abs(ball.localPosition.y) > _tableSize.y*0.5f - 0.05f)
            {
                _ballVelocity.y *= -1;
                ball.localPosition = new Vector3(ball.localPosition.x, Mathf.Clamp(ball.localPosition.y, -_tableSize.y*0.5f+0.05f, _tableSize.y*0.5f-0.05f), ball.localPosition.z);
            }

            // Paddle collision
            if (Vector3.Distance(ball.localPosition, leftPaddle.localPosition) < 0.2f)
            {
                _ballVelocity.x = Mathf.Abs(_ballVelocity.x);
                _ballVelocity.y += (ball.localPosition.y - leftPaddle.localPosition.y) * 2f;
                _ballVelocity = _ballVelocity.normalized * (ballSpeed + leftScore * 0.1f);
            }
            if (Vector3.Distance(ball.localPosition, rightPaddle.localPosition) < 0.2f)
            {
                _ballVelocity.x = -Mathf.Abs(_ballVelocity.x);
                _ballVelocity.y += (ball.localPosition.y - rightPaddle.localPosition.y) * 2f;
                _ballVelocity = _ballVelocity.normalized * (ballSpeed + rightScore * 0.1f);
            }
        }

        void CheckScore()
        {
            if (ball.localPosition.x < -_tableSize.x*0.5f - 0.2f)
            {
                rightScore++;
                ResetBall();
            }
            if (ball.localPosition.x > _tableSize.x*0.5f + 0.2f)
            {
                leftScore++;
                ResetBall();
            }
        }

        void OnGUI()
        {
            // 3D GUI would be spatial, but for debug we use OnGUI
            // In real VR, this is a 3D TextMeshPro above table
        }
    }
}
