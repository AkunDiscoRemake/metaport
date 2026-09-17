using UnityEngine;
namespace MetaPort.Games.GorillaTagMini {
public class GorillaTagMini : MonoBehaviour {
    public bool isClimbing=false;
    void Update(){
        var hm=HandTracking.HandTrackingManager.Instance;
        if(hm==null) return;
        // Se ambas mãos em pinch e movendo pra baixo, anda pra frente (locomoção gorilla)
        if(hm.IsTracking(Core.MonadoOpenXRLoader.Handedness.Left) && hm.IsTracking(Core.MonadoOpenXRLoader.Handedness.Right)){
            float leftPinch=hm.GetSkeleton(Core.MonadoOpenXRLoader.Handedness.Left).GetPinchStrength();
            float rightPinch=hm.GetSkeleton(Core.MonadoOpenXRLoader.Handedness.Right).GetPinchStrength();
            if(leftPinch>0.8f && rightPinch>0.8f){
                // Move na direção das mãos
                Vector3 avgPos=(hm.GetHandPosition(Core.MonadoOpenXRLoader.Handedness.Left)+hm.GetHandPosition(Core.MonadoOpenXRLoader.Handedness.Right))*0.5f;
                transform.position+=transform.forward*Time.deltaTime*2f;
            }
        }
    }
}
}
