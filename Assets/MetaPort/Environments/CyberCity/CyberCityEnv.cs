using UnityEngine;

namespace MetaPort.Environments.CyberCity
{
    public class CyberCityEnv : MonoBehaviour
    {
        void Awake()
        {
            // Cidade cyberpunk com neon
            for (int i = 0; i < 20; i++)
            {
                var building = GameObject.CreatePrimitive(PrimitiveType.Cube);
                building.name = $"Building_{i}";
                building.transform.SetParent(transform, false);
                building.transform.position = new Vector3(Random.Range(-20f,20f), Random.Range(1f,8f), Random.Range(10f,40f));
                building.transform.localScale = new Vector3(Random.Range(1f,3f), Random.Range(3f,15f), Random.Range(1f,3f));
                var mat = new Material(Shader.Find("MetaPort/Icon3D"));
                mat.SetColor("_BaseColor", Color.HSVToRGB(Random.value, 0.8f, 0.3f));
                mat.SetColor("_Emission", Color.HSVToRGB(Random.value, 1f, 1f) * 0.5f);
                building.GetComponent<Renderer>().material = mat;
            }
        }
    }
}
