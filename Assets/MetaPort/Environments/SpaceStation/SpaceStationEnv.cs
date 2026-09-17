using UnityEngine;
namespace MetaPort.Environments.SpaceStation {
public class SpaceStationEnv : MonoBehaviour {
void Awake(){
var floor=GameObject.CreatePrimitive(PrimitiveType.Cube);
floor.name="StationFloor"; floor.transform.SetParent(transform,false);
floor.transform.localPosition=Vector3.down*1.5f; floor.transform.localScale=new Vector3(10,0.2f,10);
var mat=new Material(Shader.Find("MetaPort/Icon3D")); mat.SetColor("_BaseColor", new Color(0.7f,0.7f,0.75f));
floor.GetComponent<Renderer>().material=mat;
for(int i=0;i<8;i++){ var light=GameObject.CreatePrimitive(PrimitiveType.Cylinder); light.transform.SetParent(transform,false); light.transform.localPosition=new Vector3(Random.Range(-4f,4f),2.5f,Random.Range(-4f,4f)); light.transform.localScale=new Vector3(0.2f,0.05f,0.2f); var lm=new Material(Shader.Find("MetaPort/Icon3D")); lm.SetColor("_Emission", Color.white*2f); light.GetComponent<Renderer>().material=lm; }
}
}
}
