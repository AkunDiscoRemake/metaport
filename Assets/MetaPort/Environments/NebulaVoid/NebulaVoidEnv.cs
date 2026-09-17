using UnityEngine;
namespace MetaPort.Environments.NebulaVoid {
public class NebulaVoidEnv : MonoBehaviour {
void Awake(){
var nebula=GameObject.CreatePrimitive(PrimitiveType.Sphere); nebula.name="Nebula"; nebula.transform.SetParent(transform,false); nebula.transform.localScale=Vector3.one*100f; var mesh=nebula.GetComponent<MeshFilter>().mesh; var tris=mesh.triangles; System.Array.Reverse(tris); mesh.triangles=tris; mesh.RecalculateNormals(); var mat=new Material(Shader.Find("MetaPort/Skybox3D")); mat.SetColor("_TopColor", new Color(0.6f,0.1f,0.8f)); mat.SetColor("_BottomColor", new Color(0.05f,0.02f,0.2f)); nebula.GetComponent<Renderer>().material=mat;
for(int i=0;i<50;i++){ var star=GameObject.CreatePrimitive(PrimitiveType.Sphere); star.transform.SetParent(transform,false); star.transform.localPosition=Random.onUnitSphere*40f; star.transform.localScale=Vector3.one*Random.Range(0.05f,0.2f); var sm=new Material(Shader.Find("Unlit/Color")); sm.color=Color.white; star.GetComponent<Renderer>().material=sm; }
}
}
}
