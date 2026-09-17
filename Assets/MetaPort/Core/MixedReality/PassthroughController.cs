using UnityEngine;
using UnityEngine.XR.ARFoundation;

namespace MetaPort.Core.MixedReality
{
    public class PassthroughController : MonoBehaviour
    {
        public ARCameraBackground arBackground;
        public Material passthroughMat;
        public bool enableDepthOcclusion = true;

        void Awake()
        {
            if (arBackground == null) arBackground = FindObjectOfType<ARCameraBackground>();
        }

        void Update()
        {
            if (arBackground != null && passthroughMat != null)
            {
                arBackground.material = passthroughMat;
            }
        }
    }
}
