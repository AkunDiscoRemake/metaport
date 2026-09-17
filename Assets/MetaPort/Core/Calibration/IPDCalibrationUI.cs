using UnityEngine;

namespace MetaPort.Core.Calibration
{
    public class IPDCalibrationUI : MonoBehaviour
    {
        public Transform leftEyePreview;
        public Transform rightEyePreview;
        public float currentIPD = 0.064f;

        void Awake()
        {
            // Cria preview 3D de olhos com IPD ajustável
            if (leftEyePreview == null)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                go.name = "LeftEyePreview";
                go.transform.SetParent(transform, false);
                go.transform.localScale = Vector3.one * 0.05f;
                leftEyePreview = go.transform;
            }
            if (rightEyePreview == null)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                go.name = "RightEyePreview";
                go.transform.SetParent(transform, false);
                go.transform.localScale = Vector3.one * 0.05f;
                rightEyePreview = go.transform;
            }
        }

        void Update()
        {
            leftEyePreview.localPosition = new Vector3(-currentIPD * 0.5f, 0, 0);
            rightEyePreview.localPosition = new Vector3(currentIPD * 0.5f, 0, 0);
        }

        public void AdjustIPD(float delta)
        {
            currentIPD = Mathf.Clamp(currentIPD + delta, 0.05f, 0.08f);
            var calibrator = CardboardCalibrator.Instance;
            if (calibrator != null) calibrator.ipd = currentIPD;
        }
    }
}
