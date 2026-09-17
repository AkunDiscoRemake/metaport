using UnityEngine;
namespace MetaPort.Games.AsgardWrathMini {
public class AsgardMini : MonoBehaviour {
    public Transform sword; public Transform shield;
    void Awake(){
        var swordGO = GameObject.CreatePrimitive(PrimitiveType.Cube);
        swordGO.name="Sword_3D"; swordGO.transform.SetParent(transform,false);
        swordGO.transform.localScale=new Vector3(0.05f,0.8f,0.1f);
        var mat=new Material(Shader.Find("MetaPort/Icon3D")); mat.SetColor("_BaseColor", Color.gray);
        swordGO.GetComponent<Renderer>().material=mat;
        sword=swordGO.transform;
        var shieldGO = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
        shieldGO.name="Shield_3D"; shieldGO.transform.SetParent(transform,false);
        shieldGO.transform.localScale=new Vector3(0.4f,0.05f,0.4f);
        shield=shieldGO.transform;
    }
    void Update(){
        var hm=HandTracking.HandTrackingManager.Instance;
        if(hm!=null){
            if(hm.IsTracking(Core.MonadoOpenXRLoader.Handedness.Right)) sword.position=hm.GetHandPosition(Core.MonadoOpenXRLoader.Handedness.Right);
            if(hm.IsTracking(Core.MonadoOpenXRLoader.Handedness.Left)) shield.position=hm.GetHandPosition(Core.MonadoOpenXRLoader.Handedness.Left);
        }
    }
}
}
