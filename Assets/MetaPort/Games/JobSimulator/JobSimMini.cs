using UnityEngine;
namespace MetaPort.Games.JobSimulator {
public class JobSimMini : MonoBehaviour {
    void Awake(){
        // Cria bancada de trabalho 3D
        var counter=GameObject.CreatePrimitive(PrimitiveType.Cube);
        counter.name="Counter_3D"; counter.transform.SetParent(transform,false);
        counter.transform.localPosition=Vector3.forward*1f+Vector3.down*0.2f;
        counter.transform.localScale=new Vector3(1.5f,0.1f,0.8f);
        for(int i=0;i<5;i++){
            var obj=GameObject.CreatePrimitive(PrimitiveType.Cube);
            obj.name=$"JobObject_{i}"; obj.transform.SetParent(transform,false);
            obj.transform.localPosition=new Vector3(Random.Range(-0.5f,0.5f),0.5f,Random.Range(0.8f,1.2f));
            obj.transform.localScale=Vector3.one*0.15f;
            obj.AddComponent<Rigidbody>().useGravity=false;
            var mat=new Material(Shader.Find("MetaPort/Icon3D")); mat.SetColor("_BaseColor", Color.HSVToRGB(Random.value,0.8f,0.9f));
            obj.GetComponent<Renderer>().material=mat;
        }
    }
}
}
