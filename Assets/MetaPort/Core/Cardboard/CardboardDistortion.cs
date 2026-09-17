using UnityEngine;

namespace MetaPort.Core.Cardboard
{
    /// <summary>
    /// Distorção de lente Cardboard - corrige para cada viewer
    /// </summary>
    [RequireComponent(typeof(Camera))]
    public class CardboardDistortion : MonoBehaviour
    {
        public Material distortionMaterial;
        public Vector2 k1k2 = new Vector2(0.34f, 0.55f);
        public float fov = 60f;

        void OnRenderImage(RenderTexture src, RenderTexture dest)
        {
            if (distortionMaterial != null)
            {
                distortionMaterial.SetVector("_Distortion", k1k2);
                Graphics.Blit(src, dest, distortionMaterial);
            }
            else
            {
                Graphics.Blit(src, dest);
            }
        }
    }
}
