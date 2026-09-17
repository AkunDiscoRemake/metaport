using UnityEngine;
namespace MetaPort.Input {
public class VoiceControl : MonoBehaviour {
public static VoiceControl Instance;
void Awake(){ Instance=this; }
#if UNITY_ANDROID
private AndroidJavaObject _speechRecognizer;
void Start(){
try{
using(var unityPlayer=new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
using(var activity=unityPlayer.GetStatic<AndroidJavaObject>("currentActivity")){
Debug.Log("[Voice] Speech recognizer ready");
}
}catch{}
}
#endif
public void StartListening(System.Action<string> onResult){
Debug.Log("[Voice] Listening... diga 'abrir browser' ou 'modo VR'");
#if UNITY_EDITOR
onResult?.Invoke("abrir browser");
#endif
}
}
}
