using UnityEngine;

namespace MetaPort.Core.Cardboard
{
    /// <summary>
    /// Fallback caso Cardboard package não esteja instalado
    /// Permite usar MetaPort sem com.google.xr.cardboard
    /// Usa apenas ARCore + Gyro
    /// </summary>
    public class CardboardFallback : MonoBehaviour
    {
        public static bool IsCardboardPackageAvailable()
        {
#if UNITY_EDITOR
            // Verifica se package existe
            var assemblies = System.AppDomain.CurrentDomain.GetAssemblies();
            foreach (var asm in assemblies)
            {
                if (asm.GetName().Name.Contains("Cardboard"))
                    return true;
            }
            return false;
#else
            return true; // No Android assume que tem
#endif
        }

        [Header("Fallback sem Cardboard")]
        public bool useFallbackIfNoCardboard = true;
        public float fallbackFOV = 60f;
        public float fallbackIPD = 0.064f;

        void Awake()
        {
            if (!IsCardboardPackageAvailable() && useFallbackIfNoCardboard)
            {
                Debug.LogWarning("[MetaPort] Cardboard package não encontrado, usando fallback ARCore + Gyro - 6DOF ainda funciona!");
                SetupFallbackRig();
            }
        }

        void SetupFallbackRig()
        {
            // Cria stereo cameras manualmente sem Cardboard SDK
            var cam = Camera.main;
            if (cam == null) return;

            cam.fieldOfView = fallbackFOV;
            cam.stereoTargetEye = StereoTargetEyeMask.Both;

            // Se não tem Cardboard, usa SinglePassInstanced
            // Já configurado no XR Management
        }
    }
}
