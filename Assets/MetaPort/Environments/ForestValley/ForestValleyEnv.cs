using UnityEngine;
namespace MetaPort.Environments.ForestValley {
public class ForestValleyEnv : MonoBehaviour {
void Awake(){
for(int i=0;i<30;i++){ var tree=GameObject.CreatePrimitive(PrimitiveType.Cylinder); tree.transform.SetParent(transform,false); tree.transform.localPosition=new Vector3(Random.Range(-25f,25f),0,Random.Range(5f,35f)); tree.transform.localScale=new Vector3(0.3f,Random.Range(2f,6f),0.3f); var mat=new Material(Shader.Find("MetaPort/Icon3D")); mat.SetColor("_BaseColor", new Color(0.4f,0.25f,0.15f)); tree.GetComponent<Renderer>().material=mat; var leaves=GameObject.CreatePrimitive(PrimitiveType.Sphere); leaves.transform.SetParent(tree.transform,false); leaves.transform.localPosition=Vector3.up*1.2f; leaves.transform.localScale=new Vector3(2,1,2); var lm=new Material(Shader.Find("MetaPort/Icon3D")); lm.SetColor("_BaseColor", new Color(0.1f,0.5f,0.2f)); leaves.GetComponent<Renderer>().material=lm; }
}
}
}
