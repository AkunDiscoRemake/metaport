using UnityEngine;

namespace MetaPort.Core.Calibration
{
    /// <summary>
    /// Calibrador automático de Cardboard - IPD, distorção, FOV, gyro drift
    /// Melhor experiência possível em VR Box de R$20
    /// </summary>
    public class CardboardCalibrator : MonoBehaviour
    {
        public static CardboardCalibrator Instance;

        [Header("Auto Calibration")]
        public bool autoCalibrateOnStart = true;
        public bool enableIPDCalibration = true;
        public bool enableGyroDriftCorrection = true;
        public bool enableLensDistortionCalibration = true;

        [Header("IPD")]
        [Range(0.05f, 0.08f)] public float ipd = 0.064f;
        public float ipdStep = 0.001f;

        [Header("Distortion")]
        public Vector2 distortionCoeffs = new Vector2(0.34f, 0.55f);
        public float vignetteStrength = 0.15f;

        [Header("Gyro")]
        public Vector3 gyroBias = Vector3.zero;
        public float driftCorrectionSpeed = 0.02f;
        private Quaternion _gyroInitial;
        private Vector3 _magnetometerInitial;

        void Awake()
        {
            Instance = this;
            if (autoCalibrateOnStart) Calibrate();
        }

        public void Calibrate()
        {
            Debug.Log("[MetaPort] Iniciando calibração automática Cardboard...");

            // IPD - pergunta pro usuário ou detecta via câmera?
            // Usa média 64mm mas permite ajuste fino com slider 3D
            LoadSavedCalibration();

            // Gyro drift
            if (SystemInfo.supportsGyroscope)
            {
                Input.gyro.enabled = true;
                _gyroInitial = Input.gyro.attitude;
                StartCoroutine(CalibrateGyroDrift());
            }

            // Lens distortion - usa perfil Cardboard Viewer
            if (enableLensDistortionCalibration)
            {
                // Em real app, leria QR code do Cardboard Viewer via câmera
                // Aqui usa perfil genérico otimizado
                distortionCoeffs = new Vector2(0.34f, 0.55f);
            }

            Debug.Log($"[MetaPort] Calibrado: IPD={ipd*1000:F1}mm Distortion={distortionCoeffs}");
        }

        System.Collections.IEnumerator CalibrateGyroDrift()
        {
            // Coleta 2 segundos de gyro parado para calcular bias
            Vector3 sum = Vector3.zero;
            int samples = 0;
            float timer = 0;
            while (timer < 2f)
            {
                sum += Input.gyro.rotationRate;
                samples++;
                timer += Time.deltaTime;
                yield return null;
            }
            gyroBias = sum / samples;
            Debug.Log($"[MetaPort] Gyro bias: {gyroBias}");
        }

        public void AdjustIPD(float delta)
        {
            ipd = Mathf.Clamp(ipd + delta * ipdStep, 0.05f, 0.08f);
            var rig = FindObjectOfType<Cardboard.CardboardRig>();
            if (rig != null) rig.stereoMultiplier = ipd / 0.064f;
            SaveCalibration();
        }

        void LoadSavedCalibration()
        {
            if (PlayerPrefs.HasKey("MetaPort_IPD"))
                ipd = PlayerPrefs.GetFloat("MetaPort_IPD", 0.064f);
        }

        void SaveCalibration()
        {
            PlayerPrefs.SetFloat("MetaPort_IPD", ipd);
            PlayerPrefs.Save();
        }

        // UI 3D para calibrar - slider espacial
        public void ShowCalibrationUI()
        {
            var window = SDK.MetaPortAPI.CreateSpatialWindow("Calibração Cardboard", new Vector3(1f, 0.6f, 0.05f));
            // Adicionaria sliders 3D para IPD
        }
    }
}
